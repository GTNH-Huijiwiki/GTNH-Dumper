package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Field;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.iouter.gtnhdumper.Tags;

import cpw.mods.fml.common.Loader;

/**
 * Amazing Trophies draws block-structure trophy models from a VBO that is built on first use and cached. Built
 * during the icon export, the cached geometry comes out shifted by a different amount each run, so structure
 * trophies render beside their pedestal. Before exporting icons, swap the shared renderer for the plain one,
 * which draws the blocks every frame. The mod chooses between the two only by {@code -Damazingtrophies.disableVBO};
 * {@code RenderHelper.INSTANCE} is static final, so it is replaced through Unsafe.
 */
public final class TrophyRenderCompat {

    private static final Logger LOG = LogManager.getLogger(Tags.MODID);
    private static boolean done;

    private TrophyRenderCompat() {}

    public static synchronized void useImmediateRenderer() {
        if (done || !Loader.isModLoaded("amazingtrophies")) return;
        done = true;
        try {
            Class<?> helper = Class.forName("glowredman.amazingtrophies.model.complex.RenderHelper");
            Field instance = helper.getDeclaredField("INSTANCE");
            Object current = instance.get(null);
            if (current.getClass() == helper) return; // VBO already disabled
            Object plain = helper.getConstructor()
                .newInstance();
            Field theUnsafe = Class.forName("sun.misc.Unsafe")
                .getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
            unsafe.putObject(unsafe.staticFieldBase(instance), unsafe.staticFieldOffset(instance), plain);
            LOG.info("Amazing Trophies structure models: using the immediate renderer for icon export");
        } catch (Exception | LinkageError e) {
            LOG.error("Cannot switch Amazing Trophies to the immediate renderer", e);
        }
    }
}
