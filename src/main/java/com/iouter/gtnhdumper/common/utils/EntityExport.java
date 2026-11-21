package com.iouter.gtnhdumper.common.utils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.INpc;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.iouter.gtnhdumper.GTNHDumper;
import com.iouter.gtnhdumper.Tags;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Every entity registered in {@link EntityList}, written to {@code entities.json} as {@code {"entities": [...]}}.
 * <p>
 * Each record is keyed by the EntityList name ({@code id}, the name spawners, biome spawn lists and quest hunt tasks
 * use). Names are the game's {@code entity.<id>.name} translations, current language and English, only where the
 * language files have the key. Living entities are constructed once, without being added to a world, to read their
 * base attributes; mob info (infernal mobs, soul vial, spawn sources, drops) comes from MobsInfo and the powered
 * spawner settings from Ender IO, when those mods are loaded.
 */
public final class EntityExport {

    private static final Logger LOG = LogManager.getLogger(Tags.MODID);

    private EntityExport() {}

    public static void write(File file) throws Exception {
        Map<String, Class<?>> registered = new TreeMap<>();
        for (Object entry : EntityList.stringToClassMapping.entrySet()) {
            Map.Entry<?, ?> pair = (Map.Entry<?, ?>) entry;
            registered.put(String.valueOf(pair.getKey()), (Class<?>) pair.getValue());
        }
        Map<String, String> english = new HashMap<>();
        Utils.getEnglishTranslation(() -> {
            for (String id : registered.keySet()) {
                String key = "entity." + id + ".name";
                if (StatCollector.canTranslate(key)) english.put(id, StatCollector.translateToLocal(key));
            }
        });
        MobsInfo mobs = MobsInfo.load();
        World world = mobs.world != null ? mobs.world : Minecraft.getMinecraft().theWorld;
        List<Map<String, Object>> entities = new ArrayList<>();
        int failures = 0;
        for (Map.Entry<String, Class<?>> entry : registered.entrySet()) {
            try {
                entities.add(record(entry.getKey(), entry.getValue(), english.get(entry.getKey()), mobs, world));
            } catch (Exception | LinkageError e) {
                failures++;
                LOG.error("Entity export failed: " + entry.getKey(), e);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", entry.getKey());
                row.put(
                    "class",
                    entry.getValue()
                        .getName());
                row.put("error", e.toString());
                entities.add(row);
            }
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("entities", entities);
        Files.write(
            file.toPath(),
            GTNHDumper.GSON.toJson(root)
                .getBytes(StandardCharsets.UTF_8));
        GTNHDumper.info("已写入：" + file.getAbsolutePath() + "（" + entities.size() + " 个实体，" + failures + " 个出错）");
    }

    private static Map<String, Object> record(String id, Class<?> cls, String english, MobsInfo mobs, World world)
        throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        String key = "entity." + id + ".name";
        if (StatCollector.canTranslate(key)) row.put("name", StatCollector.translateToLocal(key));
        if (english != null) row.put("nameEnglish", english);
        row.put("class", cls.getName());
        source(row, cls);
        row.put("categories", categories(cls));
        List<String> creatureTypes = new ArrayList<>();
        for (EnumCreatureType type : EnumCreatureType.values()) {
            if (type.getCreatureClass()
                .isAssignableFrom(cls)) creatureTypes.add(type.name());
        }
        if (!creatureTypes.isEmpty()) row.put("creatureTypes", creatureTypes);

        Entity entity = null;
        Object mob = mobs.recipe(id);
        if (mob != null) entity = (Entity) mobs.get(mob, "entity");
        if (entity == null && world != null && EntityLivingBase.class.isAssignableFrom(cls)) {
            try {
                entity = EntityList.createEntityByName(id, world);
            } catch (Exception | LinkageError e) {
                row.put("instanceError", e.toString());
            }
        }
        if (entity != null) {
            row.put("width", entity.width);
            row.put("height", entity.height);
            row.put("fireImmune", entity.isImmuneToFire());
        }
        if (entity instanceof EntityLivingBase living) stats(row, living);
        if (mob != null) mobs.describe(row, mob);
        PoweredSpawner.describe(row, id);
        return row;
    }

