package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.angelica.glsm.GLStateManager;

import cpw.mods.fml.relauncher.ReflectionHelper;

/** Samples stored sprite frames explicitly; compass/clock updateAnimation follows the world instead. */
public class DynamicTexture {

    private static final Field frameCounterField = ReflectionHelper
        .findField(TextureAtlasSprite.class, "frameCounter", "field_110973_g");
    private static final Field animationMetadataField = ReflectionHelper
        .findField(TextureAtlasSprite.class, "animationMetadata", "field_110982_k");

    public final Set<TextureAtlasSprite> textures = new HashSet<>();
    private final Map<TextureAtlasSprite, SpriteAnimationTimeline> timelines = new HashMap<>();
    private final Map<TextureAtlasSprite, Integer> originalFrames = new HashMap<>();
    private final ResourceLocation atlas;
    public final int lcm;

    public DynamicTexture(ItemStack stack) {
        atlas = stack.getItem()
            .getSpriteNumber() == 0 ? TextureMap.locationBlocksTexture : TextureMap.locationItemsTexture;
        BigInteger period = BigInteger.ONE;
        for (TextureAtlasSprite texture : getTextures(stack)) {
            if (texture.getFrameCount() <= 1 || !textures.add(texture)) continue;
            AnimationMetadataSection animation = getAnimationMetadata(texture);
            int count = animation == null || animation.getFrameCount() == 0 ? texture.getFrameCount()
                : animation.getFrameCount();
            int[] frames = new int[count];
            int[] durations = new int[count];
            for (int i = 0; i < count; i++) {
                frames[i] = animation == null || animation.getFrameCount() == 0 ? i : animation.getFrameIndex(i);
                durations[i] = animation == null ? 1 : animation.getFrameTimeSingle(i);
                if (frames[i] < 0 || frames[i] >= texture.getFrameCount()
                    || texture.getFrameTextureData(frames[i]) == null)
                    throw new IllegalStateException(
                        "Missing animation frame " + frames[i] + " for " + texture.getIconName());
            }
            SpriteAnimationTimeline timeline = new SpriteAnimationTimeline(frames, durations);
            timelines.put(texture, timeline);
            int counter = getFrameCount(texture);
            // Vanilla clock/compass and their subclasses index stored images directly.
            boolean direct = texture instanceof net.minecraft.client.renderer.texture.TextureClock
                || texture instanceof net.minecraft.client.renderer.texture.TextureCompass;
            originalFrames.put(texture, direct ? counter : frames[Math.floorMod(counter, count)]);
            BigInteger spritePeriod = BigInteger.valueOf(timeline.period);
            period = period.divide(period.gcd(spritePeriod))
                .multiply(spritePeriod);
        }
        lcm = period.intValueExact();
    }

    public void uploadFrame(int tick) {
        upload(false, tick);
    }

    public void restore() {
        upload(true, 0);
    }

    private void upload(boolean restore, int tick) {
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(atlas);
            for (TextureAtlasSprite texture : textures) {
                int frame = restore ? originalFrames.get(texture)
                    : timelines.get(texture)
                        .frameAt(tick);
                TextureUtil.uploadTextureMipmap(
                    texture.getFrameTextureData(frame),
                    texture.getIconWidth(),
                    texture.getIconHeight(),
                    texture.getOriginX(),
                    texture.getOriginY(),
                    false,
                    false);
            }
        } finally {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    private static TextureAtlasSprite[] getTextures(ItemStack stack) {
        List<TextureAtlasSprite> textures = new ArrayList<>();
        Item item = stack.getItem();
        if (item instanceof ItemBlock) {
            Block block = ((ItemBlock) item).field_150939_a;
            if (RenderBlocks.renderItemIn3d(block.getRenderType())) {
                for (int i = 0; i < 6; i++) {
                    IIcon icon = block.getIcon(i, stack.getItemDamage());
                    if (icon instanceof TextureAtlasSprite sprite) textures.add(sprite);
                }
            } else if (stack.getIconIndex() instanceof TextureAtlasSprite sprite) {
                textures.add(sprite);
            }
        } else {
            int passes = item.getRenderPasses(stack.getItemDamage());
            for (int i = 0; i < passes; i++) {
                IIcon icon = passes == 1 ? stack.getIconIndex()
                    : item.getIconFromDamageForRenderPass(stack.getItemDamage(), i);
                if (icon instanceof TextureAtlasSprite sprite) textures.add(sprite);
            }
        }
        return textures.toArray(new TextureAtlasSprite[0]);
    }

    public static AnimationMetadataSection getAnimationMetadata(TextureAtlasSprite texture) {
        try {
            return (AnimationMetadataSection) animationMetadataField.get(texture);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static int getFrameCount(TextureAtlasSprite texture) {
        try {
            return frameCounterField.getInt(texture);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isDynamic() {
        return !textures.isEmpty();
    }
}
