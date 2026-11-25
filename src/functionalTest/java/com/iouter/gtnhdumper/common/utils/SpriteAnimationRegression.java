package com.iouter.gtnhdumper.common.utils;

import java.util.Collections;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.iouter.gtnhdumper.DeterminismTransformer;
import com.iouter.gtnhdumper.EnderIOMixinLoader;

public final class SpriteAnimationRegression {

    private SpriteAnimationRegression() {}

    public static void main(String[] args) {
        animations();
        interpolation();
        clockRewrite();
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

    private static void interpolation() {
        SpriteAnimationTimeline timeline = new SpriteAnimationTimeline(new int[] { 0, 1 }, new int[] { 4, 4 });
        SpriteAnimationTimeline.Position position = timeline.positionAt(5);
        check(
            position.frame == 1 && position.nextFrame == 0 && position.progress == 0.25,
            "Position inside a frame, wrapping to the first");
        int[][] from = { { 0xffff0000, 0x00000000 } };
        int[][] to = { { 0xff0000ff, 0xff00ff00 } };
        int[][] half = SpriteAnimationTimeline.blend(from, to, 0.5);
        check(half[0][0] == 0xff800080, "Opaque pixels cross-fade");
        check(half[0][1] == 0x8000ff00, "Transparent pixels contribute no colour");
        check(SpriteAnimationTimeline.blend(from, to, 0)[0][0] == 0xffff0000, "The first tick shows the stored frame");
    }

    private static void clockRewrite() {
        ClassNode node = new ClassNode();
        MethodNode method = new MethodNode();
        MethodInsnNode wall = new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "java/lang/System",
            "currentTimeMillis",
            "()J",
            false);
        MethodInsnNode lwjgl = new MethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjglx/Sys", "getTime", "()J", false);
        MethodInsnNode random = new MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            "java/util/Random",
            "nextDouble",
            "()D",
            false);
        method.instructions.add(wall);
        method.instructions.add(lwjgl);
        method.instructions.add(random);
        method.instructions.add(
            new FieldInsnNode(
                Opcodes.GETFIELD,
                "makeo/gadomancy/client/util/FamiliarHandlerClient$PartialEntityFamiliar",
                "ticksExisted",
                "I"));
        node.methods.add(method);
        String clockClass = "makeo.gadomancy.client.renderers.item.ItemRenderFamiliar";
        check(DeterminismTransformer.rewrite(clockClass, node) == 3, "Two clock reads and the familiar age");
        check(wall.owner.endsWith("IconRenderClock") && wall.name.equals("currentTimeMillis"), "System clock");
        check(lwjgl.name.equals("lwjglTime"), "LWJGL timer under either package name");
        check(random.getOpcode() == Opcodes.INVOKEVIRTUAL, "Random calls only change in listed classes");
        MethodInsnNode age = (MethodInsnNode) method.instructions.getLast();
        check(age.name.equals("frozenTicks") && age.desc.equals("(I)I"), "Familiar age passes through the clock");
        String randomClass = "makeo.gadomancy.common.utils.RandomizedAspectList";
        check(DeterminismTransformer.rewrite(randomClass, node) == 1, "Random call redirected");
        check(
            random.getOpcode() == Opcodes.INVOKESTATIC && random.desc.equals("(Ljava/util/Random;)D"),
            "The receiver becomes the argument");
        seededModel();
    }

    private static void seededModel() {
        ClassNode node = new ClassNode();
        MethodNode method = new MethodNode();
        MethodInsnNode constructor = new MethodInsnNode(
            Opcodes.INVOKESPECIAL,
            "java/util/Random",
            "<init>",
            "()V",
            false);
        method.instructions.add(constructor);
        method.maxStack = 2;
        node.methods.add(method);
        check(
            DeterminismTransformer.rewrite("twilightforest.client.model.ModelTFMosquitoSwarm", node) == 1,
            "Unseeded generator found");
        check(
            constructor.desc.equals("(J)V") && constructor.getPrevious() instanceof LdcInsnNode && method.maxStack == 4,
            "The seed is pushed and the stack has room for it");
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
