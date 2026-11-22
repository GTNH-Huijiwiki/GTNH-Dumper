package com.iouter.gtnhdumper.common.utils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.iouter.gtnhdumper.GTNHDumper;
import com.iouter.gtnhdumper.Tags;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Two images per living entity, in {@code entity-images/}, listed in {@code entity-images.json}:
 * <ul>
 * <li>{@code Entity <id>.png}: full body, 1024×1024, three-quarter view from slightly above, standard item
 * lighting, rendered at 2048 and box-filtered down.</li>
 * <li>{@code EntityHead <id>.png}: pixel head icon, flat front orthographic view without lighting, only the model
 * parts that make up the head (and everything attached to them), at {@link #HEAD_PIXELS} pixels per model unit. When
 * no head part can be identified the whole entity is drawn the same way ({@code headSource: "body"}).</li>
 * </ul>
 * The pose is fixed: body, head and pitch face straight ahead, no limb swing, no hurt or death animation, ticks and
 * clocks frozen. MobsInfo's recipe entity is used when there is one (it carries MobsInfo's equipment, as its NEI page
 * shows it); NBT variants ({@link EntityExport.Variant}) are drawn from that entity too.
 */
public final class EntityImages {

    private static final Logger LOG = LogManager.getLogger(Tags.MODID);

    static final int FULL_SIZE = 1024;
    static final int HEAD_PIXELS = Integer.getInteger("gtnhdumper.entityHeadPixels", 4);
    private static final int HEAD_MAX = 256;
    private static final float FULL_PITCH = 20f;
    private static final float FULL_YAW = -35f;

    /** Vanilla head parts by SRG name; mod models keep their source field names and are matched by token. */
    private static final Set<String> VANILLA_HEADS = new HashSet<>(
        Arrays.asList(
            "field_110699_Q",
            "field_110700_P",
            "field_110703_f",
            "field_110704_g",
            "field_110705_d",
            "field_110706_e",
            "field_110707_b",
            "field_110708_c",
            "field_110709_a",
            "field_110717_i",
            "field_78105_b",
            "field_78114_d",
            "field_78116_c",
            "field_78135_a",
            "field_78137_g",
            "field_78142_a",
            "field_78143_h",
            "field_78150_a",
            "field_78156_g",
            "field_78178_a",
            "field_78185_a",
            "field_78191_a",
            "field_78195_c",
            "field_78209_a",
            "field_78220_c",
            "field_78221_a",
            "field_82895_a",
            "field_82896_a",
            "field_82898_f",
            "field_82902_i",
            "field_82904_b",
            "field_82901_h"));
    private static final Set<String> HEAD_TOKENS = new HashSet<>(
        Arrays.asList(
            "head",
            "heads",
            "hat",
            "helmet",
            "skull",
            "face",
            "ear",
            "ears",
            "nose",
            "snout",
            "beak",
            "bill",
            "jaw",
            "mouth",
            "horn",
            "horns",
            "chin",
            "muzzle",
            "tusk",
            "tusks",
            "brow",
            // Galacticraft's evolved mobs wear a glass oxygen mask over the head.
            "mask",
            // Thaumcraft's cultist cleric wears a hood.
            "hood",
            "visor"));

    private static Field compiled;
    private static Field displayList;

    private EntityImages() {}

    /** File-name form of an entity id: characters MediaWiki titles cannot hold become spaces or underscores. */
    public static String fileKey(String id) {
        return id.replace('#', ' ')
            .replaceAll("[<>\\[\\]|{}/:]", "_");
    }