    /** Registering mod, Forge entity ID and network settings; vanilla global ID and spawn egg colours. */
    private static void source(Map<String, Object> row, Class<?> cls) {
        Map<?, ?> ids = ReflectionHelper.getPrivateValue(EntityList.class, null, "classToIDMapping", "field_75624_e");
        Object globalId = ids.get(cls);
        if (globalId != null) row.put("globalId", globalId);
        EntityRegistry.EntityRegistration registration = EntityRegistry.instance()
            .lookupModSpawn((Class<? extends Entity>) cls, true);
        if (registration != null) {
            ModContainer mod = registration.getContainer();
            row.put("mod", mod.getModId());
            row.put("modName", mod.getName());
            row.put("modEntityId", registration.getModEntityId());
            row.put("trackingRange", registration.getTrackingRange());
            row.put("updateFrequency", registration.getUpdateFrequency());
        } else if (cls.getName()
            .startsWith("net.minecraft.")) {
                row.put("mod", "minecraft");
                row.put("modName", "Minecraft");
            }
        if (globalId != null) {
            Object egg = EntityList.entityEggs.get(globalId);
            if (egg instanceof EntityList.EntityEggInfo info) {
                Map<String, Object> colors = new LinkedHashMap<>();
                colors.put("primary", String.format("%06x", info.primaryColor & 0xFFFFFF));
                colors.put("secondary", String.format("%06x", info.secondaryColor & 0xFFFFFF));
                row.put("spawnEgg", colors);
            }
        }
    }

    /** Kinds by class hierarchy: what the entity is, not where it spawns. */
    private static List<String> categories(Class<?> cls) {
        List<String> out = new ArrayList<>();
        if (!EntityLivingBase.class.isAssignableFrom(cls)) {
            out.add("nonLiving");
            return out;
        }
        if (IBossDisplayData.class.isAssignableFrom(cls)) out.add("boss");
        if (IMob.class.isAssignableFrom(cls)) out.add("hostile");
        if (EntityAnimal.class.isAssignableFrom(cls)) out.add("animal");
        if (EntityWaterMob.class.isAssignableFrom(cls)) out.add("water");
        if (EntityAmbientCreature.class.isAssignableFrom(cls)) out.add("ambient");
        if (EntityTameable.class.isAssignableFrom(cls)) out.add("tameable");
        if (INpc.class.isAssignableFrom(cls)) out.add("npc");
        if (EntityFlying.class.isAssignableFrom(cls)) out.add("flying");
        if (IRangedAttackMob.class.isAssignableFrom(cls)) out.add("ranged");
        if (EntityAgeable.class.isAssignableFrom(cls)) out.add("ageable");
        if (!EntityCreature.class.isAssignableFrom(cls) && !EntityLiving.class.isAssignableFrom(cls)) out.add("other");
        return out;
    }

    private static void stats(Map<String, Object> row, EntityLivingBase living) {
        attribute(row, "health", living, SharedMonsterAttributes.maxHealth);
        attribute(row, "attackDamage", living, SharedMonsterAttributes.attackDamage);
        attribute(row, "movementSpeed", living, SharedMonsterAttributes.movementSpeed);
        attribute(row, "followRange", living, SharedMonsterAttributes.followRange);
        attribute(row, "knockbackResistance", living, SharedMonsterAttributes.knockbackResistance);
        try {
            row.put("armor", living.getTotalArmorValue());
        } catch (Exception | LinkageError e) {
            row.put("armorError", e.toString());
        }
        if (living instanceof EntityLiving mob) {
            try {
                Integer experience = ReflectionHelper
                    .getPrivateValue(EntityLiving.class, mob, "experienceValue", "field_70728_aV");
                row.put("experience", experience);
            } catch (Exception | LinkageError e) {
                row.put("experienceError", e.toString());
            }
        }
        row.put("undead", living.isEntityUndead());
        row.put("creatureAttribute", String.valueOf(living.getCreatureAttribute()));
    }

    private static void attribute(Map<String, Object> row, String name, EntityLivingBase living, IAttribute type) {
        try {
            IAttributeInstance instance = living.getEntityAttribute(type);
            if (instance != null) row.put(name, instance.getAttributeValue());
        } catch (Exception | LinkageError e) {
            row.put(name + "Error", e.toString());
        }
    }

    /** MobsInfo (com.kuba6000.mobsinfo), read by reflection: the mod is optional. */
    private static final class MobsInfo {

        private Map<?, ?> recipes = new HashMap<>();
        private World world;

        static MobsInfo load() {
            MobsInfo info = new MobsInfo();
            if (!Loader.isModLoaded("mobsinfo")) return info;
            try {
                Class<?> recipe = Class.forName("com.kuba6000.mobsinfo.api.MobRecipe");
                info.recipes = (Map<?, ?>) recipe.getField("MobNameToRecipeMap")
                    .get(null);
                info.world = (World) Class.forName("com.kuba6000.mobsinfo.api.DummyWorld")
                    .getConstructor()
                    .newInstance();
            } catch (Exception | LinkageError e) {
                LOG.error("MobsInfo unavailable for entity export", e);
            }
            return info;
        }

        Object recipe(String id) {
            return recipes.get(id);
        }

