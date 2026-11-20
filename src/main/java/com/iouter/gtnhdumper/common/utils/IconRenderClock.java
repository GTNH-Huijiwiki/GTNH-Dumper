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
