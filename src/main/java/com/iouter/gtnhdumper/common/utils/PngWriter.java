package com.iouter.gtnhdumper.common.utils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Owns PNG encoding only; callers keep all OpenGL operations on the client thread.
 * <p>
 * Every image is identified by a SHA-1 of its size and ARGB pixels. When a cache directory is given, encoded files are
 * stored there under that hash and linked into the output, so an unchanged image is never encoded twice across runs.
 * The pixel hashes are also reported per output file for downstream change detection.
 */
public final class PngWriter implements AutoCloseable {

    /** Pending pixels are bounded by bytes, so many small icons queue while large animation strips wait. */
    private static final long DEFAULT_QUEUE_BYTES = 512L * 1024 * 1024;

    private final ThreadPoolExecutor executor;
    private final File cacheDirectory;
    private final Semaphore queueBudget;
    private final int queueBudgetKiB;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final LongAdder count = new LongAdder();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder encodeNanos = new LongAdder();
    private final Map<String, String> pixelHashes = new ConcurrentHashMap<>();

    public PngWriter(int workers) {
        this(workers, null);
    }

    public PngWriter(int workers, File cacheDirectory) {
        if (workers < 0 || workers > 64) throw new IllegalArgumentException("PNG workers must be 0..64");
        this.cacheDirectory = cacheDirectory;
        long queueBytes = Long.getLong("gtnhdumper.pngQueueMB", DEFAULT_QUEUE_BYTES >> 20) << 20;
        queueBudgetKiB = (int) Math.max(1, Math.min(Integer.MAX_VALUE, queueBytes >> 10));
        queueBudget = new Semaphore(queueBudgetKiB);
        executor = workers == 0 ? null
            : new ThreadPoolExecutor(workers, workers, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), task -> {
                Thread thread = new Thread(task, "DumperPngWriter");
                thread.setDaemon(true);
                return thread;
            });
    }

    /** Default worker count: leave two cores for the render thread and the game. */
    public static int defaultWorkers() {
        return Math.max(
            1,
            Math.min(
                16,
                Runtime.getRuntime()
                    .availableProcessors() - 2));
    }

    /** The caller transfers ownership of an immutable image until close() has finished. */
    public void write(File file, BufferedImage image) {
        checkFailure();
        Runnable task = () -> {
            long start = System.nanoTime();
            try {
                store(file, image);
                count.increment();
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            } finally {
                encodeNanos.add(System.nanoTime() - start);
            }
        };
        int cost = (int) Math.max(1, Math.min(queueBudgetKiB, (long) image.getWidth() * image.getHeight() * 4 >> 10));
        if (executor == null) {
            task.run();
            checkFailure();
            return;
        }
        try {
            queueBudget.acquire(cost);
        } catch (InterruptedException error) {
            Thread.currentThread()
                .interrupt();
            throw new IllegalStateException("Interrupted while waiting for PNG writers", error);
        }
        executor.execute(() -> {
            try {
                task.run();
            } finally {
                queueBudget.release(cost);
            }
        });
    }

    public long count() {
        return count.sum();
    }

    public long cacheHits() {
        return cacheHits.sum();
    }

    public long encodeNanos() {
        return encodeNanos.sum();
    }

    /** Output file name to pixel hash, sorted by name; complete after close(). */
    public Map<String, String> pixelHashes() {
        return new TreeMap<>(pixelHashes);
    }

    private void checkFailure() {
        if (failure.get() != null) throw new IllegalStateException("PNG output failed", failure.get());
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.HOURS))
                    throw new IllegalStateException("Timed out waiting for PNG writers");
            } catch (InterruptedException error) {
                Thread.currentThread()
                    .interrupt();
                throw new IllegalStateException("Interrupted while waiting for PNG writers", error);
            }
        }
        checkFailure();
    }

    private void store(File file, BufferedImage image) throws IOException {
        String hash = pixelHash(image);
        pixelHashes.put(file.getName(), hash);
        if (cacheDirectory == null) {
            encode(file, image);
            return;
        }
        Path cached = new File(cacheDirectory, hash.substring(0, 2) + File.separator + hash + ".png").toPath();
        // Writers of the same pixels, or of the same output name, take the same lock; cache entries are never replaced
        // because Windows briefly removes the target of a replacing move.
        synchronized (lock(hash)) {
            if (Files.isRegularFile(cached)) cacheHits.increment();
            else {
                Files.createDirectories(cached.getParent());
                File temporary = new File(
                    cached.getParent()
                        .toFile(),
                    hash + ".tmp");
                encode(temporary, image);
                Files.move(temporary.toPath(), cached);
            }
        }
        Path target = file.toPath();
        synchronized (lock(target.toString())) {
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(target);
            try {
                Files.createLink(target, cached);
            } catch (IOException | UnsupportedOperationException unsupportedLink) {
                Files.copy(cached, target);
            }
        }
    }

    private final Object[] locks = new Object[1024];

    {
        for (int i = 0; i < locks.length; i++) locks[i] = new Object();
    }

    private Object lock(String key) {
        return locks[(key.hashCode() & Integer.MAX_VALUE) % locks.length];
    }

    /** SHA-1 of "width:height:" followed by the ARGB pixels in row order, big endian. */
    public static String pixelHash(BufferedImage image) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
        int width = image.getWidth();
        int height = image.getHeight();
        digest.update((width + ":" + height + ":").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        int[] row = new int[width];
        ByteBuffer bytes = ByteBuffer.allocate(width * 4);
        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            bytes.clear();
            bytes.asIntBuffer()
                .put(row);
            digest.update(bytes.array());
        }
        StringBuilder hex = new StringBuilder(40);
        for (byte b : digest.digest()) hex.append(Character.forDigit((b >> 4) & 15, 16))
            .append(Character.forDigit(b & 15, 16));
        return hex.toString();
    }

    public static void encode(File file, BufferedImage image) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory())
            throw new IOException("Cannot create PNG directory: " + parent);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png")
            .next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionType(param.getCompressionTypes()[0]);
        // Keep maximum compression, including in the parallel export path.
        param.setCompressionQuality(0f);
        // FileImageOutputStream does not truncate old files; replace the directory entry
        // so shorter rewrites have no trailing bytes and cached hardlinks remain immutable.
        java.nio.file.Files.deleteIfExists(file.toPath());
        try (ImageOutputStream output = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
