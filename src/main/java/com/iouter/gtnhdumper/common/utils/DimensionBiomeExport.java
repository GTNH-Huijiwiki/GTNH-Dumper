package com.iouter.gtnhdumper.common.utils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.DimensionManager;

import com.iouter.gtnhdumper.GTNHDumper;

/**
 * The biomes each registered dimension can generate, written to {@code dimensionbiomes.json}:
 * {@code {"method": {...}, "dimensions": [{"dimensionId", "biomes": [ids], ...}]}}.
 * <p>
 * Every dimension gets a fresh provider bound to a throw-away probe world, so the result does not depend on which
 * worlds are loaded. {@code WorldChunkManager.getBiomesToSpawnIn()} only lists the spawn-point biomes (seven in the
 * overworld), so it is exported separately as {@code spawnBiomes}; {@code biomes} is the union of the biomes found by
 * sampling the chunk manager over fixed seeds and a fixed square around the origin. Rare biomes outside the sampled
 * area can be missing; dimensions with fixed biomes are exact.
 */
public final class DimensionBiomeExport {

    /** Fixed so that repeated exports give the same result. */
    static final long[] SEEDS = { 1L, 20260101L, -559038737L, 8675309L };
    /** Half width of the sampled square, in blocks. */
    static final int RADIUS = 8192;
    /** Tile edge of a {@code getBiomesForGeneration} call, in quarter-resolution cells (1024 blocks). */
    private static final int TILE_CELLS = 256;
    /** Spacing and edge of the full-resolution probes, in blocks. */
    private static final int BLOCK_STEP = 128;
    private static final int BLOCK_TILE = 16;

    private DimensionBiomeExport() {}

    private static final Pattern DEFAULT_GENERATOR = Pattern.compile("^\\s*S:\"World Generator\"=(\\S+)\\s*$");
    private static WorldType worldType;
    private static String worldTypeSource;

    /**
     * The world type new worlds get in this pack. All dimensions of a world share it, and GTNH does not generate its
     * overworld with the vanilla default: the DefaultWorldGenerator mod selects the type in its config. Falls back to
     * the vanilla default when that config is absent or names an unknown type.
     */
    private static synchronized WorldType worldType() {
        if (worldType != null) return worldType;
        worldType = WorldType.DEFAULT;
        worldTypeSource = "vanilla default";
        File config = new File(Minecraft.getMinecraft().mcDataDir, "config/defaultworldgenerator.cfg");
        try {
            if (config.isFile()) for (String line : Files.readAllLines(config.toPath(), StandardCharsets.UTF_8)) {
                Matcher matcher = DEFAULT_GENERATOR.matcher(line);
                if (!matcher.matches()) continue;
                WorldType configured = WorldType.parseWorldType(matcher.group(1));
                if (configured != null) {
                    worldType = configured;
                    worldTypeSource = "config/defaultworldgenerator.cfg";
                }
                break;
            }
        } catch (java.io.IOException | RuntimeException e) {
            GTNHDumper.LOG.warn("Cannot read {}", config, e);
        }
        return worldType;
    }

