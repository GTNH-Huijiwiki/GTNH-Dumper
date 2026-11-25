package com.iouter.gtnhdumper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Points classes whose output depends on the real clock, unseeded random numbers or hash order at fixed values while
 * exporting. The replacements behave as before outside an export, so normal play is unchanged. Class lists instead of
 * one mixin per class: the same few calls repeat across many mods, in static and instance methods alike. Classes that
 * read {@code Minecraft.getSystemTime()} need no clock entry, that method is frozen itself.
 */
public final class DeterminismTransformer implements IClassTransformer {

    /** {@code System} property prefix under which each rewritten class records its call count, for the report. */
    public static final String REPORT_PREFIX = "gtnhdumper.renderClock.";

    private static final String CLOCK = "com/iouter/gtnhdumper/common/utils/IconRenderClock";
    private static final String COMPUTER_PARTS = "pl.asie.computronics.item.ItemOpenComputers";

    /**
     * Fields renderers keep time or animation state in, as {@code owner.name}, with the {@code IconRenderClock} method
     * every read passes through. The classes that read them are listed in {@link #CLOCK_CLASSES}.
     */
    private static final Map<String, String[]> FIELDS = new HashMap<>();
    static {
        // The familiar's age counts client ticks.
        field(
            "makeo/gadomancy/client/util/FamiliarHandlerClient$PartialEntityFamiliar.ticksExisted",
            "frozenTicks",
            "I");
        // The infusion claw moves its arms by the ticks since it was last drawn.
        field("makeo/gadomancy/common/blocks/tiles/TileInfusionClaw.lastRenderTick", "frozenAge", "F");
        // Which block a phased facade shows, and which texture the Infinity Chest wears, advance while the game runs.
        field("buildcraft/transport/render/FacadeItemRenderer.renderState", "frozenState", "I");
        field("wanion/lib/client/animation/Animation.currentFrame", "frozenState", "I");
    }

    private static void field(String name, String method, String type) {
        FIELDS.put(name, new String[] { method, "(" + type + ")" + type });
    }

    /** Classes whose real-time reads ({@code System} and LWJGL {@code Sys}) follow the frozen clock. */
    static final Set<String> CLOCK_CLASSES = new HashSet<>(
        Arrays.asList(
            // Spinning Galacticraft, GalaxySpace and Amun-Ra items.
            "micdoodle8.mods.galacticraft.core.client.render.item.ItemRendererKey",
            "micdoodle8.mods.galacticraft.core.client.render.item.ItemRendererTier1Rocket",
            "micdoodle8.mods.galacticraft.planets.mars.client.render.item.ItemRendererTier2Rocket",
            "micdoodle8.mods.galacticraft.planets.asteroids.client.render.item.ItemRendererTier3Rocket",
            "micdoodle8.mods.galacticraft.planets.asteroids.client.render.item.ItemRendererAstroMiner",
            "micdoodle8.mods.galacticraft.planets.asteroids.client.render.item.ItemRendererBeamReceiver",
            "micdoodle8.mods.galacticraft.planets.asteroids.client.render.item.ItemRendererBeamReflector",
            "micdoodle8.mods.galacticraft.planets.asteroids.client.render.item.ItemRendererGrappleHook",
            "galaxyspace.core.render.item.ItemRendererRocket",
            "de.katzenpapst.amunra.client.renderer.item.ItemRendererShuttle",
            // Item colours and icons that cycle with the time of day on the player's computer.
            "thaumcraft.common.items.ItemCrystalEssence",
            "thaumcraft.common.items.ItemManaBean",
            "thaumcraft.common.items.ItemWispEssence",
            "pl.asie.computronics.item.ItemOpenComputers",
            "vazkii.botania.common.item.equipment.tool.ItemEnderDagger",
            "vazkii.botania.common.item.equipment.tool.terrasteel.ItemTerraPick",
            "tuhljin.automagy.items.ItemGlyph",
            "magicbees.bees.BeeIconColourProvider$1",
            "fox.spiteful.avaritia.compat.forestry.GreedyBeeSpecies",
            "fox.spiteful.avaritia.compat.forestry.ItemComb",
            // Renderers.
            "appeng.client.render.blocks.RenderBlockSkyCompass",
            "buildcraft.transport.render.FacadeItemRenderer",
            "com.gtnewhorizon.gtnhlib.client.renderer.postprocessing.shaders.UniversiumShader",
            "thaumcraft.client.renderers.tile.ItemNodeRenderer",
            "com.kentington.thaumichorizons.client.renderer.tile.ItemJarTHRenderer",
            "makeo.gadomancy.client.renderers.item.ItemNodeRenderer",
            "makeo.gadomancy.client.renderers.item.ItemRenderFamiliar",
            "makeo.gadomancy.client.renderers.tile.RenderTileInfusionClaw",
            "wanion.lib.client.animation.Animation",
            // Counts the server's ticks to turn the item above an obelisk.
            "crazypants.enderio.machine.obelisk.ObeliskSpecialRenderer",
            "makeo.gadomancy.client.renderers.tile.RenderTileNodeBasic",
            "forestry.apiculture.render.RenderBeeItem",
            "forestry.lepidopterology.render.RenderButterflyItem",
            "com.brandon3055.draconicevolution.client.render.block.RenderEarthItem",
            "witchinggadgets.client.render.ItemRenderPrimordialGauntlet",
            "gregtech.common.render.items.GlitchEffectRenderer",
            "gregtech.common.render.items.GlitchEffectMetaItemRenderer",
            // Recipe and research pages that cycle through examples, and a focus whose cost is rolled from the time.
            "makeo.gadomancy.common.crafting.InfusionVisualDisguiseArmor",
            "makeo.gadomancy.common.crafting.RecipeVisualStickyJar",
            "makeo.gadomancy.common.research.AlternatingResearchItem",
            "makeo.gadomancy.common.research.MultiResearchPage$OverrideList",
            "thaumcraft.common.items.wands.foci.ItemFocusPrimal"));

