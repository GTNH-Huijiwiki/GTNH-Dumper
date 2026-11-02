package com.iouter.gtnhdumper.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Prepare a nonempty recipe category, or remove its stale file after filtering it to empty. */
public final class RecipeFiles {

    private RecipeFiles() {}

    public static boolean prepare(File file, JsonObject category) throws IOException {
        JsonElement recipes = category.get("recipes");
        if (recipes == null || recipes.isJsonNull()
            || recipes.getAsJsonArray()
                .size() == 0) {
            Files.deleteIfExists(file.toPath());
            return false;
        }
        File parent = file.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        return true;
    }
}
