package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.google.gson.Gson;

import micdoodle8.mods.galacticraft.api.galaxies.Moon;
import micdoodle8.mods.galacticraft.api.galaxies.Planet;
import micdoodle8.mods.galacticraft.api.galaxies.SolarSystem;
import micdoodle8.mods.galacticraft.api.world.IAtmosphericGas;
import micdoodle8.mods.galacticraft.api.world.IGalacticraftWorldProvider;
import micdoodle8.mods.galacticraft.api.world.ISolarLevel;

/** Exercises real Forge registry IDs and optional provider getters without generating worlds. */
public final class DimensionExportRegression {

    private DimensionExportRegression() {}

    public static void main(String[] args) throws Exception {
        lookup();
        registryCoverage();
        initialization();
        galactic();
        System.out.println("Dimension export regression checks passed");
    }

    private static void lookup() {
        DimensionManager.registerProviderType(9141, TestProvider.class, false);
        DimensionManager.registerDimension(9142, 9141);
        DimensionManager.registerDimension(9143, 9141);
        try {
            DimensionExportData index = new DimensionExportData(false);
            TestProvider provider = new TestProvider();
            provider.setDimension(9142);
            index.add(9142, provider);
            Map<String, Object> row = index.resolve("test", "unused", "ignored");
            check(
                row.get("dimensionId")
                    .equals(9142),
                "Dimension ID is not the provider type");
            check(
                row.get("providerTypeId")
                    .equals(9141),
                "Shared provider type retained");
            check(
                row.get("movementFactor")
                    .equals(8.0),
                "Coordinate multiplier preserved");
            check(
                row.get("canRespawnHere")
                    .equals(false),
                "False values are data, not absence");
            check(
                row.get("shouldLoadSpawn")
                    .equals(false),
                "Provider spawn setting preserved");
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("abbreviatedName", "Te");
            info.put("internalName", "test");
            info.put("fullName", "Fixture_Test");
            info.put("originalName", "Test");
            info.put("localizedName", "Test localized");
            info.put("tier", 3);
            info.put("stoneTypes", Arrays.asList("Stone", "Netherrack"));
            Map<String, Object> nested = index.resolveGtnei(info);
            check(
                nested.get("key")
                    .equals("Te")
                    && nested.get("dimensionId")
                        .equals(9142),
                "GTNEI nesting preserves the old map key and common dimension ID");
            check(
                nested.get("gtneiInfo")
                    .equals(info),
                "All seven GTNEI attributes survive nesting");
            for (String name : info.keySet())
                check(!nested.containsKey(name), "GTNEI-only field must not leak: " + name);
            com.google.gson.JsonObject json = new Gson().toJsonTree(nested)
                .getAsJsonObject();
            check(
                json.getAsJsonObject("gtneiInfo")
                    .getAsJsonPrimitive("tier")
                    .isNumber(),
                "Nested tier remains numeric instead of becoming a string");
            Map<String, Object> unknownInfo = new LinkedHashMap<>(info);
            unknownInfo.put("internalName", "unknown");
            unknownInfo.put("fullName", "unknown");
            unknownInfo.put("originalName", "unknown");
            Map<String, Object> unmatched = index.resolveGtnei(unknownInfo);
            check(
                unmatched.containsKey("gtneiInfo") && !unmatched.containsKey("dimensionId"),
                "Unmatched legacy locations retain their nested information");
            check(
                index.resolve("missing", "missing", "TEST")
                    .get("dimensionId")
                    .equals(9142),
                "English-name fallback matches legacy dimension records");
            Map<String, Object> missing = index.resolve("EndAsteroid", "EndAsteroid", "Far End Asteroids");
            check(
                missing.get("dimensionLookup")
                    .equals("unmatched") && !missing.containsKey("dimensionId"),
                "Synthetic NEI locations must not acquire an invented dimension ID");
            provider = new TestProvider();
            provider.setDimension(9143);
            index.add(9143, provider);
            Map<String, Object> ambiguous = index.resolve("test", "unused", "ignored");
            check(
                ambiguous.get("dimensionLookup")
                    .equals("ambiguous"),
                "Duplicate names remain ambiguous");
            check(!ambiguous.containsKey("dimensionId"), "Do not silently choose the first duplicate");
            check(
                ambiguous.get("candidateDimensionIds")
                    .equals(Arrays.asList(9142, 9143)),
                "Candidates have deterministic numeric order");
        } finally {
            DimensionManager.unregisterDimension(9142);
            DimensionManager.unregisterDimension(9143);
            DimensionManager.unregisterProviderType(9141);
        }
    }

