package com.iouter.gtnhdumper.common.utils;

/** Scope in which newly constructed entities get a class-derived random seed (see EntitySeedMixin). */
public final class EntitySeeds {

    private static volatile int depth;

    private EntitySeeds() {}

    public static synchronized void begin() {
        depth++;
    }

    public static synchronized void end() {
        if (depth > 0) depth--;
    }

    public static boolean active() {
        return depth > 0 || IconRenderClock.isFrozen();
    }
}
