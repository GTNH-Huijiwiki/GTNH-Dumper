package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Exercises source/output union selection using the same entry points as normal and custom dumps. */
public final class RecipeOutputFilterRegression {

    private RecipeOutputFilterRegression() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> mods = new LinkedHashMap<>();
        mods.put("exampleaddon", "Example Addon");
        mods.put("other", "Other");
        ModFilter filter = new ModFilter("exampleaddon", mods);
        Item item = new Item();
        // Populate the real registry without the FML lifecycle, which requires LaunchClassLoader.
        Method add = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        add.setAccessible(true);
        add.invoke(Item.itemRegistry, 31000, "exampleaddon:product", item);
        add.invoke(Item.itemRegistry, 31001, "other:product", new Item());
        check(filter.matchesOutputItemKey("exampleaddon:product"), "Direct item output");
        check(filter.matchesOutputItemKey("exampleaddon:product:7"), "Metadata item output");
        check(
            RecipeOutputFilter.matchesItems(
                new JsonParser().parse("[\"#outputFilterRegression\"]"),
                "#outputFilterRegression"::equals),
            "Ore dictionary output alternatives must reach the item ownership resolver");
        check(!filter.matchesOutputItemKey("exampleaddon:product:bad"), "Invalid metadata");
        check(!filter.matchesOutputItemKey("exampleaddon:missing"), "Unregistered output");
        check(!filter.matchesOutputItemKey("fluid.exampleaddon:product"), "Fluid is not an item");
        check(!filter.matchesItemOutputs(null), "Missing native outputs");
        check(!filter.matchesItemOutputs(new ItemStack[] { null }), "Null native output");
        check(filter.matchesItemOutputs(new ItemStack[] { null, new ItemStack(item) }), "Native GT outputs");

        JsonObject category = json("{\"identifier\":\"test\",\"recipes\":[]}");
        JsonArray records = category.getAsJsonArray("recipes");
        JsonObject sourceOnly = json("{\"sourceMod\":\"exampleaddon\",\"outputItems\":[\"other:product\"]}");
        JsonObject outputOnly = json(
            "{\"sourceMod\":\"other\",\"inputItems\":[\"other:product\"],"
                + "\"outputItems\":[\"other:product\",{\"key\":\"exampleaddon:product:7\",\"chance\":50,\"nbt\":\"{x:1}\"}],\"duration\":80}");
        JsonObject both = json("{\"sourceMod\":\"exampleaddon\",\"outputItems\":[\"exampleaddon:product\"]}");
        JsonObject unknownSource = json(
            "{\"outputItems\":[[null,\"other:product\",{\"key\":\"exampleaddon:product:7\",\"amount\":4}]]}");
        records.add(sourceOnly);
        records.add(outputOnly);
        records.add(both);
        records.add(unknownSource);
        records.add(
            json(
                "{\"inputItems\":[\"exampleaddon:product\"],\"otherItems\":[\"exampleaddon:product\"],\"outputItems\":[\"other:product\"]}"));
        records.add(
            json("{\"outputFluids\":[\"exampleaddon:product\"],\"outputItems\":[\"fluid.exampleaddon:product\"]}"));
        records.add(json("{\"outputItems\":[{\"key\":\"other:product\",\"tooltip\":\"exampleaddon:product\"}]}"));
        records.add(json("{\"outputItems\":[null,{},42,true]}"));
        String original = category.toString();
        new ModFilter("", mods).filterRecipes(category);
        check(original.equals(category.toString()), "Full export must remain unchanged");
        filter.filterRecipes(category);
        JsonArray selected = category.getAsJsonArray("recipes");
        check(selected.size() == 4, "Source/output union must retain exactly four whole recipes");
        check(selected.get(0) == sourceOnly && selected.get(1) == outputOnly, "Keep complete records and order");
        check(selected.get(2) == both && selected.get(3) == unknownSource, "Union must not duplicate records");
        check(
            "other".equals(
                outputOnly.get("sourceMod")
                    .getAsString()),
            "Output matching must not rewrite provenance");
        check(
            outputOnly.getAsJsonArray("outputItems")
                .size() == 2,
            "Foreign coproducts must be retained");

        for (String field : new String[] { "normalDrops", "rareDrops", "additionalDrops", "infernalDrops", "eliteItems",
            "ultraItems", "infernoItems", "fishItems", "junkItems", "treasureItems" }) {
            JsonObject drops = json(
                "{\"recipes\":[{\"" + field + "\":[{\"key\":\"exampleaddon:product\",\"chance\":1}]}]}");
            filter.filterRecipes(drops);
            check(
                drops.getAsJsonArray("recipes")
                    .size() == 1,
                "Conditional drop output: " + field);
        }
        JsonObject eec = json(
            "{\"identifier\":\"kubatech.eec\",\"recipes\":[{\"otherItems\":[\"exampleaddon:product\"]}]}");
        filter.filterRecipes(eec);
        check(
            eec.getAsJsonArray("recipes")
                .size() == 1,
            "EEC conditional enchanted drops");
        JsonObject absent = json("{\"recipes\":null}");
        filter.filterRecipes(absent);
        check(
            absent.get("recipes")
                .isJsonNull(),
            "Unsupported recipe storage remains explicit");
        System.out.println("Recipe output filter regression checks passed");
    }

    private static JsonObject json(String value) {
        return new JsonParser().parse(value)
            .getAsJsonObject();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
