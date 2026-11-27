package com.iouter.gtnhdumper.common.dumper;

import java.awt.image.BufferedImage;

/** Clock-driven captures retain repeated frames to preserve duration and frame positions. */
public final class TimedIconFramesRegression {

    public static void main(String[] args) {
        com.iouter.gtnhdumper.common.utils.SpriteAnimationRegression.main(args);
        checkProfiles();
        checkTickLoops();
        checkFrameStrip();
    }

    private static void checkTickLoops() {
        GtTickAnimation spin = GtTickAnimation
            .forRenderer("gregtech.common.render.items.TranscendentalMetaItemRenderer");
        GtTickAnimation gaia = GtTickAnimation.forRenderer("gregtech.common.render.items.GaiaSpiritRenderer");
        if (GtTickAnimation.forRenderer("gregtech.common.render.items.GeneratedMaterialRenderer") != null)
            throw new AssertionError("Still renderer treated as animated");
        GtTickAnimation.Loop plain = spin.loop(1);
        if (plain.frameCount != 103 || plain.tickAt(0) != 0 || Math.abs(plain.tickAt(103) * 3.5 - 360) > 1e-9)
            throw new AssertionError("One turn per loop expected");
        // A texture animation of its own: whole texture periods, whole effect cycles.
        checkLoop(gaia.loop(182), 182, 180);
        checkLoop(spin.loop(90), 90, 720.0 / 7);
        checkLoop(gaia.loop(8), 184, 180);
        checkLoop(gaia.loop(500), 500, 540);
        checkGlitch();
    }

    private static void checkGlitch() {
        for (String name : new String[] { "GlitchEffectRenderer", "GlitchEffectMetaItemRenderer" })
            if (GtTickAnimation.forRenderer("gregtech.common.render.items." + name) != GtTickAnimation.GLITCH)
                throw new AssertionError(name + " is not captured as the glitch animation");
        checkLoop(GtTickAnimation.GLITCH.loop(1), 40, 40);
        // GT counts 10 ms steps within a two second cycle: it glitches up to step 40 and rerolls every fifth step.
        int glitched = 0;
        for (int tick = 0; tick < 40; tick++) {
            long step = GtTickAnimation.glitchMillis(tick) % 2000 / 10;
            if (step % 5 != 0) throw new AssertionError("Tick " + tick + " misses the renderer's offset update");
            if (step <= 40) glitched++;
            if (tick == 0 && step <= 40) throw new AssertionError("The still frame is glitched");
        }
        if (glitched != 9) throw new AssertionError("Expected 9 glitched frames, got " + glitched);
    }

    private static void checkLoop(GtTickAnimation.Loop loop, int frames, double ticks) {
        if (loop.frameCount != frames || Math.abs(loop.tickAt(frames) - ticks) > 1e-9)
            throw new AssertionError("Loop " + loop.frameCount + " frames over " + loop.tickAt(loop.frameCount));
    }

    private static void checkProfiles() {
        ItemIconRenderProfile normal = ItemIconRenderProfile.select("minecraft:diamond", 0);
        if (normal.sampling != null || normal.disableCull) throw new AssertionError("Ordinary item policy changed");
        ItemIconRenderProfile raw = ItemIconRenderProfile.select("gregtech:gt.metaitem.01", 32415);
        ItemIconRenderProfile energized = ItemIconRenderProfile.select("gregtech:gt.metaitem.01", 32417);
        if (raw != energized || !raw.disableCull) throw new AssertionError("Tesseract profile mismatch");
        if (raw.sampling.frameCount != 120 || raw.sampling.frameSize != 64 || raw.sampling.intervalMillis != 50)
            throw new AssertionError("Tesseract sampling contract changed");
        if (ItemIconRenderProfile.select("other:item", 32415) != normal
            || ItemIconRenderProfile.select("gregtech:gt.metaitem.01", 32416) != normal)
            throw new AssertionError("Profile must match both item identity and damage");
        // Sampling and GL state are separate requirements, including for static custom renderers.
        ItemIconRenderProfile samplingOnly = new ItemIconRenderProfile(raw.sampling, false);
        ItemIconRenderProfile cullOnly = new ItemIconRenderProfile(null, true);
        if (samplingOnly.sampling == null || samplingOnly.disableCull
            || cullOnly.sampling != null
            || !cullOnly.disableCull) throw new AssertionError("Sampling and culling became coupled");
    }

    private static void checkFrameStrip() {
        BufferedImage[] frames = new BufferedImage[120];
        for (int i = 0; i < frames.length; i++) frames[i] = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        BufferedImage strip = ItemIconDumper.concatenateImages(frames, false);
        if (strip.getWidth() != 7680 || strip.getHeight() != 64) throw new AssertionError("Frame metadata mismatch");
        for (int i = 0; i < frames.length; i++) frames[i].setRGB(0, 0, 0xff000000 | i);
        strip = ItemIconDumper.concatenateImages(frames, false);
        for (int i = 0; i < frames.length; i++)
            if (strip.getRGB(i * 64, 0) != (0xff000000 | i)) throw new AssertionError("Lost or reordered frame " + i);
        BufferedImage[] duplicates = { frames[0], frames[0] };
        if (ItemIconDumper.concatenateImages(duplicates)
            .getWidth() != 64) throw new AssertionError("Existing texture deduplication changed");
    }
}
