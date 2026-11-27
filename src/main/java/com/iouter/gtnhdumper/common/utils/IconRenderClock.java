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
 * drawn textures, so that frame differs from run to run). Classes that read {@code System} or LWJGL time directly are
 * redirected here by {@code DeterminismTransformer}; the shared random generators are reseeded before each render.
 * Whatever still depends on time or chance shows up by comparing pixel hashes of repeated exports.
 */
public final class IconRenderClock {

    public static final long SYSTEM_TIME = 3_600_000L;
    private static final long WORLD_TIME = 6000L;

    private static volatile boolean frozen;
    /** Time and shared randomness are pinned for data exports too, without touching the world or textures. */
    private static volatile boolean timeFrozen;
    /** The player's ground contact before a data task pinned it, or null. */
    private static Boolean savedOnGround;
    private static long savedWorldTime;
    private static long savedTotalWorldTime;
    private static float savedPartialTicks;
    private static int savedPlayerTicks;
    private static boolean worldSaved;
    private static String failure;
    private static int resetSprites;
    private static int uncachedModelRenders;
    /** Pulse sizes by animation frame, as standard normal samples. Frame 0, also the still icon, is the mean. */
    private static final double[] PULSES = pulses();
    private static int effectFrame;
    private static int fixedPulses;
    /** Milliseconds added to the frozen clock while sampling an animation that is driven by wall-clock time. */
    private static volatile long clockOffset;
    private static boolean starfieldDrawn;
    private static java.util.Random mathRandom;
    private static final java.util.Random SEQUENCE = new java.util.Random(SYSTEM_TIME);
    private static java.util.SplittableRandom glitchRandom = new java.util.SplittableRandom(SYSTEM_TIME);
    private static String mathRandomState = "unused";
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
        { "thaumic.tinkerer.client.core.handler.ClientTickHandler", "elapsedTicks", 6000 },
        // CodeChickenCore's render time turns NEI's spawner mobs, wireless trackers and ender storage models.
        { "codechicken.core.internal.CCCEventHandler", "renderTime", 6000 },
        { "codechicken.core.internal.CCCEventHandler", "renderFrame", 0f },
        // The overlay on condensate fluid displays counts client ticks.
        { "gregtech.client.handler.CondensateAnimationTickHandler", "currentFrame", 0 }, };
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

    /** True while exporting: redirected clock and random reads return fixed values. */
    public static boolean isTimeFrozen() {
        return frozen || timeFrozen;
    }

    /** Pins only the clocks and redirected random reads, for tasks that export data. */
    public static void freezeTime() {
        timeFrozen = true;
        // Some tooltips change while the player is in the air, such as the slime sling's.
        net.minecraft.client.entity.EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
        if (player != null && savedOnGround == null) {
            savedOnGround = player.onGround;
            player.onGround = true;
        }
    }

    public static void releaseTime() {
        timeFrozen = false;
        net.minecraft.client.entity.EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
        if (player != null && savedOnGround != null) player.onGround = savedOnGround;
        savedOnGround = null;
    }

    public static synchronized void freeze() {
        if (frozen) return;
        failure = null;
        uncachedModelRenders = 0;
        fixedPulses = 0;
        effectFrame = 0;
        clockOffset = 0;
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

    /**
     * Moves GT's frozen animation counter, which spinning and colour-cycling item renderers read, so their frames can
     * be stepped through. {@link #restoreGtAnimationTick} puts the frozen value back.
     */
    public static synchronized void setGtAnimationTick(double tick) {
        long whole = (long) Math.floor(tick);
        setGtCounters(whole, (float) (tick - whole));
    }

    public static synchronized void restoreGtAnimationTick() {
        setGtCounters(WORLD_TIME, 0f);
    }

    private static void setGtCounters(long tick, float partial) {
        int found = 0;
        for (Object[] saved : savedCounters) {
            java.lang.reflect.Field field = (java.lang.reflect.Field) saved[0];
            if (!field.getDeclaringClass()
                .getName()
                .equals("gregtech.common.GTClient")) continue;
            try {
                if (field.getName()
                    .equals("mAnimationTick")) field.setLong(saved[1], tick);
                else field.setFloat(saved[1], partial);
                found++;
            } catch (IllegalAccessException error) {
                throw new IllegalStateException(error);
            }
        }
        if (found != 2) throw new IllegalStateException("GT animation counters are not frozen: " + counterFailures);
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

    /** The animation frame being rendered, for effects that would otherwise draw a new random value each time. */
    public static void setEffectFrame(int frame) {
        effectFrame = frame;
    }

    /** {@code Random.nextGaussian()} for redirected pulse effects: fixed per animation frame while exporting icons. */
    public static double pulseGaussian(java.util.Random random) {
        if (!isTimeFrozen()) return random.nextGaussian();
        fixedPulses++;
        return PULSES[Math.floorMod(effectFrame, PULSES.length)];
    }

    private static double[] pulses() {
        double[] pulses = new double[4096];
        // One stream: the first values of consecutively seeded generators are strongly correlated.
        java.util.Random random = new java.util.Random(SYSTEM_TIME);
        for (int i = 1; i < pulses.length; i++) pulses[i] = random.nextGaussian();
        return pulses;
    }

    /**
     * True while exporting icons, so a mod's order-dependent inventory model cache is not used; counted for the report.
     */
    public static boolean bypassInventoryModelCache() {
        if (!frozen) return false;
        uncachedModelRenders++;
        return true;
    }

    /**
     * Called by {@code UniversiumShader.use()}: the star field drifts with the wall clock, so an icon drawn with it is
     * an animation although none of its textures is.
     */
    public static void starfieldDrawn() {
        starfieldDrawn = true;
    }

    /** Whether the star field was drawn since the last call. */
    public static boolean takeStarfieldDrawn() {
        boolean drawn = starfieldDrawn;
        starfieldDrawn = false;
        return drawn;
    }

    /** The frozen clock in milliseconds, including the offset of the frame being sampled. */
    public static long frozenMillis() {
        return SYSTEM_TIME + clockOffset;
    }

    /** Advances the frozen clock to sample an animation driven by wall-clock time; 0 returns to the frozen instant. */
    public static void setClockOffset(long millis) {
        clockOffset = millis;
    }

    /** {@code System.currentTimeMillis()} for redirected item renderers. */
    public static long currentTimeMillis() {
        return isTimeFrozen() ? frozenMillis() : System.currentTimeMillis();
    }

    /** {@code System.nanoTime()} for redirected item renderers. */
    public static long nanoTime() {
        return isTimeFrozen() ? frozenMillis() * 1_000_000L : System.nanoTime();
    }

    /** LWJGL's {@code Sys.getTime()}, whose timer counts milliseconds. */
    public static long lwjglTime() {
        return isTimeFrozen() ? frozenMillis() : org.lwjgl.Sys.getTime();
    }

    /** {@code Random.nextDouble()} for redirected classes: the middle of its range while exporting. */
    public static double fixedDouble(java.util.Random random) {
        return isTimeFrozen() ? 0.5 : random.nextDouble();
    }

    /** {@code Random.nextInt(bound)} for redirected classes: the middle of its range while exporting. */
    public static int fixedInt(java.util.Random random, int bound) {
        return isTimeFrozen() ? bound / 2 : random.nextInt(bound);
    }

    /** A tick counter read from a mod's own object. */
    public static int frozenTicks(int ticks) {
        return isTimeFrozen() ? (int) WORLD_TIME : ticks;
    }

    /** As {@link #frozenTicks}, for a counter kept as a {@code long}. */
    public static long frozenLong(long ticks) {
        return isTimeFrozen() ? WORLD_TIME : ticks;
    }

    /** The moment a renderer last drew something: now, so that no time has passed since. */
    public static float frozenAge(float lastTick) {
        return isTimeFrozen() ? WORLD_TIME : lastTick;
    }

    /** A renderer's current step in a slideshow of its own: the first one. */
    public static int frozenState(int state) {
        return isTimeFrozen() ? 0 : state;
    }

    /** {@code Object.hashCode()} used to vary a colour between stacks. */
    public static int identityHash(Object value) {
        return isTimeFrozen() ? 0 : value.hashCode();
    }

    /** A random number from {@code min} to {@code max}: the middle one while exporting. */
    public static int fixedRange(int min, int max) {
        return isTimeFrozen() ? (min + max) / 2
            : java.util.concurrent.ThreadLocalRandom.current()
                .nextInt(min, max + 1);
    }

    /** {@code Random.nextBoolean()} for icons that mix at random: the same mix in every render while exporting. */
    public static boolean sequenceBoolean(java.util.Random random) {
        return isTimeFrozen() ? SEQUENCE.nextBoolean() : random.nextBoolean();
    }

    /** As {@link #sequenceBoolean}. */
    public static float sequenceFloat(java.util.Random random) {
        return isTimeFrozen() ? SEQUENCE.nextFloat() : random.nextFloat();
    }

    /** {@code Random.nextDouble()} for the glitch renderers: a fixed value for each draw of each animation frame. */
    public static double glitchDouble(java.util.Random random) {
        return isTimeFrozen() ? glitchRandom.nextDouble() : random.nextDouble();
    }

    /** As {@link #glitchDouble}, for the draws that only decide a direction by their sign. */
    public static double glitchGaussian(java.util.Random random) {
        return isTimeFrozen() ? glitchRandom.nextDouble() - 0.5 : random.nextGaussian();
    }

    /**
     * Restarts the generators renderers share, so what a render draws from them does not depend on how much earlier
     * renders consumed: the client world's, and the one behind {@code Math.random()}, from which new entities take
     * their facing and dropped items their bobbing phase.
     */
    public static void reseedSharedRandom() {
        if (isTimeFrozen()) reseedSharedRandom(WORLD_TIME * 31 + effectFrame);
    }

    /** As {@link #reseedSharedRandom()}, with a caller's seed, e.g. before building one particular entity. */
    public static void reseedSharedRandom(long seed) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld != null) minecraft.theWorld.rand.setSeed(seed);
        SEQUENCE.setSeed(seed);
        // Consecutive frame seeds must give unrelated values, which java.util.Random's first draws do not.
        glitchRandom = new java.util.SplittableRandom(seed);
        if (mathRandom == null) {
            if (!"unused".equals(mathRandomState)) return;
            try {
                java.lang.reflect.Field field = Class.forName("java.lang.Math$RandomNumberGeneratorHolder")
                    .getDeclaredField("randomNumberGenerator");
                field.setAccessible(true);
                mathRandom = (java.util.Random) field.get(null);
                mathRandomState = "seeded";
            } catch (ReflectiveOperationException | RuntimeException error) {
                mathRandomState = error.toString();
                return;
            }
        }
        mathRandom.setSeed(seed);
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
        report.put("uncachedGtModelRenders", uncachedModelRenders);
        report.put("fixedRandomPulses", fixedPulses);
        report.put("mathRandom", mathRandomState);
        // Classes the clock transformer rewrote, with the number of call sites; classes never loaded are absent.
        Map<String, String> redirected = new java.util.TreeMap<>();
        for (String property : System.getProperties()
            .stringPropertyNames()) {
            String prefix = com.iouter.gtnhdumper.DeterminismTransformer.REPORT_PREFIX;
            if (property.startsWith(prefix))
                redirected.put(property.substring(prefix.length()), System.getProperty(property));
        }
        report.put("redirectedClockClasses", redirected);
        report.put("failure", failure);
        return report;
    }

    private static Timer timer(Minecraft minecraft) {
        return ReflectionHelper.getPrivateValue(Minecraft.class, minecraft, "timer", "field_71428_T");
    }
}
