package com.iouter.gtnhdumper.common.utils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;

import com.iouter.gtnhdumper.GTNHDumper;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Registered biomes with their climate, generation and spawn parameters, plus Witchery's two biome books as the game
 * shows them. Written to {@code biomes.json}:
 * {@code {"biomes": [...], "bookOfBiomes": {...}, "bookOfBiomesExtended": {...}}}; the books are present when Witchery
 * is loaded.
 */
public final class BiomeExport {

    private static final Field ENABLE_RAIN = ReflectionHelper
        .findField(BiomeGenBase.class, "enableRain", "field_76765_S");

    private BiomeExport() {}

    public static void write(File file) throws Exception {
        Map<Integer, List<Integer>> bookPages = new LinkedHashMap<>();
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> books = Loader.isModLoaded("witchery") ? WitcheryBiomeBooks.export(bookPages) : null;
        List<Map<String, Object>> biomes = new ArrayList<>();
        for (BiomeGenBase biome : BiomeGenBase.getBiomeGenArray()) {
            if (biome != null) biomes.add(describe(biome, bookPages.get(biome.biomeID)));
        }
        root.put("biomes", biomes);
        if (books != null) root.putAll(books);
        Files.createDirectories(
            file.toPath()
                .getParent());
        Files.write(
            file.toPath(),
            GTNHDumper.GSON.toJson(root)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> describe(BiomeGenBase biome, List<Integer> pages) throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", biome.biomeID);
        row.put("name", biome.biomeName);
        row.put(
            "class",
            biome.getClass()
                .getName());
        row.put("temperature", biome.temperature);
        row.put("rainfall", biome.rainfall);
        row.put("highHumidity", biome.isHighHumidity());
        row.put("enableSnow", biome.getEnableSnow());
        row.put("enableRain", ENABLE_RAIN.getBoolean(biome));
        row.put("lightning", biome.canSpawnLightningBolt());
        row.put("rootHeight", biome.rootHeight);
        row.put("heightVariation", biome.heightVariation);
        row.put("color", biome.color);
        row.put("waterColorMultiplier", biome.waterColorMultiplier);
        row.put("topBlock", blockName(biome.topBlock));
        row.put("fillerBlock", blockName(biome.fillerBlock));
        List<String> types = new ArrayList<>();
        for (BiomeDictionary.Type type : BiomeDictionary.getTypesForBiome(biome)) types.add(type.name());
        row.put("types", types);
        if (Loader.isModLoaded("Forestry")) {
            row.put(
                "forestryTemperature",
                forestry.api.core.EnumTemperature.getFromBiome(biome)
                    .name());
            row.put(
                "forestryHumidity",
                forestry.api.core.EnumHumidity.getFromValue(biome.rainfall)
                    .name());
        }
        Map<String, Object> ic2 = ic2CropBonus(biome);
        if (ic2 != null) row.put("ic2CropBonus", ic2);
        Map<String, Object> spawns = new LinkedHashMap<>();
        for (EnumCreatureType type : EnumCreatureType.values()) {
            List<Map<String, Object>> entries = new ArrayList<>();
            List<?> list = biome.getSpawnableList(type);
            if (list == null) continue; // some mods return null instead of an empty list
            for (Object value : list) {
                BiomeGenBase.SpawnListEntry entry = (BiomeGenBase.SpawnListEntry) value;
                Map<String, Object> spawn = new LinkedHashMap<>();
                Object name = EntityList.classToStringMapping.get(entry.entityClass);
                spawn.put("entity", name != null ? name : entry.entityClass.getName());
                spawn.put("weight", entry.itemWeight);
                spawn.put("min", entry.minGroupCount);
                spawn.put("max", entry.maxGroupCount);
                entries.add(spawn);
            }
            if (!entries.isEmpty()) spawns.put(
                type.name()
                    .toLowerCase(),
                entries);
        }
        row.put("spawns", spawns);
        if (pages != null) row.put("bookPages", pages);
        return row;
    }

    private static String blockName(Block block) {
        return block == null ? null
            : GameData.getBlockRegistry()
                .getNameForObject(block);
    }

    /** IC2 crop humidity and nutrient bonuses by biome, when IC2's crop API is present. */
    private static Map<String, Object> ic2CropBonus(BiomeGenBase biome) {
        try {
            Class<?> crops = Class.forName("ic2.api.crops.Crops");
            Object instance = crops.getField("instance")
                .get(null);
            if (instance == null) return null;
            Method humidity = crops.getMethod("getHumidityBiomeBonus", BiomeGenBase.class);
            Method nutrients = crops.getMethod("getNutrientBiomeBonus", BiomeGenBase.class);
            Map<String, Object> bonus = new LinkedHashMap<>();
            bonus.put("humidity", humidity.invoke(instance, biome));
            bonus.put("nutrients", nutrients.invoke(instance, biome));
            return bonus;
        } catch (ReflectiveOperationException | LinkageError missing) {
            return null;
        }
    }
}
