package com.iouter.gtnhdumper.common.utils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import com.luciad.imageio.webp.CompressionType;
import com.luciad.imageio.webp.WebPImageWriterSpi;
import com.luciad.imageio.webp.WebPWriteParam;

/** Still image encoding through libwebp. */
final class WebpCodec {

    private static final int[] PIXEL_SIDES = { 16, 32, 64, 128 };
    // 直接实例化 SPI：游戏里的类加载器不一定能让 ImageIO 自动发现插件。
    private static final WebPImageWriterSpi SPI = new WebPImageWriterSpi();

    /** 图片正好是某个小尺寸按最近邻放大的结果时返回那个边长，否则返回 0。全透明像素的颜色不参与比较。 */
    static int pixelSide(BufferedImage image) {
        int width = image.getWidth();
        if (width != image.getHeight()) return 0;
        int[] pixels = image.getRGB(0, 0, width, width, null, 0, width);
        for (int side : PIXEL_SIDES) {
            if (side < width && width % side == 0 && uniformBlocks(pixels, width, width / side)) return side;
        }
        return 0;
    }

    private static boolean uniformBlocks(int[] pixels, int width, int block) {
        for (int y = 0; y < width; y++) {
            int base = (y - y % block) * width;
            for (int x = 0; x < width; x++) {
                if (visible(pixels[y * width + x]) != visible(pixels[base + x - x % block])) return false;
            }
        }
        return true;
    }

    private static int visible(int argb) {
        return (argb >>> 24) == 0 ? 0 : argb;
    }

    /** 按块取左上角像素缩小；只在 pixelSide 认可的尺寸上使用，可逐像素还原。 */
    static BufferedImage shrink(BufferedImage image, int side) {
        int block = image.getWidth() / side;
        BufferedImage small = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < side; y++)
            for (int x = 0; x < side; x++) small.setRGB(x, y, visible(image.getRGB(x * block, y * block)));
        return small;
    }

    static byte[] lossless(BufferedImage image, int method) throws IOException {
        return encode(image, CompressionType.Lossless, 0.8f, method);
    }

    static byte[] lossy(BufferedImage image, float quality, int method) throws IOException {
        return encode(image, CompressionType.Lossy, quality, method);
    }

    private static byte[] encode(BufferedImage image, CompressionType type, float quality, int method)
        throws IOException {
        ImageWriter writer = SPI.createWriterInstance(null);
        try {
            WebPWriteParam param = (WebPWriteParam) writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType(type.name());
            param.setCompressionQuality(quality);
            param.setMethod(method);
            param.setThreadLevel(0);
            if (type == CompressionType.Lossless) param.setExact(true);
            else param.setAlphaQuality(100);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(output);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