    public static void write(File output) throws Exception {
        File directory = new File(output, "entity-images");
        if (directory.isDirectory()) for (File old : directory.listFiles()) Files.delete(old.toPath());
        Files.createDirectories(directory.toPath());
        compiled = ReflectionHelper.findField(ModelRenderer.class, "compiled", "field_78812_q");
        displayList = ReflectionHelper.findField(ModelRenderer.class, "displayList", "field_78811_r");

        Map<String, Object> recipes = mobsInfoRecipes();
        Map<String, Object> images = new TreeMap<>();
        Map<String, String> failures = new TreeMap<>();
        Map<String, String> skipped = new TreeMap<>();
        List<String[]> targets = new ArrayList<>();
        for (Object entry : EntityList.stringToClassMapping.entrySet()) {
            Map.Entry<?, ?> pair = (Map.Entry<?, ?>) entry;
            if (EntityLivingBase.class.isAssignableFrom((Class<?>) pair.getValue()))
                targets.add(new String[] { String.valueOf(pair.getKey()), null });
        }
        Map<String, EntityExport.Variant> variants = new LinkedHashMap<>();
        for (EntityExport.Variant variant : EntityExport.variants()) {
            variants.put(variant.id, variant);
            targets.add(new String[] { variant.id, variant.baseId });
        }
        targets.sort((a, b) -> a[0].compareTo(b[0]));

        IconRenderClock.freeze();
        EntitySeeds.begin();
        // Mobs that borrow the viewer's skin (Twilight Forest giants; SimpleSkinBackport's giantSkinSet=NONE copies
        // the player's skin and arm width) must not show whoever runs the export: the player wears Steve with wide
        // arms meanwhile, and the giants take SimpleSkinBackport's own Steve (STEVE_ONLY), whose 64x64 layout their
        // model expects; vanilla's 64x32 steve.png would be mapped wrongly. A null skin would not do either:
        // SimpleSkinBackport then picks one of its default skins by UUID.
        net.minecraft.client.entity.AbstractClientPlayer player = Minecraft.getMinecraft().thePlayer;
        Field skin = ReflectionHelper
            .findField(net.minecraft.client.entity.AbstractClientPlayer.class, "locationSkin", "field_110312_d");
        Object ownSkin = skin.get(player);
        Object steve = net.minecraft.client.entity.AbstractClientPlayer.locationStevePng;
        Field giantSet = null;
        Object ownGiantSet = null;
        if (Loader.isModLoaded("simpleskinbackport")) {
            try {
                Object skinDef = Class.forName("roadhog360.simpleskinbackport.core.DefaultPlayerSkin")
                    .getField("STEVE")
                    .get(null);
                steve = skinDef.getClass()
                    .getMethod("getResource")
                    .invoke(skinDef);
                Class<?> sets = Class.forName("roadhog360.simpleskinbackport.core.DefaultPlayerSkin$Set");
                giantSet = Class.forName("roadhog360.simpleskinbackport.configuration.configs.ConfigModCompat")
                    .getField("TFgiantSkinSet");
                ownGiantSet = giantSet.get(null);
                giantSet.set(
                    null,
                    sets.getField("STEVE_ONLY")
                        .get(null));
            } catch (Exception | LinkageError e) {
                LOG.warn("SimpleSkinBackport Steve skin unavailable", e);
            }
        }
        skin.set(player, steve);
        Boolean ownSlim = slim(player, null);
        if (ownSlim != null) slim(player, false);
        // Same content-addressed PNG cache as the item icons: unchanged pixels are hard-linked, not encoded again,
        // so the files keep their modification time and the toolchain's hash cache hits on re-runs.
        File cache = Boolean.parseBoolean(System.getProperty("gtnhdumper.pngCache", "true"))
            ? new File(Minecraft.getMinecraft().mcDataDir, "dumper-cache/png")
            : null;
        PngWriter writer = new PngWriter(PngWriter.defaultWorkers(), cache);
        FBOHelper.setPngWriter(writer);
        try {
            for (String[] target : targets) {
                String id = target[0];
                if (SKIP.containsKey(id)) {
                    skipped.put(id, SKIP.get(id));
                    continue;
                }
                try {
                    EntityLivingBase entity = entity(id, target[1], variants.get(id), recipes);
                    if (entity == null) {
                        failures.put(id, "no instance");
                        continue;
                    }
                    Render render = RenderManager.instance.getEntityRenderObject(entity);
                    if (render == null) {
                        failures.put(id, "no renderer");
                        continue;
                    }
                    if (variants.get(id) == null) equip(entity);
                    Map<String, Object> row = new LinkedHashMap<>();
                    String key = fileKey(id);
                    BufferedImage full = SKIP_FULL.containsKey(id) ? null : full(entity);
                    if (SKIP_FULL.containsKey(id)) row.put("fullSkipped", SKIP_FULL.get(id));
                    if (full != null) {
                        String name = "Entity " + key + ".png";
                        FBOHelper.saveToFile(new File(directory, name), full);
                        row.put("full", name);
                    }
                    currentId = id;
                    helmetModels = helmetModels(entity);
                    Set<ModelRenderer> head = headParts(render);
                    BufferedImage icon = head.isEmpty() ? null : head(entity, render, head);
                    row.put("headSource", icon != null ? "model" : "body");
                    if (icon == null) icon = head(entity, render, null);
                    if (icon != null) {
                        String name = "EntityHead " + key + ".png";
                        FBOHelper.saveToFile(new File(directory, name), icon);
                        row.put("head", name);
                        row.put("headView", lastIconView);
                        row.put("headPixelsPerUnit", HEAD_PIXELS);
                    }
                    if (row.containsKey("full") || row.containsKey("head")) images.put(id, row);
                    else failures.put(id, "nothing drawn");
                } catch (Throwable e) {
                    // One mod's renderer failing is recorded in "failures" (as the item icons record theirs in
                    // renderFailures); an ERROR here would mark the whole export task failed.
                    LOG.warn("Entity image failed: " + id, e);
                    failures.put(id, e.toString());
                }
            }
        } finally {
            try {
                writer.close();
            } finally {
                FBOHelper.setPngWriter(null);
            }
            skin.set(player, ownSkin);
            if (giantSet != null) giantSet.set(null, ownGiantSet);
            if (ownSlim != null) slim(player, ownSlim);
            EntitySeeds.end();
            IconRenderClock.release();
        }
        Map<String, String> hashes = writer.pixelHashes();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("images", images);
        // Bosses whose head is a separate entity take that entity's head icon.
        for (Map.Entry<String, String> entry : ICON_FROM.entrySet()) {
            Object target = images.get(entry.getKey()), source = images.get(entry.getValue());
            if (!(target instanceof Map<?, ?>) || !(source instanceof Map<?, ?>)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> to = (Map<String, Object>) target;
            Map<?, ?> from = (Map<?, ?>) source;
            if (!(from.get("head") instanceof String head)) continue;
            String name = "EntityHead " + fileKey(entry.getKey()) + ".png";
            Files.copy(
                new File(directory, head).toPath(),
                new File(directory, name).toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            to.put("head", name);
            if (hashes.containsKey(head)) hashes.put(name, hashes.get(head));
            to.put("headSource", "entity:" + entry.getValue());
            to.put("headView", from.get("headView"));
        }
        root.put("failures", failures);
        root.put("skipped", skipped);
        // Pixel hash per file (sha1 of "width:height:" and ARGB rows), as icon-hashes.json for the item icons.
        root.put("pixelHashes", new TreeMap<>(hashes));
        root.put("pngCacheHits", writer.cacheHits());
        Files.write(
            new File(output, "entity-images.json").toPath(),
            GTNHDumper.GSON.toJson(root)
                .getBytes(StandardCharsets.UTF_8));
        GTNHDumper.info("已写入实体图片：" + images.size() + " 个实体，" + failures.size() + " 个失败");
    }

    /**
     * SimpleSkinBackport's slim-arm state on the player: read it ({@code value} null) or set it; null when the mod is
     * absent.
     */
    private static Boolean slim(Object player, Boolean value) {
        try {
            if (value == null) return (Boolean) player.getClass()
                .getMethod("ssb$isSlim")
                .invoke(player);
            player.getClass()
                .getMethod("ssb$setSlim", boolean.class)
                .invoke(player, value);
            return value;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Map<String, Object> mobsInfoRecipes() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!Loader.isModLoaded("mobsinfo")) return out;
        try {
            Map<?, ?> map = (Map<?, ?>) Class.forName("com.kuba6000.mobsinfo.api.MobRecipe")
                .getField("MobNameToRecipeMap")
                .get(null);
            for (Map.Entry<?, ?> entry : map.entrySet()) out.put(String.valueOf(entry.getKey()), entry.getValue());
        } catch (Exception | LinkageError e) {
            LOG.error("MobsInfo recipes unavailable for entity images", e);
        }
        return out;
    }

    /**
     * A new instance, built inside the class-seeded random scope ({@link EntitySeeds}) so every export starts from
     * the same state; variants get their NBT on a new base entity. MobsInfo's instances are not used: they were made
     * at start-up with unseeded random state (Special Mobs' looks, an enderman's anger) and differ per launch.
     */
    private static EntityLivingBase entity(String id, String baseId, EntityExport.Variant variant,
        Map<String, Object> recipes) throws Exception {
        World world = Minecraft.getMinecraft().theWorld;
        Entity entity = EntityList.createEntityByName(variant != null ? variant.baseId : id, world);
        if (variant != null && entity != null) variant.apply(entity);
        return entity instanceof EntityLivingBase living ? living : null;
    }

    /**
     * Separate entities that make up a boss, posed around it for the full-body image (the main entity already in its
     * export pose):
     * <ul>
     * <li>Twilight Forest Naga: its body segments, 2 blocks apart as the game keeps them, trailing behind the head in
     * a gentle S on the ground, each turned towards the one before.</li>
     * <li>Twilight Forest Hydra: its three starting heads with their necks, placed by the game's own
     * {@code HydraHeadContainer} in the idle state.</li>
     * </ul>
     */
    private static List<Entity> parts(EntityLivingBase entity) {
        List<Entity> out = new ArrayList<>();
        String cls = entity.getClass()
            .getName();
        try {
            if (cls.equals("twilightforest.entity.boss.EntityTFNaga")) {
                Field bodyField = entity.getClass()
                    .getDeclaredField("body");
                bodyField.setAccessible(true);
                Entity[] body = (Entity[]) bodyField.get(entity);
                double px = entity.posX, pz = entity.posZ;
                double t = 0;
                for (int i = 0; body != null && i < body.length; i++) {
                    Entity segment = body[i];
                    if (segment == null) continue;
                    // Walk 2 blocks along x = 2.5 sin(0.35 z'), going away from the camera (-Z).
                    double nx = px, nz = pz;
                    for (int step = 0; step < 400; step++) {
                        t += 0.01;
                        nx = entity.posX + 2.5 * Math.sin(0.35 * t);
                        nz = entity.posZ - t;
                        if (Math.hypot(nx - px, nz - pz) >= 2.0) break;
                    }
                    float yaw = (float) Math.toDegrees(Math.atan2(-(px - nx), pz - nz));
                    segment.worldObj = entity.worldObj;
                    segment.setPosition(nx, entity.posY, nz);
                    segment.lastTickPosX = segment.prevPosX = nx;
                    segment.lastTickPosY = segment.prevPosY = entity.posY;
                    segment.lastTickPosZ = segment.prevPosZ = nz;
                    segment.rotationYaw = segment.prevRotationYaw = yaw;
                    segment.rotationPitch = segment.prevRotationPitch = 0f;
                    out.add(segment);
                    px = nx;
                    pz = nz;
                }
            } else if (cls.equals("twilightforest.entity.boss.EntityTFHydra")) {
                Object[] containers = (Object[]) entity.getClass()
                    .getField("hc")
                    .get(entity);
                Class<?> containerType = Class.forName("twilightforest.entity.boss.HydraHeadContainer");
                Class<?> headType = Class.forName("twilightforest.entity.boss.EntityTFHydraHead");
                for (int i = 0; containers != null && i < Math.min(3, containers.length); i++) {
                    Object container = containers[i];
                    if (container == null) continue;
                    Field headField = containerType.getField("headEntity");
                    if (headField.get(container) == null) headField.set(
                        container,
                        headType.getConstructor(entity.getClass(), String.class, float.class, float.class)
                            .newInstance(entity, "head" + i, 3f, 3f));
                    containerType.getField("currentState")
                        .setInt(container, 0);
                    for (String step : new String[] { "setHeadPosition", "setHeadFacing", "setNeckPosition" }) {
                        java.lang.reflect.Method method = containerType.getDeclaredMethod(step);
                        method.setAccessible(true);
                        method.invoke(container);
                    }
                    for (String name : new String[] { "necka", "neckb", "neckc", "neckd", "necke", "headEntity" }) {
                        Object part = containerType.getField(name)
                            .get(container);
                        if (part instanceof Entity e) {
                            e.worldObj = entity.worldObj;
                            e.lastTickPosX = e.prevPosX = e.posX;
                            e.lastTickPosY = e.prevPosY = e.posY;
                            e.lastTickPosZ = e.prevPosZ = e.posZ;
                            e.prevRotationYaw = e.rotationYaw;
                            e.prevRotationPitch = e.rotationPitch;
                            out.add(e);
                        }
                    }
                }
            }
        } catch (Exception | LinkageError e) {
            LOG.warn("Boss parts unavailable for " + cls, e);
        }
        return out;
    }

    /**
     * The movement ring buffer of a dragon (vanilla {@code ringBuffer}, Hardcore Ender Expansion's
     * {@code movementBuffer}), or null for other entities.
     */
    static double[][] dragonRing(EntityLivingBase entity) {
        for (Class<?> type = entity.getClass(); type != null
            && type != EntityLivingBase.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName();
                if (field.getType() != double[][].class
                    || !(name.equals("ringBuffer") || name.equals("field_70979_e") || name.equals("movementBuffer")))
                    continue;
                try {
                    field.setAccessible(true);
                    return (double[][]) field.get(entity);
                } catch (Exception | LinkageError e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Mobs whose mod gives them their look through equipment (Thaumcraft cultists' robes, the Twilight Forest snow
     * guardian made of armour) get it here: {@code addRandomArmor} is called once, with the class-seeded random, when
     * a mod class overrides it and the entity holds nothing yet. Vanilla's random armour is left out.
     */
    private static void equip(EntityLivingBase entity) {
        if (!(entity instanceof EntityLiving living)) return;
        for (int slot = 0; slot < 5; slot++) if (living.getEquipmentInSlot(slot) != null) return;
        for (Class<?> type = entity.getClass(); type != null
            && type != EntityLiving.class; type = type.getSuperclass()) {
            java.lang.reflect.Method method = null;
            for (String name : new String[] { "addRandomArmor", "func_82164_bB" }) {
                try {
                    method = type.getDeclaredMethod(name);
                    break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (method == null) continue;
            if (type.getName()
                .startsWith("net.minecraft.")) return;
            try {
                method.setAccessible(true);
                method.invoke(entity);
            } catch (Exception | LinkageError e) {
                LOG.warn(
                    "addRandomArmor failed for " + entity.getClass()
                        .getName(),
                    e);
            }
            return;
        }
    }

    /** Straight-ahead pose at the player's position, in the client world, nothing animated. */
    private static World pose(EntityLivingBase entity) {
        Minecraft mc = Minecraft.getMinecraft();
        World previous = entity.worldObj;
        entity.worldObj = mc.theWorld;
        // Renderers draw from the entity's and the world's random (an angry enderman's shaking head, Special Mobs'
        // looks via the world random): reseed both before every render so each export draws the same frame.
        long seed = entity.getClass()
            .getName()
            .hashCode();
        entity.getRNG()
            .setSeed(seed);
        mc.theWorld.rand.setSeed(seed);
        entity.setPosition(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        entity.lastTickPosX = entity.prevPosX = entity.posX;
        entity.lastTickPosY = entity.prevPosY = entity.posY;
        entity.lastTickPosZ = entity.prevPosZ = entity.posZ;
        // Dragon renderers turn the model the other way round (it faces away at yaw 0), and take the yaw from the
        // movement ring buffer, not rotationYaw.
        double[][] ring = dragonRing(entity);
        float yaw = ring != null ? 180f : 0f;
        if (ring != null) for (double[] row : ring) if (row != null && row.length >= 2) {
            row[0] = yaw;
            row[1] = entity.posY;
        }
        // Bats at rest hang upside down with folded wings: show them flying.
        for (String name : new String[] { "setIsBatHanging", "func_82236_f" }) {
            try {
                java.lang.reflect.Method hang = entity.getClass()
                    .getMethod(name, boolean.class);
                hang.invoke(entity, false);
                break;
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        entity.rotationYaw = entity.prevRotationYaw = yaw;
        entity.renderYawOffset = entity.prevRenderYawOffset = yaw;
        entity.rotationYawHead = entity.prevRotationYawHead = yaw;
        entity.rotationPitch = entity.prevRotationPitch = 0f;
        entity.limbSwing = entity.limbSwingAmount = entity.prevLimbSwingAmount = 0f;
        entity.swingProgress = entity.prevSwingProgress = 0f;
        entity.isSwingInProgress = false;
        entity.hurtTime = entity.deathTime = 0;
        entity.ticksExisted = 0;
        entity.onGround = true;
        if (entity instanceof EntityLiving living) living.setCustomNameTag("");
        return previous;
    }

    private static BufferedImage full(EntityLivingBase entity) {
        float extent = 3f * Math.max(1f, Math.max(entity.height, entity.width));
        float[] box = null;
        for (int attempt = 0; attempt < 4 && box == null; attempt++, extent *= 2f) {
            BufferedImage probe = render(entity, null, 256, 0f, entity.height / 2f, extent, true);
            box = bounds(probe, 0f, entity.height / 2f, extent, true);
        }
        if (box == null) return null;
        float side = Math.max(box[2] - box[0], box[3] - box[1]) * 1.06f;
        float cx = (box[0] + box[2]) / 2f, cy = (box[1] + box[3]) / 2f;
        BufferedImage large = render(entity, null, FULL_SIZE * 2, cx, cy, side, true);
        return halve(large);
    }

    /** How the icon was viewed: front, or side when the front view is a thin sliver (horse heads, silverfish). */
    static String lastIconView = "front";
    /** Width of the last icon's drawing in model units (16 per block). */
    private static float lastIconUnits;

    /**
     * Pixel icon of the given head parts (all parts when {@code head} is null), cropped and centred on a square. The
     * front view is used; only a tall sliver (a horse's face seen head-on) is replaced by the side view, when that is
     * squarer.
     */
    private static BufferedImage head(EntityLivingBase entity, Render render, Set<ModelRenderer> head) {
        Float forced = VIEWS.get(currentId);
        if (forced == null && head != null && lastSpecial != null) forced = lastSpecial.side ? 90f : 0f;
        iconPitch = head != null && lastSpecial != null ? lastSpecial.pitch : 0f;
        if (PITCHES.containsKey(currentId)) iconPitch = PITCHES.get(currentId);
        iconLit = head != null && lastSpecial != null && lastSpecial.lit;
        if (forced != null) {
            lastIconView = forced == 90f ? "side" : forced == 0f ? "front" : "turned";
            return icon(entity, head, forced);
        }
        BufferedImage front = icon(entity, head, 0f);
        lastIconView = "front";
        // A narrow face (horse heads are 4-6 units wide; heads are usually 8) drawn as a tall sliver.
        if (front != null && (aspect(front) <= 1.8 || lastIconUnits > 6.5f)) return front;
        BufferedImage side = icon(entity, head, 90f);
        if (side != null && (front == null || aspect(side) < aspect(front))) {
            lastIconView = "side";
            return side;
        }
        return front;
    }

    /**
     * Hand-picked head icons for models the general rules get wrong, keyed by model class name: the fields that make
     * the head, and whether it is drawn from the side.
     */
    private static final class Special {

        final boolean side;
        final Set<String> heads;
        /** Camera tilt in degrees, looking down onto the head (beetle pincers that point straight ahead). */
        float pitch;
        /** Draw the icon with standard item lighting: for bodies drawn in a glow pass that a flat render loses. */
        boolean lit;

        Special(boolean side, String... heads) {
            this.side = side;
            this.heads = new HashSet<>(Arrays.asList(heads));
        }

        Special pitch(float degrees) {
            pitch = degrees;
            return this;
        }

        Special lit() {
            lit = true;
            return this;
        }
    }

    private static final Map<String, Special> SPECIALS = new java.util.HashMap<>();
    static {
        // Horses as on the Minecraft Wiki: the head in profile, without neck and mane (head, mouth, ears).
        SPECIALS.put(
            "net.minecraft.client.model.ModelHorse",
            new Special(
                true,
                "head",
                "mouthTop",
                "mouthBottom",
                "horseLeftEar",
                "horseRightEar",
                "muleLeftEar",
                "muleRightEar",
                "field_110709_a",
                "field_110707_b",
                "field_110708_c",
                "field_110705_d",
                "field_110706_e",
                "field_110703_f",
                "field_110704_g"));
        // Blood Magic's bile demon: head and nose; its "horns" are the two halves of the yoke the chains hang from.
        SPECIALS.put(
            "WayofTime.alchemicalWizardry.common.renderer.model.ModelBileDemon",
            new Special(false, "head", "nose"));
        // Ghasts (vanilla model, used by Special Mobs and Galacticraft too; Twilight Forest's own): the face cube,
        // without tentacles, as the Minecraft Wiki's ghast icon.
        SPECIALS.put("net.minecraft.client.model.ModelGhast", new Special(false, "body", "field_78128_a"));
        SPECIALS.put("twilightforest.client.model.ModelTFGhast", new Special(false, "body"));
        // Twilight Forest beetles: head with jaws, antennae, eyes and teeth; pincers point ahead, so look down on them.
        SPECIALS.put(
            "twilightforest.client.model.ModelTFPinchBeetle",
            new Special(
                false,
                "head",
                "jaw1a",
                "jaw1b",
                "jaw2a",
                "jaw2b",
                "antenna1",
                "antenna2",
                "eye1",
                "eye2",
                "tooth1a",
                "tooth1b",
                "tooth1c",
                "tooth2a",
                "tooth2b",
                "tooth2c").pitch(50f));
        SPECIALS.put(
            "twilightforest.client.model.ModelTFFireBeetle",
            new Special(false, "head", "jaw1a", "jaw1b", "jaw2a", "jaw2b", "antenna1", "antenna2", "eye1", "eye2")
                .pitch(30f));
        SPECIALS.put(
            "twilightforest.client.model.ModelTFSlimeBeetle",
            new Special(false, "head", "antenna1", "antenna2", "eye1", "eye2", "mouth"));
        // Biomes O' Plenty bird: head, beak and tuft in profile.
        SPECIALS.put(
            "biomesoplenty.client.models.entities.ModelBird",
            new Special(true, "Head", "Beak", "TuftBottom", "TuftTop"));
        // Pixies (Botania, Biomes O' Plenty): the whole little creature, lit; the body is a glow pass.
        SPECIALS
            .put("vazkii.botania.client.model.ModelPixie", new Special(false, "Body", "LeftWing", "RightWing").lit());
        SPECIALS.put(
            "biomesoplenty.client.models.entities.ModelPixie",
            new Special(false, "Body", "LeftWing", "RightWing").lit());
        // Thaumcraft eldritch guardian and warden (same model): the hood and the eye inside it.
        SPECIALS.put(
            "thaumcraft.client.renderers.models.entities.ModelEldritchGuardian",
            new Special(false, "Hood1", "Hood2", "Hood3", "Hood4", "HoodEye"));
        // Twilight Forest hydra head: head and jaw head-on; the crest on top is only a thin bar from the front.
        SPECIALS.put("twilightforest.client.model.ModelTFHydraHead", new Special(false, "head", "jaw"));
        // Galacticraft's creeper boss: the middle head only.
        SPECIALS.put(
            "micdoodle8.mods.galacticraft.planets.mars.client.model.ModelCreeperBoss",
            new Special(false, "headMain"));
    }

    /** Entities whose images are not exported, with the reason. */
    private static final Map<String, String> SKIP = new TreeMap<>();
    static {
        // A plain stand of sticks, and its name is the armour stand item's: no page of its own as a creature.
        SKIP.put("etfuturum.wooden_armorstand", "armour stand: not a creature, same name as the item");
        // Abstract base classes registered in EntityList: no instance can be made.
        SKIP.put("Mob", "abstract base class, no instance");
        SKIP.put("Monster", "abstract base class, no instance");
        // A player stand-in used by mods' automation, not a mob; its renderer throws without a real player.
        SKIP.put("FakePlayerEntity", "fake player, not a creature");
    }

    /**
     * Full-body images not exported, with the reason: bosses assembled from several entities at run time, which one
     * entity cannot show (the wiki keeps its own pictures of them).
     */
    private static final Map<String, String> SKIP_FULL = new TreeMap<>();
    /** Head icon taken from another entity: the boss's head is a separate entity. */
    private static final Map<String, String> ICON_FROM = new TreeMap<>();
    static {
        ICON_FROM.put("TwilightForest.Hydra", "TwilightForest.HydraHead");
    }

    /** Icon view fixed per entity id, as the yaw of the camera: 0 front, 90 side. */
    private static final Map<String, Float> VIEWS = new java.util.HashMap<>();
    static {
        // Narrow heads that read best head-on, as the Minecraft Wiki draws them.
        VIEWS.put("Thaumcraft.Golem", 0f);
        VIEWS.put("ThaumicHorizons.GolemTH", 0f);
        VIEWS.put("etfuturum.rabbit", 0f);
        VIEWS.put("TwilightForest.Questing Ram", 0f);
        // Flat bugs: front-on, with the camera tilted from PITCHES where given.
        VIEWS.put("HardcoreEnderExpansion.Louse", 0f);
        VIEWS.put("witchery.louse", 0f);
    }

    /** Icon camera tilt per entity id, in degrees looking down: flat bugs from straight above. */
    private static final Map<String, Float> PITCHES = new java.util.HashMap<>();
    static {
        PITCHES.put("HardcoreEnderExpansion.Louse", 90f);
    }

    private static String currentId;
    /** Tilt and lighting of the icon being drawn, from its special. */
    private static float iconPitch;
    private static boolean iconLit;

    /** The special of the last {@link #headParts} call, if its renderer's main model has one. */
    private static Special lastSpecial;

    /** Height over width of the drawn pixels. */
    private static double aspect(BufferedImage image) {
        int[] box = alphaBox(image);
        if (box == null) return 1;
        return (box[3] - box[1] + 1) / (double) (box[2] - box[0] + 1);
    }

    /** Opaque pixels weighted by squareness, in units of the icon's own pixel grid. */
    private static double score(BufferedImage image) {
        int opaque = 0;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) if ((image.getRGB(x, y) >>> 24) != 0) opaque++;
        int[] box = alphaBox(image);
        if (box == null) return 0;
        double w = box[2] - box[0] + 1, h = box[3] - box[1] + 1;
        return opaque * Math.min(w, h) / Math.max(w, h);
    }

    private static BufferedImage icon(EntityLivingBase entity, Set<ModelRenderer> head, float yaw) {
        float extent = 3f * Math.max(1f, Math.max(entity.height, entity.width));
        float[] box = null;
        for (int attempt = 0; attempt < 4 && box == null; attempt++, extent *= 2f) {
            BufferedImage probe = renderIcon(entity, head, 256, 0f, entity.height / 2f, extent, yaw);
            box = bounds(probe, 0f, entity.height / 2f, extent, false);
        }
        if (box == null) return null;
        lastIconUnits = (box[2] - box[0]) * 16f;
        // 16 model units per block; snap the view to whole pixels so texels land on the pixel grid.
        int perBlock = 16 * HEAD_PIXELS;
        float side = Math.max(box[2] - box[0], box[3] - box[1]);
        while (side * perBlock > HEAD_MAX && perBlock > 16) perBlock /= 2;
        int size = Math.max(8, (int) Math.ceil(side * perBlock) + 4);
        if ((size & 1) == 1) size++;
        float cx = Math.round((box[0] + box[2]) / 2f * perBlock) / (float) perBlock;
        float cy = Math.round((box[1] + box[3]) / 2f * perBlock) / (float) perBlock;
        BufferedImage image = renderIcon(entity, head, size, cx, cy, size / (float) perBlock, yaw);
        return crop(image);
    }

    /**
     * Icon render of the head parts without what the renderer draws besides the model (a staff, a bow, a quiver):
     * the same view is drawn once more with every model part blanked, and pixels identical in both are removed; where
     * the head covers such an extra the pixels differ and the head stays.
     */
    private static BufferedImage renderIcon(EntityLivingBase entity, Set<ModelRenderer> head, int size, float cx,
        float cy, float extent, float yaw) {
        BufferedImage image = render(entity, head, size, cx, cy, extent, false, yaw);
        if (head == null) return image;
        Set<ModelRenderer> none = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        BufferedImage extras = render(entity, none, size, cx, cy, extent, false, yaw);
        int w = image.getWidth(), h = image.getHeight();
        int[] a = image.getRGB(0, 0, w, h, null, 0, w);
        int[] b = extras.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < a.length; i++) if ((b[i] >>> 24) != 0 && a[i] == b[i]) a[i] = 0;
        image.setRGB(0, 0, w, h, a, 0, w);
        return image;
    }

    private static BufferedImage render(EntityLivingBase entity, Set<ModelRenderer> keep, int size, float cx, float cy,
        float extent, boolean lit) {
        return render(entity, keep, size, cx, cy, extent, lit, 0f);
    }

    /**
     * Render into a square FBO. The view maps a square of {@code extent} blocks centred on ({@code cx}, {@code cy})
     * in view space (after the camera rotation) onto the image.
     */
    private static BufferedImage render(EntityLivingBase entity, Set<ModelRenderer> keep, int size, float cx, float cy,
        float extent, boolean lit, float iconYaw) {
        Map<ModelRenderer, Object[]> hidden = keep == null ? null : hideExcept(entity, keep);
        Map<ModelRenderer, float[]> pushed = keep == null || keep.isEmpty() ? null : pushForward(keep, iconYaw);
        // The flat icons show the creature only: no held items or body armour, no add-on decorations.
        Runnable undecorate = lit ? () -> {} : undecorate(entity);
        World previous = pose(entity);
        float bossHealth = BossStatus.healthScale;
        int bossTimeout = BossStatus.statusBarTime;
        String bossName = BossStatus.bossName;
        boolean bossFog = BossStatus.hasColorModifier;
        RenderManager manager = RenderManager.instance;
        float viewX = manager.playerViewX, viewY = manager.playerViewY;
        try (FBOHelper fbo = new FBOHelper(size)) {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            fbo.begin();
            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glPushMatrix();
            GLStateManager.glLoadIdentity();
            GLStateManager.glOrtho(-1, 1, -1, 1, -1000, 1000);
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            try {
                float scale = 2f / extent;
                GLStateManager.glScalef(scale, scale, scale);
                GLStateManager.glTranslatef(-cx, -cy, 0f);
                if (lit) {
                    GLStateManager.glRotatef(FULL_PITCH, 1f, 0f, 0f);
                    GLStateManager.glRotatef(FULL_YAW, 0f, 1f, 0f);
                } else {
                    if (iconPitch != 0f) GLStateManager.glRotatef(iconPitch, 1f, 0f, 0f);
                    // Side view: the creature faces left.
                    if (iconYaw != 0f) GLStateManager.glRotatef(-iconYaw, 0f, 1f, 0f);
                }
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
                GLStateManager.glColor4f(1, 1, 1, 1);
                if (lit || iconLit) {
                    GLStateManager.enableLighting();
                    RenderHelper.enableStandardItemLighting();
                } else {
                    RenderHelper.disableStandardItemLighting();
                    GLStateManager.disableLighting();
                }
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
                manager.playerViewX = lit ? FULL_PITCH : 0f;
                manager.playerViewY = 180f;
                manager.renderEntityWithPosYaw(entity, 0, 0, 0, 0f, 0f);
                if (lit) for (Entity part : parts(entity)) manager.renderEntityWithPosYaw(
                    part,
                    part.posX - entity.posX,
                    part.posY - entity.posY,
                    part.posZ - entity.posZ,
                    part.rotationYaw,
                    0f);
                if (!lit) GLStateManager.disableLighting();
            } finally {
                RenderHelper.disableStandardItemLighting();
                GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
                GLStateManager.glPopMatrix();
                GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
                fbo.end();
                GL11.glPopAttrib();
            }
            BufferedImage image = flip(fbo.saveToImage());
            fbo.restoreTexture();
            return image;
        } finally {
            manager.playerViewX = viewX;
            manager.playerViewY = viewY;
            BossStatus.healthScale = bossHealth;
            BossStatus.statusBarTime = bossTimeout;
            BossStatus.bossName = bossName;
            BossStatus.hasColorModifier = bossFog;
            entity.worldObj = previous;
            undecorate.run();
            if (pushed != null) for (Map.Entry<ModelRenderer, float[]> entry : pushed.entrySet()) {
                entry.getKey().offsetX = entry.getValue()[0];
                entry.getKey().offsetY = entry.getValue()[1];
                entry.getKey().offsetZ = entry.getValue()[2];
            }
            if (hidden != null) restore(hidden);
        }
    }

    /**
     * Take off everything but the helmet and switch off Battlegear 2's skeleton quiver (drawn on the back from
     * {@code RenderLivingEvent.Post}, outside the model); returns the undo.
     */
    private static Runnable undecorate(EntityLivingBase entity) {
        List<Runnable> undo = new ArrayList<>();
        if (entity instanceof EntityLiving living) {
            for (int slot = 0; slot < 4; slot++) {
                net.minecraft.item.ItemStack stack = living.getEquipmentInSlot(slot);
                if (stack == null) continue;
                int index = slot;
                living.setCurrentItemOrArmor(index, null);
                undo.add(() -> living.setCurrentItemOrArmor(index, stack));
            }
        }
        if (Loader.isModLoaded("battlegear2")) {
            try {
                Field quiver = Class.forName("mods.battlegear2.utils.BattlegearConfig")
                    .getField("enableSkeletonQuiver");
                boolean value = quiver.getBoolean(null);
                quiver.setBoolean(null, false);
                undo.add(() -> {
                    try {
                        quiver.setBoolean(null, value);
                    } catch (IllegalAccessException ignored) {}
                });
            } catch (Exception | LinkageError e) {
                LOG.warn("Battlegear skeleton quiver switch unavailable", e);
            }
        }
        return () -> undo.forEach(Runnable::run);
    }

    /** GL reads bottom row first. */
    private static BufferedImage flip(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            out.setRGB(0, h - 1 - y, w, 1, row, 0, w);
        }
        return out;
    }

    /**
     * Visible bounds of a probe render in view units (minX, minY, maxX, maxY); null when nothing was drawn or the
     * drawing touches the border (the view was too small).
     */
    private static float[] bounds(BufferedImage image, float cx, float cy, float extent, boolean lit) {
        int[] box = alphaBox(clean(image));
        if (box == null) return null;
        int size = image.getWidth();
        if (box[0] == 0 || box[1] == 0 || box[2] == size - 1 || box[3] == size - 1) return null;
        float unit = extent / size;
        // Image y grows downwards; view y upwards.
        return new float[] { cx - extent / 2f + box[0] * unit, cy + extent / 2f - (box[3] + 1) * unit,
            cx - extent / 2f + (box[2] + 1) * unit, cy + extent / 2f - box[1] * unit };
    }

    /**
     * Drop specks: connected pieces (8-neighbour, any alpha) smaller than 1 % of the largest piece, such as a stray
     * pixel of a tiny part far from the head. Works on a copy.
     */
    static BufferedImage clean(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int[] px = image.getRGB(0, 0, w, h, null, 0, w);
        int[] label = new int[w * h];
        List<Integer> sizes = new ArrayList<>();
        sizes.add(0);
        int[] stack = new int[w * h];
        for (int start = 0; start < px.length; start++) {
            if (label[start] != 0 || (px[start] >>> 24) == 0) continue;
            int id = sizes.size(), count = 0, top = 0;
            stack[top++] = start;
            label[start] = id;
            while (top > 0) {
                int p = stack[--top];
                count++;
                int x = p % w, y = p / w;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int q = ny * w + nx;
                    if (label[q] == 0 && (px[q] >>> 24) != 0) {
                        label[q] = id;
                        stack[top++] = q;
                    }
                }
            }
            sizes.add(count);
        }
        int largest = 0;
        for (int size : sizes) largest = Math.max(largest, size);
        int min = Math.max(1, largest / 100);
        for (int i = 0; i < px.length; i++) if (label[i] != 0 && sizes.get(label[i]) < min) px[i] = 0;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    private static int[] alphaBox(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                if ((row[x] >>> 24) == 0) continue;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        return maxX < 0 ? null : new int[] { minX, minY, maxX, maxY };
    }

    /** Crop to the drawn pixels and centre them on a transparent square. */
    private static BufferedImage crop(BufferedImage image) {
        image = clean(image);
        int[] box = alphaBox(image);
        if (box == null) return null;
        int w = box[2] - box[0] + 1, h = box[3] - box[1] + 1, side = Math.max(w, h);
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = image.getRGB(box[0], box[1], w, h, null, 0, w);
        out.setRGB((side - w) / 2, (side - h) / 2, w, h, pixels, 0, w);
        return out;
    }

    /** 2×2 box filter with premultiplied alpha. */
    private static BufferedImage halve(BufferedImage image) {
        int w = image.getWidth() / 2, h = image.getHeight() / 2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            long a = 0, r = 0, g = 0, b = 0;
            for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
                int p = image.getRGB(2 * x + dx, 2 * y + dy);
                int pa = p >>> 24;
                a += pa;
                r += ((p >> 16) & 255) * pa;
                g += ((p >> 8) & 255) * pa;
                b += (p & 255) * pa;
            }
            if (a == 0) continue;
            out.setRGB(x, y, (int) (a / 4) << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a));
        }
        return out;
    }

    /** Head parts of every model the renderer holds, with all their attached children. */
    private static Set<ModelRenderer> headParts(Render render) {
        Set<ModelRenderer> out = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        // Parts a model names as something else (a yoke, a weapon, a body) stay out even when hung on the head.
        Set<ModelRenderer> named = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelBase model : models(render)) {
            for (Class<?> type = model.getClass(); type != null
                && type != ModelBase.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || isHead(field.getName())) continue;
                    try {
                        field.setAccessible(true);
                        Object value = field.get(model);
                        if (value instanceof ModelRenderer part) named.add(part);
                        else if (value instanceof ModelRenderer[]parts)
                            for (ModelRenderer part : parts) if (part != null) named.add(part);
                    } catch (Exception | LinkageError ignored) {}
                }
            }
        }
        excluded = named;
        lastSpecial = null;
        for (ModelBase model : models(render)) {
            for (Class<?> type = model.getClass(); type != null && type != ModelBase.class
                && lastSpecial == null; type = type.getSuperclass()) lastSpecial = SPECIALS.get(type.getName());
        }
        for (ModelBase model : models(render)) {
            for (Class<?> type = model.getClass(); type != null
                && type != ModelBase.class; type = type.getSuperclass()) {
                Set<String> numbered = new HashSet<>();
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    if (lastSpecial != null ? !lastSpecial.heads.contains(field.getName()) : !isHead(field.getName()))
                        continue;
                    // Numbered heads (head1, head2, head3): only the first is the icon's.
                    String name = field.getName();
                    String base = name.replaceAll("\\d+$", "");
                    if (!base.equals(name) && !base.startsWith("field_") && !numbered.add(base)) continue;
                    try {
                        field.setAccessible(true);
                        Object value = field.get(model);
                        if (value instanceof ModelRenderer part) addTree(out, part);
                        else if (value instanceof ModelRenderer[]parts) {
                            // An array of heads (the Wither's three): the icon shows the first, the main one.
                            for (ModelRenderer part : parts) if (part != null) {
                                addTree(out, part);
                                break;
                            }
                        }
                    } catch (Exception | LinkageError ignored) {}
                }
            }
        }
        return out;
    }