    /**
     * Classes whose random values are fixed: effect offsets (by animation frame) and values that are rolled anew on
     * every
     * read. Their clock reads stay real where they only decide when to roll again, so that they do roll, now fixed.
     */
    static final Set<String> RANDOM_CLASSES = new HashSet<>(
        Arrays.asList(
            "gregtech.common.render.items.GlitchEffectRenderer",
            "gregtech.common.render.items.GlitchEffectMetaItemRenderer",
            // Tainted coal burns for a random time; the essentia compressor's recipe cost and the QED's name change.
            "fox.spiteful.forbidden.ForbiddenRecipes$1",
            "makeo.gadomancy.common.utils.RandomizedAspectList",
            "com.rwtema.extrautils.item.ItemBlockQED",
            // The oven glove picks one of several joke names every second.
            "com.dreammaster.item.baubles.OvenGlove",
            // A screaming enderman shakes; the top buffer core's colour is rolled on every read.
            "net.minecraft.client.renderer.entity.RenderEnderman",
            "gtPlusPlus.core.item.general.ItemBufferCore"));

    /** Classes whose icon is a random mix: they draw from a stream that restarts before each render. */
    static final Set<String> SEQUENCE_CLASSES = new HashSet<>(
        Arrays.asList("twilightforest.block.BlockTFTowerTranslucent"));

    /**
     * Classes that lay out a model with {@code new Random()} when the game starts, before any export: always seeded.
     */
    static final Set<String> SEEDED_CLASSES = new HashSet<>(
        Arrays.asList("twilightforest.client.model.ModelTFMosquitoSwarm"));

