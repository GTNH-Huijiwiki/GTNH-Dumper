package com.iouter.gtnhdumper.common.utils;

import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Select whole records by original source OR an item in an explicitly defined output slot. */
public final class RecipeOutputFilter {

    private static final String[] OUTPUT_FIELDS = { "outputItems", "normalDrops", "rareDrops", "additionalDrops",
        "infernalDrops", "eliteItems", "ultraItems", "infernoItems", "fishItems", "junkItems", "treasureItems" };

    private RecipeOutputFilter() {}

    public static void filter(JsonObject category, Predicate<String> matchesSource, Predicate<String> matchesItem) {
        JsonElement records = category.get("recipes");
        if (records == null || !records.isJsonArray()) return;
        boolean eec = "kubatech.eec".equals(string(category.get("identifier")));
        JsonArray selected = new JsonArray();
        for (JsonElement record : records.getAsJsonArray()) {
            if (!record.isJsonObject()) continue;
            JsonObject recipe = record.getAsJsonObject();
            String source = string(recipe.get("sourceMod"));
            boolean matches = source != null && matchesSource.test(source);
            for (String field : OUTPUT_FIELDS) {
                if (matches) break;
                matches = matchesItems(recipe.get(field), matchesItem);
            }
            // EEC's otherItems are conditional enchanted drops; other handlers use this field for catalysts.
            if (!matches && eec) matches = matchesItems(recipe.get("otherItems"), matchesItem);
            if (matches) selected.add(record);
        }
        category.add("recipes", selected);
    }

    public static boolean matchesItems(JsonElement items, Predicate<String> matchesItem) {
        if (items == null || items.isJsonNull()) return false;
        if (items.isJsonArray()) {
            for (JsonElement item : items.getAsJsonArray()) {
                if (matchesItems(item, matchesItem)) return true;
            }
            return false;
        }
        String key = string(
            items.isJsonObject() ? items.getAsJsonObject()
                .get("key") : items);
        return key != null && !key.startsWith("fluid.") && matchesItem.test(key);
    }

    private static String string(JsonElement value) {
        return value != null && value.isJsonPrimitive()
            && value.getAsJsonPrimitive()
                .isString() ? value.getAsString() : null;
    }
}
