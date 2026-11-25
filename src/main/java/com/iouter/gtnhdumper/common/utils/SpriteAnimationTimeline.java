package com.iouter.gtnhdumper.common.utils;

/** Explicit animation order and tick durations, independent of live world-driven sprite counters. */
public final class SpriteAnimationTimeline {

    private final int[] frames;
    private final int[] durations;
    public final int period;

    public SpriteAnimationTimeline(int[] frames, int[] durations) {
        if (frames.length == 0 || frames.length != durations.length)
            throw new IllegalArgumentException("Animation needs matching, nonempty frame and duration arrays");
        this.frames = frames.clone();
        this.durations = durations.clone();
        int total = 0;
        for (int i = 0; i < frames.length; i++) {
            if (frames[i] < 0 || durations[i] <= 0) throw new IllegalArgumentException("Invalid animation frame");
            total = Math.addExact(total, durations[i]);
        }
        period = total;
    }

    public int frameAt(int tick) {
        return positionAt(tick).frame;
    }

    public Position positionAt(int tick) {
        int remaining = Math.floorMod(tick, period);
        for (int i = 0; i < frames.length; i++) {
            if (remaining < durations[i])
                return new Position(frames[i], frames[(i + 1) % frames.length], (double) remaining / durations[i]);
            remaining -= durations[i];
        }
        throw new AssertionError("Animation tick outside period");
    }

    /** The stored frame shown at a tick, the one that follows it, and how far the tick is towards it (0 to 1). */
    public static final class Position {

        public final int frame;
        public final int nextFrame;
        public final double progress;

        private Position(int frame, int nextFrame, double progress) {
            this.frame = frame;
            this.nextFrame = nextFrame;
            this.progress = progress;
        }
    }

    /**
     * Cross-fades two frames' mipmap levels the way Angelica's {@code InterpolatedIcon} does for sprites whose
     * {@code .mcmeta} sets {@code interpolate}: alpha is mixed linearly and each side's colour counts in proportion to
     * the alpha it contributes, so fully transparent pixels do not darken the result.
     */
    public static int[][] blend(int[][] from, int[][] to, double progress) {
        int[][] result = new int[from.length][];
        for (int level = 0; level < from.length; level++) {
            if (from[level] == null) continue;
            // A level the next frame lacks keeps the current pixels.
            if (level >= to.length || to[level] == null || to[level].length != from[level].length) {
                result[level] = from[level];
                continue;
            }
            int[] pixels = new int[from[level].length];
            for (int i = 0; i < pixels.length; i++) pixels[i] = blendPixel(from[level][i], to[level][i], progress);
            result[level] = pixels;
        }
        return result;
    }

    private static int blendPixel(int from, int to, double progress) {
        double fromAlpha = (from >>> 24) * (1 - progress);
        double toAlpha = (to >>> 24) * progress;
        double alpha = fromAlpha + toAlpha;
        double fromWeight = alpha == 0 ? 1 - progress : fromAlpha / alpha;
        double toWeight = alpha == 0 ? progress : toAlpha / alpha;
        int result = (int) Math.round(alpha) << 24;
        for (int shift = 16; shift >= 0; shift -= 8)
            result |= (int) Math.round(((from >> shift) & 0xff) * fromWeight + ((to >> shift) & 0xff) * toWeight)
                << shift;
        return result;
    }
}