    /** Classes that pick an example by iterating a registry in hash order. */
    static final Set<String> SORTED_SET_CLASSES = new HashSet<>(
        Arrays.asList("buildcraft.transport.recipes.GateExpansionRecipe"));

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !isListed(transformedName)) return basicClass;
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        int rewritten = rewrite(transformedName, node);
        System.setProperty(REPORT_PREFIX + transformedName, Integer.toString(rewritten));
        if (rewritten == 0) {
            // The class changed in another version of its mod: its icons may differ between exports again.
            LogManager.getLogger("gtnhdumper")
                .warn("Nothing to rewrite in {}", transformedName);
            return basicClass;
        }
        // Frames stay valid: the stack and locals between the original instructions are unchanged. Seeding raises
        // maxStack itself.
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isListed(String className) {
        return CLOCK_CLASSES.contains(className) || RANDOM_CLASSES.contains(className)
            || SORTED_SET_CLASSES.contains(className)
            || SEQUENCE_CLASSES.contains(className)
            || SEEDED_CLASSES.contains(className);
    }

    /**
     * @param className the deobfuscated name; the node's own name may still be obfuscated.
     * @return the number of rewritten instructions.
     */
    public static int rewrite(String className, ClassNode node) {
        boolean clock = CLOCK_CLASSES.contains(className);
        boolean random = RANDOM_CLASSES.contains(className);
        boolean sortedSets = SORTED_SET_CLASSES.contains(className);
        boolean sequence = SEQUENCE_CLASSES.contains(className);
        boolean seeded = SEEDED_CLASSES.contains(className);
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode call) {
                    if (clock && redirectClock(call) || random && redirectRandom(call)
                        || sequence && redirectSequence(call)
                        || className.equals(COMPUTER_PARTS) && redirectIdentityHash(call)) {
                        rewritten++;
                    } else if (seeded && isUnseededRandom(call)) {
                        method.instructions.insertBefore(insn, new LdcInsnNode(0L));
                        call.desc = "(J)V";
                        method.maxStack += 2;
                        rewritten++;
                    } else if (clock && isServerTickCount(call)) {
                        insn = passThrough(method, insn, "frozenLong", "(J)J");
                        rewritten++;
                    } else if (sortedSets && isBiMapView(call)) {
                        MethodInsnNode sort = new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/iouter/gtnhdumper/common/utils/StableOrder",
                            "stableSet",
                            "(Ljava/util/Set;)Ljava/util/Set;",
                            false);
                        method.instructions.insert(insn, sort);
                        insn = sort;
                        rewritten++;
                    }
                } else if (clock && insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD) {
                    String[] replacement = FIELDS.get(field.owner + "." + field.name);
                    if (replacement != null) {
                        insn = passThrough(method, insn, replacement[0], replacement[1]);
                        rewritten++;
                    }
                }
            }
        }
        return rewritten;
    }

    /** Hands the value on top of the stack to a static {@code IconRenderClock} method that returns the same type. */
    private static AbstractInsnNode passThrough(MethodNode method, AbstractInsnNode after, String name, String desc) {
        MethodInsnNode call = new MethodInsnNode(Opcodes.INVOKESTATIC, CLOCK, name, desc, false);
        method.instructions.insert(after, call);
        return call;
    }

    private static boolean redirectClock(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKESTATIC || !call.desc.equals("()J")) return false;
        String target = null;
        if (call.owner.equals("java/lang/System")
            && (call.name.equals("currentTimeMillis") || call.name.equals("nanoTime"))) target = call.name;
        // lwjgl3ify renames LWJGL 2 references, before or after this transformer.
        else if ((call.owner.equals("org/lwjgl/Sys") || call.owner.equals("org/lwjglx/Sys"))
            && call.name.equals("getTime")) target = "lwjglTime";
        if (target == null) return false;
        call.owner = CLOCK;
        call.name = target;
        return true;
    }

    private static boolean redirectRandom(MethodInsnNode call) {
        // GT++'s own helper for a number in a range.
        if (call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals("gtPlusPlus/core/util/math/MathUtils")
            && call.name.equals("randInt")
            && call.desc.equals("(II)I")) {
            call.owner = CLOCK;
            call.name = "fixedRange";
            return true;
        }
        if (!call.owner.equals("java/util/Random") || call.getOpcode() != Opcodes.INVOKEVIRTUAL) return false;
        String target = null;
        if (call.name.equals("nextGaussian") && call.desc.equals("()D")) target = "pulseGaussian";
        else if (call.name.equals("nextDouble") && call.desc.equals("()D")) target = "fixedDouble";
        else if (call.name.equals("nextInt") && call.desc.equals("(I)I")) target = "fixedInt";
        if (target == null) return false;
        toStatic(call, target, "java/util/Random");
        return true;
    }

    private static boolean redirectSequence(MethodInsnNode call) {
        if (!call.owner.equals("java/util/Random") || call.getOpcode() != Opcodes.INVOKEVIRTUAL) return false;
        String target = null;
        if (call.name.equals("nextBoolean") && call.desc.equals("()Z")) target = "sequenceBoolean";
        else if (call.name.equals("nextFloat") && call.desc.equals("()F")) target = "sequenceFloat";
        if (target == null) return false;
        toStatic(call, target, "java/util/Random");
        return true;
    }

    /** A computer part's colour mixes in the stack's identity hash, which differs in every run. */
    private static boolean redirectIdentityHash(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKEVIRTUAL || !call.owner.equals("java/lang/Object")
            || !call.name.equals("hashCode")
            || !call.desc.equals("()I")) return false;
        toStatic(call, "identityHash", "java/lang/Object");
        return true;
    }

    /** Turns an instance call into a static clock method: the receiver on the stack becomes the first argument. */
    private static void toStatic(MethodInsnNode call, String name, String receiver) {
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = CLOCK;
        call.name = name;
        call.desc = "(L" + receiver + ";" + call.desc.substring(1);
    }

    private static boolean isUnseededRandom(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKESPECIAL && call.owner.equals("java/util/Random")
            && call.name.equals("<init>")
            && call.desc.equals("()V");
    }

    private static boolean isServerTickCount(MethodInsnNode call) {
        return call.owner.equals("crazypants/enderio/CommonProxy") && call.name.equals("getTickCount")
            && call.desc.equals("()J");
    }

    private static boolean isBiMapView(MethodInsnNode call) {
        return call.owner.equals("com/google/common/collect/BiMap") && call.desc.equals("()Ljava/util/Set;")
            && (call.name.equals("values") || call.name.equals("keySet"));
    }
}
