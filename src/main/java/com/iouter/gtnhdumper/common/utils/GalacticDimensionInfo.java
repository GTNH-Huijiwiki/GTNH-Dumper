package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.world.WorldProvider;

import micdoodle8.mods.galacticraft.api.galaxies.CelestialBody;
import micdoodle8.mods.galacticraft.api.galaxies.Moon;
import micdoodle8.mods.galacticraft.api.galaxies.Planet;
import micdoodle8.mods.galacticraft.api.galaxies.Satellite;
import micdoodle8.mods.galacticraft.api.galaxies.SolarSystem;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.WorldProviderSpace;
import micdoodle8.mods.galacticraft.api.world.IExitHeight;
import micdoodle8.mods.galacticraft.api.world.IGalacticraftWorldProvider;
import micdoodle8.mods.galacticraft.api.world.ISolarLevel;

public final class GalacticDimensionInfo {

    private GalacticDimensionInfo() {}

    public static List<String> aliases(WorldProvider provider) {
        if (!(provider instanceof IGalacticraftWorldProvider)) return Collections.emptyList();
        CelestialBody body = ((IGalacticraftWorldProvider) provider).getCelestialBody();
        return body == null ? Collections.emptyList() : Arrays.asList(body.getName(), body.getUnlocalizedName());
    }

    public static Map<String, Object> read(WorldProvider provider) {
        if (!(provider instanceof IGalacticraftWorldProvider)) return null;
        IGalacticraftWorldProvider space = (IGalacticraftWorldProvider) provider;
        Map<String, Object> result = new LinkedHashMap<>();
        DimensionExportData.read(result, "celestialBody", () -> bodyInfo(space.getCelestialBody()));
        DimensionExportData.read(result, "gravity", space::getGravity);
        DimensionExportData.read(result, "fallDamageModifier", space::getFallDamageModifier);
        DimensionExportData.read(result, "thermalLevelModifier", space::getThermalLevelModifier);
        DimensionExportData.read(result, "windLevel", space::getWindLevel);
        DimensionExportData.read(result, "hasBreathableAtmosphere", space::hasBreathableAtmosphere);
        DimensionExportData.read(result, "soundVolReductionAmount", space::getSoundVolReductionAmount);
        DimensionExportData.read(result, "meteorFrequency", space::getMeteorFrequency);
        DimensionExportData.read(result, "fuelUsageMultiplier", space::getFuelUsageMultiplier);
        DimensionExportData.read(result, "netherPortalsOperational", space::netherPortalsOperational);
        DimensionExportData.read(result, "solarSize", space::getSolarSize);
        if (provider instanceof WorldProviderSpace) {
            WorldProviderSpace world = (WorldProviderSpace) provider;
            DimensionExportData.read(result, "dayLengthTicks", world::getDayLength);
            DimensionExportData.read(result, "canRainOrSnow", world::canRainOrSnow);
            DimensionExportData.read(result, "hasSunset", world::hasSunset);
        }
        if (provider instanceof ISolarLevel) {
            DimensionExportData
                .read(result, "solarEnergyMultiplier", ((ISolarLevel) provider)::getSolarEnergyMultiplier);
        }
        if (provider instanceof IExitHeight) {
            DimensionExportData.read(result, "exitHeight", ((IExitHeight) provider)::getYCoordinateToTeleport);
        }
        optional(result, provider, "atmosphericPressure", "AtmosphericPressure");
        optional(result, provider, "solarRadiation", "SolarRadiation");
        optional(result, provider, "lightningStormFrequency", "getLightningStormFrequency");
        return result;
    }

    private static Map<String, Object> bodyInfo(CelestialBody body) {
        if (body == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", body.getName());
        result.put("unlocalizedName", body.getUnlocalizedName());
        result.put("localizedName", body.getLocalizedName());
        result.put("type", body instanceof Satellite ? "satellite" : body instanceof Moon ? "moon" : "planet");
        result.put("reachable", body.getReachable());
        result.put("tierRequired", body.getTierRequirement());
        result.put("allowSatellite", body.getAllowSatellite());
        result.put(
            "atmosphere",
            body.atmosphere.stream()
                .map(Enum::name)
                .collect(Collectors.toList()));
        if (body.getBodyIcon() != null) result.put(
            "icon",
            body.getBodyIcon()
                .toString());
        result.put("relativeSize", body.getRelativeSize());
        result.put("relativeOrbitTime", body.getRelativeOrbitTime());
        result.put("phaseShift", body.getPhaseShift());
        result.put("relativeDistance", body.getRelativeDistanceFromCenter().unScaledDistance);
        Planet parent = null;
        if (body instanceof Moon) parent = ((Moon) body).getParentPlanet();
        else if (body instanceof Satellite) parent = ((Satellite) body).getParentPlanet();
        if (parent != null) result.put("parentBody", parent.getUnlocalizedName());
        Planet planet = body instanceof Planet ? (Planet) body : parent;
        SolarSystem system = planet == null ? null : planet.getParentSolarSystem();
        if (system != null) {
            Map<String, Object> solarSystem = new LinkedHashMap<>();
            solarSystem.put("name", system.getName());
            solarSystem.put("unlocalizedName", system.getUnlocalizedName());
            solarSystem.put("localizedName", system.getLocalizedName());
            solarSystem.put("parentGalaxy", system.getUnlocalizedParentGalaxyName());
            if (system.getMainStar() != null) solarSystem.put(
                "mainStar",
                system.getMainStar()
                    .getUnlocalizedName());
            result.put("solarSystem", solarSystem);
        }
        return result;
    }

    private static void optional(Map<String, Object> target, Object provider, String key, String methodName) {
        final Method method;
        try {
            method = provider.getClass()
                .getMethod(methodName);
        } catch (NoSuchMethodException e) {
            return;
        }
        DimensionExportData.read(target, key, () -> {
            try {
                return method.invoke(provider);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
