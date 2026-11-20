package com.iouter.gtnhdumper.automation;

import com.google.gson.JsonObject;

/** Export scope selected by the one-shot request, independent of the NEI options screen. */
final class ExportOptions {

    final String outputMode;
    final String modFilter;
    final boolean smoke;
    final boolean imagesOnly;
    final boolean researchOnly;
    final boolean noImages;

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
        // These modes explicitly request image capture, including with the generated noImages=true default.
        noImages = researchOnly || flag(request, "noImages", true) && !imagesOnly;
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