    private static void registryCoverage() {
        DimensionManager.registerProviderType(9151, SpectreProvider.class, false);
        DimensionManager.registerDimension(9152, 9151);
        DimensionManager.registerDimension(9153, 9151);
        DimensionManager.registerDimension(9154, 9151);
        try {
            DimensionExportData index = DimensionExportData.discover(null, false);
            index.captureEnglishNames();
            List<Map<String, Object>> all = index.additionalDimensions(Collections.emptySet(), Collections.emptySet());
            check(
                all.size() == DimensionManager.getStaticDimensionIDs().length,
                "Every registered ID must appear even without a NEI entry");
            Map<String, Object> spectre = all.stream()
                .filter(
                    row -> row.get("dimensionId")
                        .equals(9152))
                .findFirst()
                .get();
            check(
                spectre.get("dimensionName")
                    .equals("SpectreWorld"),
                "Non-NEI dimension name retained");
            check(
                spectre.get("key")
                    .equals("dim:9152"),
                "Additional key based on real ID");
            check(
                !spectre.containsKey("gtneiInfo") && !spectre.containsKey("abbreviatedName")
                    && !spectre.containsKey("internalName")
                    && !spectre.containsKey("fullName")
                    && !spectre.containsKey("originalName")
                    && !spectre.containsKey("localizedName")
                    && !spectre.containsKey("tier")
                    && !spectre.containsKey("stoneTypes"),
                "Missing NEI data is not invented");
            check(
                all.stream()
                    .filter(row -> "SpectreWorld".equals(row.get("dimensionName")))
                    .count() == 3,
                "Shared provider and identical names do not collapse distinct dimensions");
            index.addFailure(9154, new IllegalStateException("Fixture constructor failure"));
            List<Map<String, Object>> extra = index.additionalDimensions(
                Collections.singleton(9152),
                new HashSet<>(Arrays.asList("dim:9153", "forge:dim:9153")));
            check(extra.size() == all.size() - 1, "Already represented dimension is not appended again");
            check(
                extra.stream()
                    .noneMatch(
                        row -> row.get("dimensionId")
                            .equals(9152)),
                "Covered ID excluded");
            Map<String, Object> collision = extra.stream()
                .filter(
                    row -> row.get("dimensionId")
                        .equals(9153))
                .findFirst()
                .get();
            check(
                collision.get("key")
                    .equals("forge:forge:dim:9153"),
                "Map export key cannot overwrite a pre-existing abbreviation");
            Map<String, Object> failed = extra.stream()
                .filter(
                    row -> row.get("dimensionId")
                        .equals(9154))
                .findFirst()
                .get();
            check(
                failed.get("providerTypeId")
                    .equals(9151)
                    && failed.get("providerState")
                        .equals("unavailable"),
                "Construction failure still retains registered identity");
            check(failed.containsKey("readErrors"), "Failure is explicit in exported data");
            int previous = Integer.MIN_VALUE;
            for (Map<String, Object> row : extra) {
                int id = (Integer) row.get("dimensionId");
                check(id > previous, "Additional rows have stable numeric ID order");
                previous = id;
            }
            new Gson().toJson(extra);
        } finally {
            DimensionManager.unregisterDimension(9152);
            DimensionManager.unregisterDimension(9153);
            DimensionManager.unregisterDimension(9154);
            DimensionManager.unregisterProviderType(9151);
        }
    }

    public static final class SpectreProvider extends TestProvider {

        @Override
        public String getDimensionName() {
            return "SpectreWorld";
        }
    }

    private static void initialization() throws Exception {
        WorldProviderHell unbound = new WorldProviderHell();
        Map<String, Object> row = DimensionExportData.readProvider(unbound);
        check(
            row.get("providerState")
                .equals("unbound"),
            "Unloaded provider identified");
        check(
            !row.containsKey("hasNoSky") && !row.containsKey("isHellWorld") && !row.containsKey("actualHeight"),
            "Uninitialized Nether flags and dependent height must not leak as defaults");
        check(unbound.worldObj == null, "Reading does not initialize or attach the provider");

        // Only a non-null world marker is needed: all world-dependent fixture getters are overridden.
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        TestProvider loaded = new TestProvider();
        loaded.worldObj = (WorldServer) unsafe.allocateInstance(WorldServer.class);
        loaded.hasNoSky = true;
        loaded.isHellWorld = true;
        row = DimensionExportData.readProvider(loaded);
        check(
            row.get("providerState")
                .equals("loaded")
                && row.get("hasNoSky")
                    .equals(true),
            "Initialized environmental flags retained");
        check(
            row.get("actualHeight")
                .equals(128),
            "Actual height uses initialized provider flag");
        check(!row.containsKey("horizon") && row.containsKey("readErrors"), "One getter failure is isolated");
        check(
            row.get("cloudHeight")
                .equals(96.0F),
            "Later getters still run after an error");
        new Gson().toJson(row);
    }

