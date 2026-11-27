package com.iouter.gtnhdumper.common.utils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 把等长的帧拼成无损动画 WebP。编码库只能写静态图，这里按 WebP 容器格式自己封装：
 * RIFF/WEBP → VP8X、ANIM、若干 ANMF（每个内含一张静态图的 VP8L 块）。
 * 相邻的相同帧合并时长；之后每帧只存与上一帧不同的矩形，以“不混合、不清除”覆盖到画布上。
 */
final class WebpAnimation {

    private static final class Frame {

        final int[] pixels;
        int millis;

        Frame(int[] pixels, int millis) {
            this.pixels = pixels;
            this.millis = millis;
        }
    }

    static byte[] encode(List<BufferedImage> images, int frameMillis, int method) throws IOException {
        int width = images.get(0)
            .getWidth();
        int height = images.get(0)
            .getHeight();
        List<Frame> frames = new ArrayList<>();
        for (BufferedImage image : images) {
            int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            for (int i = 0; i < pixels.length; i++) if ((pixels[i] >>> 24) == 0) pixels[i] = 0;
            Frame last = frames.isEmpty() ? null : frames.get(frames.size() - 1);
            if (last != null && Arrays.equals(last.pixels, pixels)) last.millis += frameMillis;
            else frames.add(new Frame(pixels, frameMillis));
        }
        // 各帧其实都一样时就是一张静态图。
        if (frames.size() == 1)
            return WebpCodec.lossless(toImage(frames.get(0).pixels, width, 0, 0, width, height), method);

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.write(0x10 | 0x02); // 含透明度、含动画
        header.write(0);
        header.write(0);
        header.write(0);
        int24(header, width - 1);
        int24(header, height - 1);
        chunk(body, "VP8X", header.toByteArray());
        chunk(body, "ANIM", new byte[] { 0, 0, 0, 0, 0, 0 }); // 透明背景，无限循环

        int[] canvas = null;
        for (Frame frame : frames) {
            int left = 0, top = 0, right = width, bottom = height;
            if (canvas != null) {
                left = width;
                top = height;
                right = 0;
                bottom = 0;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        if (frame.pixels[y * width + x] == canvas[y * width + x]) continue;
                        left = Math.min(left, x);
                        top = Math.min(top, y);
                        right = Math.max(right, x + 1);
                        bottom = Math.max(bottom, y + 1);
                    }
                }
                // 格式要求偏移是偶数。
                left &= ~1;
                top &= ~1;
            }
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            int24(data, left / 2);
            int24(data, top / 2);
            int24(data, right - left - 1);
            int24(data, bottom - top - 1);
            int24(data, frame.millis);
            data.write(0x02); // 不与上一帧混合，显示后不清除
            byte[] still = WebpCodec
                .lossless(toImage(frame.pixels, width, left, top, right - left, bottom - top), method);
            copyImageChunks(still, data);
            chunk(body, "ANMF", data.toByteArray());
            canvas = frame.pixels;
        }

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        ascii(file, "RIFF");
        int32(file, body.size() + 4);
        ascii(file, "WEBP");
        body.writeTo(file);
        return file.toByteArray();
    }

    private static BufferedImage toImage(int[] pixels, int stride, int left, int top, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, top * stride + left, stride);
        return image;
    }

    /** 静态图文件里的图像数据块（VP8L，或 ALPH 加 VP8 ），原样放进 ANMF。 */
    private static void copyImageChunks(byte[] still, ByteArrayOutputStream target) throws IOException {
        boolean found = false;
        for (int at = 12; at + 8 <= still.length;) {
            String type = new String(still, at, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = (still[at + 4] & 255) | (still[at + 5] & 255) << 8
                | (still[at + 6] & 255) << 16
                | (still[at + 7] & 255) << 24;
            int padded = size + (size & 1);
            if (type.equals("VP8L") || type.equals("VP8 ") || type.equals("ALPH")) {
                target.write(still, at, 8 + padded);
                found |= !type.equals("ALPH");
            }
            at += 8 + padded;
        }
        if (!found) throw new IOException("No image chunk in encoded WebP frame");
    }

    private static void chunk(ByteArrayOutputStream target, String type, byte[] data) throws IOException {
        ascii(target, type);
        int32(target, data.length);
        target.write(data);
        if ((data.length & 1) != 0) target.write(0);
    }

    private static void ascii(ByteArrayOutputStream target, String text) {
        for (char c : text.toCharArray()) target.write(c);
    }

    private static void int24(ByteArrayOutputStream target, int value) {
        target.write(value & 255);
        target.write((value >> 8) & 255);
        target.write((value >> 16) & 255);
    }

    private static void int32(ByteArrayOutputStream target, int value) {
        int24(target, value);
        target.write((value >>> 24) & 255);
    }
}
