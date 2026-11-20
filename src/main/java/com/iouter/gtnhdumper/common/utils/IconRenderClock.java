package com.iouter.gtnhdumper.common.utils;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.util.Timer;
import net.minecraft.world.storage.WorldInfo;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.angelica.glsm.GLStateManager;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Pins the clocks that item renderers read, so two exports of the same pack render identical pixels.
 * <p>
 * Covers {@code Minecraft.getSystemTime()} (enchantment glint, colour cycling), world time and partial ticks (mob
 * spawner entities, rotating models), the player's tick counter, and the animated atlas textures: every animated sprite
 * is reset to its first frame. Fluid textures drawn by fluid cells, flasks and facades are not the item's own icon, so
 * they would otherwise keep whichever frame the atlas showed when the export started (Hodgepodge only animates recently
 * drawn textures, so that frame differs from run to run). Renderers that read {@code System} time directly
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
    private static int resetSprites;
    /** Saved values of mod animation counters: field, owner (null when static), original value. */
    private static final java.util.List<Object[]> savedCounters = new java.util.ArrayList<>();
    private static final Map<String, String> counterFailures = new LinkedHashMap<>();
    private static int frozenCounters;

    /**
     * Mod animation counters read by item renderers: class, field, fixed value. The owner is the static field's class,
     * or for GT the client proxy instance. Missing mods are skipped.
     */
    private static final Object[][] COUNTERS = { { "gregtech.common.GTClient", "mAnimationTick", 6000L },
        { "gregtech.common.GTClient", "renderTickTime", 0f },
        { "vazkii.botania.client.core.handler.ClientTickHandler", "ticksInGame", 6000 },
        { "vazkii.botania.client.core.handler.ClientTickHandler", "partialTicks", 0f },
        { "thaumic.tinkerer.client.core.handler.ClientTickHandler", "elapsedTicks", 6000 }, };
    private static final java.lang.reflect.Field FRAME_COUNTER = ReflectionHelper
        .findField(TextureAtlasSprite.class, "frameCounter", "field_110973_g");
    private static final java.lang.reflect.Field TICK_COUNTER = ReflectionHelper
        .findField(TextureAtlasSprite.class, "tickCounter", "field_110983_h");
    private static final java.lang.reflect.Field UPLOADED_SPRITES = ReflectionHelper
        .findField(TextureMap.class, "mapUploadedSprites", "field_94252_e");

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
            resetSprites = resetAnimations(minecraft, TextureMap.locationBlocksTexture)
                + resetAnimations(minecraft, TextureMap.locationItemsTexture);
            if (minecraft.theWorld != null) minecraft.theWorld.rand.setSeed(WORLD_TIME);
            freezeCounters();
        } catch (RuntimeException | ReflectiveOperationException error) {
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
        for (Object[] saved : savedCounters) {
            try {
                ((java.lang.reflect.Field) saved[0]).set(saved[1], saved[2]);
            } catch (ReflectiveOperationException ignored) {}
        }
        savedCounters.clear();
    }

    private static void freezeCounters() {
        savedCounters.clear();
        counterFailures.clear();
        for (Object[] counter : COUNTERS) {
            String name = counter[0] + "." + counter[1];
            try {
                Class<?> owner = Class.forName((String) counter[0]);
                java.lang.reflect.Field field = owner.getDeclaredField((String) counter[1]);
                field.setAccessible(true);
                Object target = null;
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    // GT keeps its counter on the client proxy.
                    target = Class.forName("gregtech.GTMod")
                        .getMethod("clientProxy")
                        .invoke(null);
                    if (target == null) continue;
                }
                savedCounters.add(new Object[] { field, target, field.get(target) });
                field.set(target, counter[2]);
            } catch (ClassNotFoundException missingMod) {
                // Mod not installed.
            } catch (ReflectiveOperationException | RuntimeException error) {
                counterFailures.put(name, error.toString());
            }
        }
        frozenCounters = savedCounters.size();
    }

    /** {@code System.currentTimeMillis()} for redirected item renderers. */
    public static long currentTimeMillis() {
        return frozen ? SYSTEM_TIME : System.currentTimeMillis();
    }

    /** {@code System.nanoTime()} for redirected item renderers. */
    public static long nanoTime() {
        return frozen ? SYSTEM_TIME * 1_000_000L : System.nanoTime();
    }

    /** Uploads the first animation frame of every animated sprite and restarts its counters. */
    private static int resetAnimations(Minecraft minecraft, net.minecraft.util.ResourceLocation location)
        throws ReflectiveOperationException {
        if (!(minecraft.getTextureManager()
            .getTexture(location) instanceof TextureMap atlas)) return 0;
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int count = 0;
        try {
            minecraft.getTextureManager()
                .bindTexture(location);
            for (Object value : ((Map<?, ?>) UPLOADED_SPRITES.get(atlas)).values()) {
                if (!(value instanceof TextureAtlasSprite sprite) || sprite.getFrameCount() <= 1) continue;
                AnimationMetadataSection animation = DynamicTexture.getAnimationMetadata(sprite);
                int first = animation == null || animation.getFrameCount() == 0 ? 0 : animation.getFrameIndex(0);
                if (first < 0 || first >= sprite.getFrameCount() || sprite.getFrameTextureData(first) == null) continue;
                TextureUtil.uploadTextureMipmap(
                    sprite.getFrameTextureData(first),
                    sprite.getIconWidth(),
                    sprite.getIconHeight(),
                    sprite.getOriginX(),
                    sprite.getOriginY(),
                    false,
                    false);
                FRAME_COUNTER.setInt(sprite, 0);
                TICK_COUNTER.setInt(sprite, 0);
                count++;
            }
        } finally {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
        return count;
    }

    public static synchronized Map<String, Object> report() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("resetAnimatedSprites", resetSprites);
        report.put("frozenCounters", frozenCounters);
        report.put("counterFailures", new LinkedHashMap<>(counterFailures));
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
