package com.iouter.gtnhdumper.common.utils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * How an item icon is encoded as WebP:
 * <ul>
 * <li>a sprite that is an exact nearest-neighbour enlargement is stored at its native size, lossless;</li>
 * <li>an animation strip ({@code *_dynamic.webp}) becomes a lossless animated WebP;</li>
 * <li>anything else is lossless, except that the still of a 3D block item may be stored lossy when that is clearly
 * smaller. Flat item sprites are never lossy.</li>
 * </ul>
 */
public final class IconImageFormat {

    public static final String EXTENSION = ".webp";
    static final String ANIMATION_SUFFIX = "_dynamic" + EXTENSION;

    private static final int METHOD = 4;
    private static final int FRAME_MILLIS = 50;
    private static final float LOSSY_QUALITY = 0.8f;
    /** A lossy file is only kept when it is at most this fraction of the lossless one. */
    private static final double LOSSY_MAX_RATIO = 0.8;
    /** Part of the content cache file name; change it whenever the rules or encoder settings above change. */
    private static final String RULES = "r1";

    private static final Set<String> LOSSY_ALLOWED = ConcurrentHashMap.newKeySet();
    private static final LongAdder LOSSY_KEPT = new LongAdder();

    private IconImageFormat() {}

    /** Called on the render thread before the still of a 3D block item is handed to the writer. */
    public static void allowLossy(String fileName) {
        LOSSY_ALLOWED.add(fileName);
    }

    /** The side of the sprite this image exactly enlarges, or 0. */
    public static int nativeSide(BufferedImage image) {
        return WebpCodec.pixelSide(image);
    }

    /** Whether the image is an exact enlargement of a smaller sprite, which the wiki must scale without smoothing. */
    public static boolean isPixelArt(BufferedImage image) {
        return nativeSide(image) > 0;
    }

    /** Stills stored lossy since the last {@link #reset}. */
    public static long lossyKept() {
        return LOSSY_KEPT.sum();
    }

    public static void reset() {
        LOSSY_ALLOWED.clear();
        LOSSY_KEPT.reset();
    }

    /** Suffix of the content cache entry: the same pixels encode differently depending on these. */
    static String cacheSuffix(String fileName) {
        if (fileName.endsWith(ANIMATION_SUFFIX)) return "." + RULES + ".anim" + EXTENSION;
        return "." + RULES + (LOSSY_ALLOWED.contains(fileName) ? ".block" : "") + EXTENSION;
    }

    static byte[] encode(String fileName, BufferedImage image) throws IOException {
        if (fileName.endsWith(ANIMATION_SUFFIX)) {
            int side = image.getHeight();
            List<BufferedImage> frames = new ArrayList<>();
            for (int x = 0; x + side <= image.getWidth(); x += side) frames.add(image.getSubimage(x, 0, side, side));
            return WebpAnimation.encode(frames, FRAME_MILLIS, METHOD);
        }
        int nativeSide = WebpCodec.pixelSide(image);
        if (nativeSide > 0) return WebpCodec.lossless(WebpCodec.shrink(image, nativeSide), METHOD);
        byte[] lossless = WebpCodec.lossless(image, METHOD);
        if (!LOSSY_ALLOWED.contains(fileName)) return lossless;
        byte[] lossy = WebpCodec.lossy(image, LOSSY_QUALITY, METHOD);
        if (lossy.length > lossless.length * LOSSY_MAX_RATIO) return lossless;
        LOSSY_KEPT.increment();
        return lossy;
    }
}
