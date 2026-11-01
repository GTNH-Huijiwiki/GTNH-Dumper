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
        int remaining = Math.floorMod(tick, period);
        for (int i = 0; i < frames.length; i++) {
            if (remaining < durations[i]) return frames[i];
            remaining -= durations[i];
        }
        throw new AssertionError("Animation tick outside period");
    }
}