        Object get(Object target, String name) {
            try {
                Field field = target.getClass()
                    .getField(name);
                return field.get(target);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }

        void describe(Map<String, Object> row, Object mob) {
            Map<String, Object> out = new LinkedHashMap<>();
            Object health = get(mob, "maxEntityHealth");
            if (health != null) out.put("health", health);
            boolean always = Boolean.TRUE.equals(get(mob, "alwaysinfernal"));
            boolean allowed = Boolean.TRUE.equals(get(mob, "infernalityAllowed"));
            out.put("infernal", always ? "always" : (allowed ? "allowed" : "never"));
            out.put("soulVial", get(mob, "isUsableInVial"));
            out.put("peaceful", get(mob, "isPeacefulAllowed"));
            Object spawns = get(mob, "spawnList");
            if (spawns instanceof Collection<?>list && !list.isEmpty()) {
                List<Object> biomes = new ArrayList<>();
                List<String> other = new ArrayList<>();
                for (Object spawn : list) {
                    BiomeGenBase biome = null;
                    try {
                        Method getter = spawn.getClass()
                            .getMethod("getBiome");
                        biome = (BiomeGenBase) getter.invoke(spawn);
                    } catch (ReflectiveOperationException ignored) {}
                    if (biome != null) biomes.add(biome.biomeID);
                    else other.add(info(spawn));
                }
                biomes.sort((a, b) -> Integer.compare((Integer) a, (Integer) b));
                java.util.Collections.sort(other);
                if (!biomes.isEmpty()) out.put("spawnBiomes", biomes);
                if (!other.isEmpty()) out.put("spawnOther", other);
            }
            Object drops = get(mob, "mOutputs");
            if (drops instanceof Collection<?>list && !list.isEmpty()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Object drop : list) rows.add(drop(drop));
                out.put("drops", rows);
            }
            row.put("mobsInfo", out);
        }

        private String info(Object spawn) {
            try {
                return String.valueOf(
                    spawn.getClass()
                        .getMethod("getInfo")
                        .invoke(spawn));
            } catch (ReflectiveOperationException e) {
                return spawn.toString();
            }
        }

        private Map<String, Object> drop(Object drop) {
            Map<String, Object> out = new LinkedHashMap<>();
            ItemStack stack = (ItemStack) get(drop, "stack");
            if (stack != null) {
                out.put("item", Utils.getItemStackShortKey(stack));
                out.put("count", stack.stackSize);
            }
            out.put("type", String.valueOf(get(drop, "type")));
            // MobsInfo keeps chances in 1/100 %: 10000 = always.
            out.put("chance", get(drop, "chance"));
            for (String flag : new String[] { "lootable", "playerOnly", "variableChance" }) {
                if (Boolean.TRUE.equals(get(drop, flag))) out.put(flag, true);
            }
            Object enchantable = get(drop, "enchantable");
            if (enchantable != null) out.put("enchantable", enchantable);
            Object damages = get(drop, "damages");
            if (damages instanceof Map<?, ?>map && !map.isEmpty()) {
                Map<String, Object> copy = new TreeMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) copy.put(String.valueOf(entry.getKey()), entry.getValue());
                out.put("damages", copy);
            }
            Object modifiers = get(drop, "chanceModifiers");
            if (modifiers instanceof Collection<?>list && !list.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (Object modifier : list) names.add(
                    modifier.getClass()
                        .getSimpleName());
                out.put("chanceModifiers", names);
            }
            Object extra = get(drop, "additionalInfo");
            if (extra instanceof Collection<?>list && !list.isEmpty()) out.put("additionalInfo", new ArrayList<>(list));
            return out;
        }
    }

    /** Ender IO powered spawner: blacklisted entities and the power cost multiplier per entity. */
    private static final class PoweredSpawner {

        private static Object config;
        private static boolean loaded;

        static void describe(Map<String, Object> row, String id) {
            if (!loaded) {
                loaded = true;
                if (Loader.isModLoaded("EnderIO")) {
                    try {
                        config = Class.forName("crazypants.enderio.machine.spawner.PoweredSpawnerConfig")
                            .getMethod("getInstance")
                            .invoke(null);
                    } catch (Exception | LinkageError e) {
                        LOG.error("Ender IO powered spawner config unavailable", e);
                    }
                }
            }
            // 只有生物能放进电动刷怪笼。
            if (config == null || !row.containsKey("health")) return;
            try {
                Map<String, Object> out = new LinkedHashMap<>();
                boolean blacklisted = (Boolean) config.getClass()
                    .getMethod("isBlackListed", String.class)
                    .invoke(config, id);
                out.put("blacklisted", blacklisted);
                out.put(
                    "costMultiplier",
                    config.getClass()
                        .getMethod("getCostMultiplierFor", String.class)
                        .invoke(config, id));
                row.put("poweredSpawner", out);
            } catch (Exception | LinkageError e) {
                row.put("poweredSpawnerError", e.toString());
            }
        }
    }
}
