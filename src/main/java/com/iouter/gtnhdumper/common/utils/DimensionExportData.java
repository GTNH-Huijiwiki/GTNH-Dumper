package com.iouter.gtnhdumper.common.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.DimensionManager;

import com.iouter.gtnhdumper.GTNHDumper;

import cpw.mods.fml.common.Loader;

public final class DimensionExportData {

    public static final String[] HEADERS = { "dimensionId", "providerTypeId", "providerClass", "dimensionName",
        "saveFolder", "movementFactor", "canRespawnHere", "isSurfaceWorld", "isHellWorld", "hasNoSky", "height",
        "actualHeight", "averageGroundLevel", "horizon", "cloudHeight", "shouldLoadSpawn", "providerState",
        "dimensionLookup", "candidateDimensionIds", "readErrors", "galacticSpaceInfo" };

    private final Map<Integer, WorldProvider> providers = new LinkedHashMap<>();
    private final Map<Integer, String> providerErrors = new LinkedHashMap<>();
    private final Map<String, Set<Integer>> aliases = new LinkedHashMap<>();
    private final boolean galacticraft;

    public DimensionExportData(boolean galacticraft) {
        this.galacticraft = galacticraft;
    }

    public static DimensionExportData discover() {
        return discover(Minecraft.getMinecraft().theWorld, Loader.isModLoaded("GalacticraftCore"));
    }

    static DimensionExportData discover(World clientWorld, boolean galacticraft) {
        DimensionExportData result = new DimensionExportData(galacticraft);
        // getIDs() only lists loaded server worlds. Include registered but unvisited dimensions too.
        Integer[] ids = DimensionManager.getStaticDimensionIDs();
        Arrays.sort(ids);
        for (int id : ids) {
            try {
                World world = DimensionManager.getWorld(id);
                if (world == null && clientWorld != null && clientWorld.provider.dimensionId == id) world = clientWorld;
                WorldProvider provider = world == null ? DimensionManager.createProviderFor(id) : world.provider;
                result.add(id, provider);
            } catch (RuntimeException | LinkageError e) {
                result.addFailure(id, e);
                GTNHDumper.LOG.warn("Cannot inspect dimension {}", id, e);
            }
        }
        return result;
    }

    public void add(int id, WorldProvider provider) {
        providers.put(id, provider);
        providerErrors.remove(id);
        addAlias(id, tryName(provider));
        if (galacticraft) {
            try {
                for (String name : GalacticDimensionInfo.aliases(provider)) addAlias(id, name);
            } catch (RuntimeException | LinkageError e) {
                GTNHDumper.LOG.warn("Cannot identify celestial body for dimension {}", id, e);
            }
        }
    }

    public void captureEnglishNames() {
        providers.forEach((id, provider) -> {
            if (provider == null) return;
            String name = tryName(provider);
            addAlias(id, name);
        });
    }

    public void addFailure(int id, Throwable error) {
        providers.put(id, null);
        providerErrors.put(
            id,
            "provider: " + error.getClass()
                .getSimpleName());
    }

    public List<Map<String, Object>> additionalDimensions(Set<Integer> represented, Set<String> usedKeys) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>(usedKeys);
        for (int id : new java.util.TreeSet<>(providers.keySet())) {
            if (represented.contains(id)) continue;
            Map<String, Object> row = readDimension(id);
            row.put("dimensionLookup", "registered");
            String key = "dim:" + id;
            while (!keys.add(key)) key = "forge:" + key;
            row.put("key", key);
            rows.add(row);
        }
        return rows;
    }

    private static String tryName(WorldProvider provider) {
        try {
            return provider.getDimensionName();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private void addAlias(int id, String name) {
        if (name != null && !name.trim()
            .isEmpty()) {
            aliases.computeIfAbsent(normalize(name), ignored -> new LinkedHashSet<>())
                .add(id);
        }
    }

    private static String normalize(String value) {
        return value.trim()
            .toLowerCase(Locale.ROOT);
    }

    public Map<String, Object> resolve(String internalName, String fullName, String englishName) {
        Set<Integer> matches = matches(Arrays.asList(internalName, fullName));
        if (matches.isEmpty()) matches = matches(Arrays.asList(englishName));
        Map<String, Object> result = new LinkedHashMap<>();
        if (matches.size() != 1) {
            result.put("dimensionLookup", matches.isEmpty() ? "unmatched" : "ambiguous");
            result.put("candidateDimensionIds", new ArrayList<>(matches));
            return result;
        }
        int id = matches.iterator()
            .next();
        result.putAll(readDimension(id));
        result.put("dimensionLookup", "matched");
        return result;
    }

    public Map<String, Object> resolveGtnei(Map<String, Object> info) {
        Map<String, Object> result = resolve(
            (String) info.get("internalName"),
            (String) info.get("fullName"),
            (String) info.get("originalName"));
        result.put("key", info.get("abbreviatedName"));
        result.put("gtneiInfo", new LinkedHashMap<>(info));
        return result;
    }

    private Map<String, Object> readDimension(int id) {
        WorldProvider provider = providers.get(id);
        Map<String, Object> result = provider == null ? new LinkedHashMap<>() : readProvider(provider);
        result.put("dimensionId", id);
        result.put("providerTypeId", DimensionManager.getProviderType(id));
        result.put("shouldLoadSpawn", DimensionManager.shouldLoadSpawn(id));
        if (provider == null) {
            result.put("providerState", "unavailable");
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) result.computeIfAbsent("readErrors", ignored -> new ArrayList<>());
            errors.add(providerErrors.get(id));
        } else if (galacticraft) read(result, "galacticSpaceInfo", () -> GalacticDimensionInfo.read(provider));
        return result;
    }

    private Set<Integer> matches(Collection<String> names) {
        Set<Integer> result = new java.util.TreeSet<>();
        for (String name : names) {
            if (name != null) {
                Set<Integer> ids = aliases.get(normalize(name));
                if (ids != null) result.addAll(ids);
            }
        }
        return result;
    }

    public static Map<String, Object> readProvider(WorldProvider provider) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean initialized = provider.worldObj != null;
        result.put("providerState", initialized ? "loaded" : "unbound");
        result.put(
            "providerClass",
            provider.getClass()
                .getName());
        read(result, "dimensionName", provider::getDimensionName);
        read(result, "saveFolder", provider::getSaveFolder);
        read(result, "movementFactor", provider::getMovementFactor);
        read(result, "canRespawnHere", provider::canRespawnHere);
        read(result, "isSurfaceWorld", provider::isSurfaceWorld);
        read(result, "height", provider::getHeight);
        // World initialization sets these fields; unbound defaults are not dimension data.
        if (initialized) {
            result.put("isHellWorld", provider.isHellWorld);
            result.put("hasNoSky", provider.hasNoSky);
            read(result, "actualHeight", provider::getActualHeight);
            read(result, "averageGroundLevel", provider::getAverageGroundLevel);
            read(result, "horizon", provider::getHorizon);
            read(result, "cloudHeight", provider::getCloudHeight);
        }
        return result;
    }

    public static void read(Map<String, Object> target, String key, Supplier<?> getter) {
        try {
            Object value = getter.get();
            if (value != null) target.put(key, value);
        } catch (RuntimeException | LinkageError e) {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) target.computeIfAbsent("readErrors", ignored -> new ArrayList<>());
            errors.add(
                key + ": "
                    + e.getClass()
                        .getSimpleName());
        }
    }
}