    static boolean isHead(String name) {
        if (VANILLA_HEADS.contains(name)) return true;
        if (name.startsWith("field_")) return false;
        for (String token : name.split("(?<=[a-z0-9])(?=[A-Z])|[_\\d]+")) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (HEAD_TOKENS.contains(lower) || lower.startsWith("head")) return true;
        }
        return false;
    }

    private static Set<ModelRenderer> excluded = java.util.Collections.emptySet();

    private static void addTree(Set<ModelRenderer> out, ModelRenderer part) {
        if (!out.add(part) || part.childModels == null) return;
        for (Object child : part.childModels)
            if (child instanceof ModelRenderer renderer && !excluded.contains(renderer)) addTree(out, renderer);
    }

    /** Every model held by the renderer (main, render-pass, armour layers). */
    /**
     * The armour model the worn helmet brings (Thaumcraft cultists' hoods and helmets): Forge hands it to the
     * renderer only while drawing, so no renderer field holds it. Its head parts count as the head.
     */
    private static List<ModelBase> helmetModels(EntityLivingBase entity) {
        List<ModelBase> out = new ArrayList<>();
        if (!(entity instanceof EntityLiving living)) return out;
        net.minecraft.item.ItemStack helmet = living.func_130225_q(3);
        if (helmet == null) return out;
        try {
            ModelBase model = net.minecraftforge.client.ForgeHooksClient.getArmorModel(entity, helmet, 0, null);
            if (model != null) out.add(model);
        } catch (Exception | LinkageError e) {
            LOG.warn("Helmet model unavailable for " + currentId, e);
        }
        return out;
    }

    private static List<ModelBase> helmetModels = new ArrayList<>();

    private static List<ModelBase> models(Render render) {
        List<ModelBase> out = new ArrayList<>();
        for (ModelBase model : helmetModels) if (!out.contains(model)) out.add(model);
        for (Class<?> type = render.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !ModelBase.class.isAssignableFrom(field.getType()))
                    continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(render);
                    if (value instanceof ModelBase model && !out.contains(model)) out.add(model);
                } catch (Exception | LinkageError ignored) {}
            }
        }
        return out;
    }

    /**
     * Blank every part outside {@code keep} by pointing it at display list 0, so its own boxes vanish while the
     * children it carries (a head attached to a body) still render. Returns the saved state.
     */
    private static Map<ModelRenderer, Object[]> hideExcept(EntityLivingBase entity, Set<ModelRenderer> keep) {
        Map<ModelRenderer, Object[]> saved = new IdentityHashMap<>();
        Render render = RenderManager.instance.getEntityRenderObject(entity);
        if (render == null) return saved;
        for (ModelBase model : models(render)) {
            for (Object box : model.boxList) {
                if (!(box instanceof ModelRenderer part) || keep.contains(part) || saved.containsKey(part)) continue;
                try {
                    saved.put(part, new Object[] { compiled.get(part), displayList.get(part) });
                    compiled.set(part, true);
                    displayList.set(part, 0);
                } catch (Exception ignored) {}
            }
        }
        return saved;
    }

    /**
     * Move the head parts {@link #PUSH} blocks towards the camera along the view axis. The orthographic view does not
     * change where they appear, only their depth, so the head covers what the renderer draws besides the model (the
     * Lich's staff in front of its face); the extras pass then differs there and the head is kept instead of a hole.
     * Model space to view: {@code Rx(pitch) Ry(-yaw) Ry(180) S(-1,-1,1)}; offsets apply before a part's own pivot and
     * rotation, so only the outermost head parts are moved and their children follow. Returns the old offsets.
     */
    private static Map<ModelRenderer, float[]> pushForward(Set<ModelRenderer> keep, float yaw) {
        Set<ModelRenderer> children = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelRenderer part : keep) if (part.childModels != null)
            for (Object child : part.childModels) if (child instanceof ModelRenderer renderer) children.add(renderer);
        double p = Math.toRadians(iconPitch), y = Math.toRadians(yaw);
        float dx = (float) (PUSH * Math.cos(p) * Math.sin(y));
        float dy = (float) (-PUSH * Math.sin(p));
        float dz = (float) (-PUSH * Math.cos(p) * Math.cos(y));
        Map<ModelRenderer, float[]> old = new IdentityHashMap<>();
        for (ModelRenderer part : keep) {
            if (children.contains(part)) continue;
            old.put(part, new float[] { part.offsetX, part.offsetY, part.offsetZ });
            part.offsetX += dx;
            part.offsetY += dy;
            part.offsetZ += dz;
        }
        return old;
    }

    private static final float PUSH = 8f;

    private static void restore(Map<ModelRenderer, Object[]> saved) {
        for (Map.Entry<ModelRenderer, Object[]> entry : saved.entrySet()) {
            try {
                compiled.set(entry.getKey(), entry.getValue()[0]);
                displayList.set(entry.getKey(), entry.getValue()[1]);
            } catch (Exception ignored) {}
        }
    }
}