    public static void write(File file) throws Exception {
        Map<String, Object> method = new LinkedHashMap<>();
        method.put(
            "description",
            "biomes: union over the fixed seeds of WorldChunkManager.getBiomesForGeneration (quarter resolution, "
                + "tiles of 1024 blocks) and getBiomeGenAt/loadBlockGeneratorData (one 16x16 block tile per 128 blocks), "
                + "inside the square of radius blocks around the origin. spawnBiomes: getBiomesToSpawnIn() (spawn-point "
                + "candidates only). chunkManagerSource: overworld, provider (own manager) or world-type (inherited "
                + "from the world type; the dimension's chunk generator may ignore it, so biomes is unreliable).");
        method.put("seeds", SEEDS);
        method.put("radius", RADIUS);
        method.put("worldType", worldType().getWorldTypeName());
        method.put("worldTypeSource", worldTypeSource);

        List<Map<String, Object>> dimensions = new ArrayList<>();
        Integer[] ids = DimensionManager.getStaticDimensionIDs();
        Arrays.sort(ids);
        for (int id : ids) dimensions.add(probe(id));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("method", method);
        root.put("dimensions", dimensions);
        Files.createDirectories(
            file.toPath()
                .getParent());
        Files.write(
            file.toPath(),
            GTNHDumper.GSON.toJson(root)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> probe(int id) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dimensionId", id);
        List<String> errors = new ArrayList<>();
        WorldProvider provider;
        try {
            provider = DimensionManager.createProviderFor(id);
        } catch (RuntimeException | LinkageError e) {
            return failed(row, errors, "provider: " + describe(e));
        }
        row.put(
            "providerClass",
            provider.getClass()
                .getName());
        TreeSet<Integer> found = new TreeSet<>();
        int samples = 0;
        try {
            // The first seed also binds the provider; later seeds rebind a fresh provider, because chunk managers
            // take the seed at construction.
            for (int i = 0; i < SEEDS.length; i++) {
                WorldProvider bound = i == 0 ? provider : DimensionManager.createProviderFor(id);
                WorldChunkManager manager = bind(bound, SEEDS[i]);
                if (manager == null) {
                    errors.add("no chunk manager");
                    break;
                }
                if (i == 0) {
                    row.put(
                        "chunkManagerClass",
                        manager.getClass()
                            .getName());
                    row.put("chunkManagerSource", chunkManagerSource(bound, manager));
                    row.put("spawnBiomes", spawnBiomes(manager, errors));
                }
                samples += sample(manager, found, errors);
            }
        } catch (RuntimeException | LinkageError e) {
            errors.add("probe: " + describe(e));
        }
        row.put("biomes", new ArrayList<>(found));
        row.put("samples", samples);
        if (found.isEmpty() && errors.isEmpty()) errors.add("no biome found");
        if (!errors.isEmpty()) row.put("errors", errors);
        return row;
    }

    private static Class<?> worldTypeManagerClass;

    /**
     * Where the chunk manager comes from: {@code overworld} for surface providers, {@code world-type} when the
     * manager is the plain vanilla one or the class the world type gives every dimension, {@code provider} when the
     * dimension builds its own. In the {@code world-type} case the dimension's real chunk generator may ignore the
     * manager, so its biome list is not reliable.
     */
    private static String chunkManagerSource(WorldProvider provider, WorldChunkManager manager) {
        if (provider instanceof WorldProviderSurface) return "overworld";
        // The overworld provider takes its manager straight from the world type.
        if (worldTypeManagerClass == null)
            worldTypeManagerClass = bind(DimensionManager.createProviderFor(0), SEEDS[0]).getClass();
        Class<?> type = manager.getClass();
        return type == WorldChunkManager.class || type == worldTypeManagerClass ? "world-type" : "provider";
    }

    private static Map<String, Object> failed(Map<String, Object> row, List<String> errors, String error) {
        errors.add(error);
        row.put("biomes", new ArrayList<Integer>());
        row.put("errors", errors);
        return row;
    }

    private static WorldChunkManager bind(WorldProvider provider, long seed) {
        WorldSettings settings = new WorldSettings(seed, WorldSettings.GameType.SURVIVAL, true, false, worldType());
        // Not finishSetup(): only the chunk manager is needed, not a chunk provider or map storage.
        World world = new ProbeWorld(provider, settings);
        provider.registerWorld(world);
        return provider.worldChunkMgr;
    }

    private static List<Integer> spawnBiomes(WorldChunkManager manager, List<String> errors) {
        TreeSet<Integer> ids = new TreeSet<>();
        try {
            List<?> biomes = manager.getBiomesToSpawnIn();
            if (biomes != null)
                for (Object biome : biomes) if (biome instanceof BiomeGenBase) ids.add(((BiomeGenBase) biome).biomeID);
        } catch (RuntimeException | LinkageError e) {
            errors.add("getBiomesToSpawnIn: " + describe(e));
        }
        return new ArrayList<>(ids);
    }

    /** Adds the biomes seen by one chunk manager; returns the number of cells read. */
    private static int sample(WorldChunkManager manager, TreeSet<Integer> found, List<String> errors) {
        int samples = 0;
        int cells = RADIUS * 2 / 4; // quarter-resolution cells across the square
        int start = -RADIUS / 4;
        try {
            for (int x = start; x < start + cells; x += TILE_CELLS) {
                for (int z = start; z < start + cells; z += TILE_CELLS) {
                    BiomeGenBase[] tile = manager.getBiomesForGeneration(null, x, z, TILE_CELLS, TILE_CELLS);
                    samples += collect(tile, found);
                }
            }
        } catch (RuntimeException | LinkageError e) {
            errors.add("getBiomesForGeneration: " + describe(e));
        }
        try {
            for (int x = -RADIUS; x < RADIUS; x += BLOCK_STEP) {
                for (int z = -RADIUS; z < RADIUS; z += BLOCK_STEP) {
                    BiomeGenBase[] tile = manager.loadBlockGeneratorData(null, x, z, BLOCK_TILE, BLOCK_TILE);
                    samples += collect(tile, found);
                }
            }
        } catch (RuntimeException | LinkageError e) {
            errors.add("loadBlockGeneratorData: " + describe(e));
        }
        try {
            BiomeGenBase origin = manager.getBiomeGenAt(0, 0);
            if (origin != null) found.add(origin.biomeID);
        } catch (RuntimeException | LinkageError e) {
            errors.add("getBiomeGenAt: " + describe(e));
        }
        return samples;
    }

    private static int collect(BiomeGenBase[] tile, TreeSet<Integer> found) {
        if (tile == null) return 0;
        for (BiomeGenBase biome : tile) if (biome != null) found.add(biome.biomeID);
        return tile.length;
    }

    private static String describe(Throwable e) {
        String message = e.getMessage();
        return e.getClass()
            .getSimpleName() + (message == null ? "" : ": " + message);
    }

    /** A world with no chunks, only a seed and world info: enough for a provider to build its chunk manager. */
    private static final class ProbeWorld extends World {

        ProbeWorld(WorldProvider provider, WorldSettings settings) {
            super(null, "dimension-biome-probe", provider, settings, Minecraft.getMinecraft().mcProfiler);
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int id) {
            return null;
        }
    }
}
