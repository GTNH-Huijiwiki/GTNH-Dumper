package com.iouter.gtnhdumper.common.utils;

import java.util.Locale;

import com.iouter.gtnhdumper.automation.ExportSession;

/**
 * Halos are drawn under an item and reach well past its slot, so a slot-sized icon only keeps a clipped grey square of
 * them. While exporting WebP icons they are left out of the picture and recorded instead, for the wiki to draw as a
 * layer of its own. The legacy PNG export keeps the clipped halo, and normal play is unchanged.
 */
public final class IconHalo {

    private static String removed;

    private IconHalo() {}

    public static boolean isRemoving() {
        return IconRenderClock.isFrozen() && !ExportSession.legacyPng();
    }

    /**
     * Called where a renderer is about to draw a halo.
     *
     * @param texture icon name of the halo texture, such as {@code avaritia:halonoise}
     * @param argb    the colour the texture is multiplied by
     * @param spread  how far it reaches past each edge of the 16 pixel slot
     * @return true when the halo must not be drawn
     */
    public static boolean remove(String texture, int argb, int spread) {
        if (!isRemoving()) return false;
        // The first one is the item's own; anything drawn later is on top of it.
        if (removed == null) removed = describe(texture, argb, spread);
        return true;
    }

    static String describe(String texture, int argb, int spread) {
        return String.format(Locale.ROOT, "%s|%08x|%d", texture, argb, spread);
    }

    /** Returns the halo removed since the last call, or null, and forgets it. */
    public static String take() {
        String halo = removed;
        removed = null;
        return halo;
    }
}
