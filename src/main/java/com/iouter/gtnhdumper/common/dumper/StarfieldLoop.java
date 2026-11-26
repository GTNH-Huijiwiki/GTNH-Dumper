package com.iouter.gtnhdumper.common.dumper;

import java.awt.image.BufferedImage;

/**
 * The loop of an icon drawn with GTNHLib's star field (Universium, Avaritia's cosmic items). The sixteen star layers
 * drift at their own speeds, so the picture never returns to where it was: the loop renders some frames beyond its end
 * and fades them into its beginning.
 */
final class StarfieldLoop {

    /** One frame per game tick, as the wiki plays them: six seconds. */
    private static final int FRAMES = 120;
    /** Longer loops only for textures whose own animation needs them; beyond this the texture is allowed to jump. */
    private static final int MAX_FRAMES = 480;
    static final int FADE_FRAMES = 30;
    static final int TICK_MILLIS = 50;

    private StarfieldLoop() {}

    /** A whole number of the item's own texture animation, which is 1 tick long when it has none. */
    static int frameCount(int texturePeriod) {
        int frames = (FRAMES + texturePeriod - 1) / texturePeriod * texturePeriod;
        return frames > MAX_FRAMES ? FRAMES : frames;
    }

    /**
     * @param rendered the loop's frames followed by {@code fade} frames continuing past its end.
     * @return the loop: it starts as the continuation of its last frame and turns into the first rendered frames.
     */
    static BufferedImage[] close(BufferedImage[] rendered, int fade) {
        BufferedImage[] loop = new BufferedImage[rendered.length - fade];
        System.arraycopy(rendered, 0, loop, 0, loop.length);
        for (int i = 0; i < fade; i++) loop[i] = blend(rendered[loop.length + i], rendered[i], (double) i / fade);
        return loop;
    }

    /** Mixes two images of one size by premultiplied alpha; {@code weight} is the share of {@code to}. */
    private static BufferedImage blend(BufferedImage from, BufferedImage to, double weight) {
        int width = from.getWidth();
        int height = from.getHeight();
        int[] a = from.getRGB(0, 0, width, height, null, 0, width);
        int[] b = to.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < a.length; i++) {
            if (a[i] == b[i]) continue;
            double alphaA = (a[i] >>> 24) * (1 - weight);
            double alphaB = (b[i] >>> 24) * weight;
            double alpha = alphaA + alphaB;
            int mixed = (int) Math.round(alpha) << 24;
            if (alpha > 0) for (int shift = 0; shift < 24; shift += 8) {
                mixed |= (int) Math.round(((a[i] >>> shift & 0xff) * alphaA + (b[i] >>> shift & 0xff) * alphaB) / alpha)
                    << shift;
            }
            a[i] = mixed;
        }
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, width, height, a, 0, width);
        return result;
    }
}
