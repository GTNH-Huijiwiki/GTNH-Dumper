package com.iouter.gtnhdumper.common.utils;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import net.minecraft.world.storage.WorldInfo;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Pins the clocks that item renderers read, so two exports of the same pack render identical pixels.
 * <p>
 * Covers {@code Minecraft.getSystemTime()} (enchantment glint, colour cycling), world time and partial ticks (mob
 * spawner entities, rotating models) and the player's tick counter. Renderers that read {@code System} time directly
 * stay time dependent; downstream tools detect those by comparing pixel hashes of repeated exports.
 */
public final class IconRenderClock {

    public static final long SYSTEM_TIME = 3_600_000L;
    private static final long WORLD_TIME = 6000L;

    private static volatile boolean frozen;
    private static long savedWorldTime;
    private static long savedTotalWorldTime;
    private static float savedPartialTicks;
    private static int savedPlayerTicks;
    private static boolean worldSaved;
    private static String failure;

    private IconRenderClock() {}

    public static boolean isFrozen() {
        return frozen;
    }

    public static synchronized void freeze() {
        if (frozen) return;
        failure = null;
        Minecraft minecraft = Minecraft.getMinecraft();
        try {
            if (minecraft.theWorld != null) {
                WorldInfo info = minecraft.theWorld.getWorldInfo();
                savedWorldTime = info.getWorldTime();
                savedTotalWorldTime = info.getWorldTotalTime();
                info.setWorldTime(WORLD_TIME);
                info.incrementTotalWorldTime(WORLD_TIME);
                worldSaved = true;
            }
            Timer timer = timer(minecraft);
            savedPartialTicks = timer.renderPartialTicks;
            timer.renderPartialTicks = 0f;
            if (minecraft.thePlayer != null) {
                savedPlayerTicks = minecraft.thePlayer.ticksExisted;
                minecraft.thePlayer.ticksExisted = (int) WORLD_TIME;
            }
        } catch (RuntimeException error) {
            failure = error.toString();
        }
        frozen = true;
    }

    public static synchronized void release() {
        if (!frozen) return;
        frozen = false;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (worldSaved && minecraft.theWorld != null) {
            WorldInfo info = minecraft.theWorld.getWorldInfo();
            info.setWorldTime(savedWorldTime);
            info.incrementTotalWorldTime(savedTotalWorldTime);
        }
        worldSaved = false;
        try {
            timer(minecraft).renderPartialTicks = savedPartialTicks;
        } catch (RuntimeException ignored) {}
        if (minecraft.thePlayer != null) minecraft.thePlayer.ticksExisted = savedPlayerTicks;
    }

    public static synchronized Map<String, Object> report() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("systemTime", SYSTEM_TIME);
        report.put("worldTime", WORLD_TIME);
        report.put("partialTicks", 0);
        report.put("failure", failure);
        return report;
    }

    private static Timer timer(Minecraft minecraft) {
        return ReflectionHelper.getPrivateValue(Minecraft.class, minecraft, "timer", "field_71428_T");
    }
}