    @SuppressWarnings("unchecked")
    private static void galactic() {
        SpaceProvider provider = new SpaceProvider();
        check(GalacticDimensionInfo.read(new TestProvider()) == null, "Ordinary dimensions have no space block");
        check(
            GalacticDimensionInfo.aliases(provider)
                .contains("fixturemoon"),
            "Use untranslated celestial identity");
        Map<String, Object> data = GalacticDimensionInfo.read(provider);
        Map<String, Object> body = (Map<String, Object>) data.get("celestialBody");
        check(
            body.get("type")
                .equals("moon"),
            "Moon type retained");
        check(
            body.get("parentBody")
                .equals("planet.fixtureplanet"),
            "Parent is a stable translation key");
        check(
            body.get("tierRequired")
                .equals(4),
            "Rocket requirement separate from NEI tier");
        check(
            body.get("atmosphere")
                .equals(Arrays.asList("NITROGEN", "OXYGEN")),
            "Gas enum names exported");
        check(
            data.get("thermalLevelModifier")
                .equals(-2.0F),
            "Cold thermal modifiers retain sign");
        check(
            data.get("atmosphericPressure")
                .equals(3),
            "Optional GalaxySpace method exported without dependency");
        check(
            data.get("solarRadiation")
                .equals(false),
            "Optional false value retained");
        check(!data.containsKey("lightningStormFrequency"), "Absent extension omitted");
        check(
            data.get("solarEnergyMultiplier")
                .equals(1.5),
            "Solar interface exported");
        new Gson().toJson(data);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static class TestProvider extends WorldProvider {

        @Override
        public String getDimensionName() {
            return "Test";
        }

        @Override
        public double getMovementFactor() {
            return 8.0;
        }

        @Override
        public boolean canRespawnHere() {
            return false;
        }

        @Override
        public int getAverageGroundLevel() {
            return 64;
        }

        @Override
        public double getHorizon() {
            throw new IllegalStateException("Fixture failure");
        }

        @Override
        public float getCloudHeight() {
            return 96;
        }
    }

    public static final class SpaceProvider extends TestProvider implements IGalacticraftWorldProvider, ISolarLevel {

        private final Moon body;

        public SpaceProvider() {
            Planet planet = new Planet("fixtureplanet").setParentSolarSystem(new SolarSystem("fixture", "galaxy"));
            body = new Moon("fixturemoon").setParentPlanet(planet);
            body.setTierRequired(4)
                .atmosphereComponent(IAtmosphericGas.NITROGEN)
                .atmosphereComponent(IAtmosphericGas.OXYGEN);
        }

        @Override
        public Moon getCelestialBody() {
            return body;
        }

        @Override
        public float getGravity() {
            return 0.02F;
        }

        @Override
        public double getMeteorFrequency() {
            return 0;
        }

        @Override
        public double getFuelUsageMultiplier() {
            return 0.5;
        }

        @Override
        public boolean canSpaceshipTierPass(int tier) {
            return tier >= 4;
        }

        @Override
        public float getFallDamageModifier() {
            return 0.5F;
        }

        @Override
        public float getSoundVolReductionAmount() {
            return 1;
        }

        @Override
        public boolean hasBreathableAtmosphere() {
            return true;
        }

        @Override
        public boolean netherPortalsOperational() {
            return false;
        }

        @Override
        public boolean isGasPresent(IAtmosphericGas gas) {
            return body.atmosphere.contains(gas);
        }

        @Override
        public float getThermalLevelModifier() {
            return -2;
        }

        @Override
        public float getWindLevel() {
            return 0;
        }

        @Override
        public float getSolarSize() {
            return 1;
        }

        @Override
        public double getSolarEnergyMultiplier() {
            return 1.5;
        }

        public int AtmosphericPressure() {
            return 3;
        }

        public boolean SolarRadiation() {
            return false;
        }
    }
}
