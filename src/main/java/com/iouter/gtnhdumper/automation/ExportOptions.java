package com.iouter.gtnhdumper.automation;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Export scope selected by the one-shot request, independent of the NEI options screen. */
final class ExportOptions {

    final String outputMode;
    final String modFilter;
    final boolean smoke;
    final boolean imagesOnly;
    final boolean researchOnly;
    final boolean noImages;
    /** Selected export tasks; empty means the full export. */
    final Set<String> tasks;
    /** Entity ids for the entityimages task; empty means every entity. Existing images and metadata are kept. */
    final Set<String> entities;

    /** Task names accepted by {@code tasks}, in export order. */
    static final List<String> TASK_NAMES = Collections.unmodifiableList(
        Arrays.asList(
            "itemicon",
            "advitempanel",
            "englishnames",
            "recipes",
            "oreDictionary",
            "fluid",
            "gtmaterial",
            "gtnhdimension",
            "ticmaterial",
            "tc4thaumonomicon",
            "betterquesting",
            "biomes",
            "dimensionbiomes",
            "achievements",
            "entities",
            "entityimages"));

    ExportOptions(JsonObject request) {
        outputMode = request.has("outputMode") ? request.get("outputMode")
            .getAsString() : "timestamp";
        if (!outputMode.equals("timestamp") && !outputMode.equals("latest")) {
            throw new IllegalArgumentException("outputMode must be timestamp or latest");
        }
        modFilter = request.has("modFilter") ? request.get("modFilter")
            .getAsString()
            .trim() : "";
        smoke = flag(request, "smoke", false);
        imagesOnly = flag(request, "imagesOnly", false);
        researchOnly = flag(request, "researchOnly", false);
        if (researchOnly && (imagesOnly || smoke)) {
            throw new IllegalArgumentException("researchOnly excludes imagesOnly/smoke");
        }
        if (imagesOnly && smoke) {
            throw new IllegalArgumentException("imagesOnly excludes smoke");
        }
        Set<String> selected = new LinkedHashSet<>();
        if (request.has("tasks")) for (JsonElement task : request.getAsJsonArray("tasks")) {
            String name = task.getAsString();
            if (!TASK_NAMES.contains(name))
                throw new IllegalArgumentException("Unknown task " + name + "; valid tasks: " + TASK_NAMES);
            selected.add(name);
        }
        tasks = Collections.unmodifiableSet(selected);
        Set<String> onlyEntities = new LinkedHashSet<>();
        if (request.has("entities")) for (JsonElement entity : request.getAsJsonArray("entities")) {
            String id = entity.getAsString()
                .trim();
            if (!id.isEmpty()) onlyEntities.add(id);
        }
        entities = Collections.unmodifiableSet(onlyEntities);
        if (!entities.isEmpty() && !(tasks.size() == 1 && tasks.contains("entityimages"))) {
            throw new IllegalArgumentException("entities requires tasks to be exactly [entityimages]");
        }
        if (!tasks.isEmpty() && (researchOnly || smoke || imagesOnly)) {
            throw new IllegalArgumentException("tasks replaces researchOnly/smoke/imagesOnly; use one of them");
        }
        // These modes explicitly request image capture, including with the generated noImages=true default.
        // With tasks, images run exactly when itemicon is listed.
        noImages = tasks.isEmpty() ? researchOnly || flag(request, "noImages", true) && !imagesOnly
            : !tasks.contains("itemicon");
        if (!modFilter.isEmpty() && smoke) {
            throw new IllegalArgumentException(
                "Mod filtering requires a data or imagesOnly export; smoke data has no per-entry mod ownership");
        }
    }

    boolean latest() {
        return outputMode.equals("latest");
    }

    private static boolean flag(JsonObject request, String name, boolean fallback) {
        return request.has(name) ? request.get(name)
            .getAsBoolean() : fallback;
    }
}
