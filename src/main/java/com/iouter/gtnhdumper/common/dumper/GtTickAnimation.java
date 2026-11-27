package com.iouter.gtnhdumper.common.dumper;

import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import com.iouter.gtnhdumper.CommonProxy;

/**
 * GregTech item icons that move with GT's client tick counter (spinning transcendent metal parts and components,
 * colour-cycling materials). They have no animated texture, and the export holds that counter still, so their frames
 * are rendered by stepping it. The renderer GT actually uses decides, not the item's name.
 */
final class GtTickAnimation {

    /** Icons are shown at 32 px; a rotating 16 px sprite needs more than its own resolution to stay legible. */
    static final int FRAME_SIZE = 64;

    private static final String PACKAGE = "gregtech.common.render.items.";
    /** {@code glRotatef(ticks * 3.5 % 360)}: one turn takes 720/7 ticks. The wiki plays one frame per tick. */
    private static final GtTickAnimation SPIN = new GtTickAnimation(103, 720.0 / 7);
    /** {@code hue = ticks % 180 / 180}. */
    private static final GtTickAnimation GAIA_HUE = new GtTickAnimation(180, 180);
    /** {@code hue = ticks % 90 / 90}. */
    private static final GtTickAnimation RAINBOW_HUE = new GtTickAnimation(90, 90);
    /** The condensate overlay: one overlay frame per tick. Its frames are chosen from the picture, not a renderer. */
    static final GtTickAnimation CONDENSATE = new GtTickAnimation(
        GtCondensateOverlay.FRAMES,
        GtCondensateOverlay.FRAMES);

    final int frameCount;
    final double periodTicks;

    private GtTickAnimation(int frameCount, double periodTicks) {
        this.frameCount = frameCount;
        this.periodTicks = periodTicks;
    }

    /**
     * A seamless loop that also covers the item's own texture animation of {@code texturePeriod} ticks (1 when it has
     * none). The loop is a whole number of texture periods near one effect cycle, and the effect completes a whole
     * number of cycles within it, running slightly faster or slower than in game. The exact common period would often
     * be thousands of frames.
     */
    Loop loop(int texturePeriod) {
        int frames = (int) Math.max(1, Math.round((double) frameCount / texturePeriod)) * texturePeriod;
        int cycles = (int) Math.max(1, Math.round((double) frames / frameCount));
        return new Loop(frames, cycles * periodTicks);
    }

    static final class Loop {

        final int frameCount;
        private final double ticks;

        private Loop(int frameCount, double ticks) {
            this.frameCount = frameCount;
            this.ticks = ticks;
        }

        /** The GT animation tick of a frame; frame 0 is the unrotated pose, also used for the still icon. */
        double tickAt(int frame) {
            return frame * ticks / frameCount;
        }
    }

    static GtTickAnimation forRenderer(String className) {
        if (!className.startsWith(PACKAGE)) return null;
        switch (className.substring(PACKAGE.length())) {
            case "TranscendentalMetaItemRenderer":
            case "TranscendentMetalRenderer":
                return SPIN;
            case "GaiaSpiritRenderer":
                return GAIA_HUE;
            case "RainbowOverlayRenderer":
            case "RainbowOverlayMetaItemRenderer":
                return RAINBOW_HUE;
            default:
                return null;
        }
    }

    static GtTickAnimation forStack(ItemStack stack) {
        if (!CommonProxy.isGTLoaded) return null;
        try {
            return find(stack, 0);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("GregTech item renderer lookup changed", error);
        }
    }

    private static GtTickAnimation find(ItemStack stack, int depth) throws ReflectiveOperationException {
        if (stack == null || stack.getItem() == null || depth > 2) return null;
        IItemRenderer renderer = MinecraftForgeClient.getItemRenderer(stack, IItemRenderer.ItemRenderType.INVENTORY);
        if (renderer == null) return null;
        String name = renderer.getClass()
            .getName();
        if (name.equals(PACKAGE + "MetaGeneratedItemRenderer")) {
            // One Forge renderer per meta item; it picks the per-item or per-material renderer itself.
            java.lang.reflect.Method select = renderer.getClass()
                .getDeclaredMethod("getRendererForItemStack", ItemStack.class);
            select.setAccessible(true);
            name = select.invoke(renderer, stack)
                .getClass()
                .getName();
        } else if (name.equals(PACKAGE + "CircuitComponentItemRenderer")) {
            return findCircuitComponent(stack, depth);
        }
        return forRenderer(name);
    }

    /** Nanochip components draw their real item, except processed ones, which have their own sprite. */
    private static GtTickAnimation findCircuitComponent(ItemStack stack, int depth)
        throws ReflectiveOperationException {
        Class<?> type = Class.forName("gregtech.common.tileentities.machines.multi.nanochip.util.CircuitComponent");
        Object component = type.getMethod("tryGetFromFakeStack", ItemStack.class)
            .invoke(null, stack);
        if (component == null) return null;
        if (type.getField("isProcessed")
            .getBoolean(component))
            return ((Enum<?>) component).name()
                .equals("ProcessedBoltTranscendentMetal") ? SPIN : null;
        Object real = ((Supplier<?>) type.getField("realComponent")
            .get(component)).get();
        return find((ItemStack) real, depth + 1);
    }
}
