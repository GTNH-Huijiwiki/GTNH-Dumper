package com.iouter.gtnhdumper.common.utils;

import java.util.Collections;

import com.iouter.gtnhdumper.EnderIOMixinLoader;

public final class SpriteAnimationRegression {

    private SpriteAnimationRegression() {}

    public static void main(String[] args) {
        animations();
        textures();
        EnderIOMixinLoader loader = new EnderIOMixinLoader();
        check(
            loader.getMixins(Collections.emptySet())
                .isEmpty(),
            "EnderIO remains optional");
        check(
            loader.getMixins(Collections.singleton("EnderIO"))
                .equals(Collections.singletonList("HyperCubeTextureMixin")),
            "Select texture hook");
        check(
            loader.getClass()
                .getClassLoader()
                .getResource(loader.getMixinConfig()) != null,
            "EnderIO config packaged");
        System.out.println("Sprite animation and texture fallback regression passed");
    }

    private static void animations() {
        SpriteAnimationTimeline timeline = new SpriteAnimationTimeline(
            new int[] { 2, 0, 2, 1 },
            new int[] { 2, 1, 3, 1 });
        int[] expected = { 2, 2, 0, 2, 2, 2, 1 };
        check(timeline.period == expected.length, "Frame durations retained");
        for (int i = 0; i < expected.length * 2; i++)
            check(timeline.frameAt(i) == expected[i % expected.length], "Reordered/repeated frame at " + i);
        int[] directions = new int[64];
        int[] durations = new int[64];
        for (int i = 0; i < directions.length; i++) {
            directions[i] = i;
            durations[i] = 1;
        }
        SpriteAnimationTimeline clock = new SpriteAnimationTimeline(directions, durations);
        for (int i = 0; i < 64; i++) check(clock.frameAt(i) == i, "Every world-driven frame is sampled");
        try {
            new SpriteAnimationTimeline(new int[] { 0 }, new int[] { 0 });
            throw new AssertionError("Invalid frame duration accepted");
        } catch (IllegalArgumentException expectedError) {}
    }

    private static void textures() {
        String original = "enderio:models/hyperCube.png";
        String relocated = "enderio:models/old/hyperCube.png";
        check(
            TextureResourceFallback.hyperCube(original, path -> true)
                .equals(original),
            "Resource pack takes precedence");
        check(
            TextureResourceFallback.hyperCube(original, relocated::equals)
                .equals(relocated),
            "Relocated model texture selected");
        check(
            TextureResourceFallback.hyperCube(original, path -> false)
                .equals(original),
            "Missing resources are not concealed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
