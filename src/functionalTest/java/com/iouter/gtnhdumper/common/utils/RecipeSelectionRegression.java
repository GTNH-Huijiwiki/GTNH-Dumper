package com.iouter.gtnhdumper.common.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.iouter.gtnhdumper.common.dumper.NEIFluidTankDumper;

import codechicken.nei.PositionedStack;
import forestry.gtnhdumper.SelectionTestHandler;

/** Native selection and actual tank extraction, plus empty-file lifecycle regression. */
public final class RecipeSelectionRegression {

    private RecipeSelectionRegression() {}

    public static void main(String[] args) throws Exception {
        Item shared = new Item();
        AtomicInteger ownershipCalls = new AtomicInteger();
        RecipeSelection selection = new RecipeSelection(true, stack -> {
            ownershipCalls.incrementAndGet();
            return stack.getItem() == shared && stack.getItemDamage() == 7;
        });
        PositionedStack hit = new PositionedStack(new ItemStack(shared, 1, 7), 0, 0, false);
        PositionedStack miss = new PositionedStack(new ItemStack(shared, 1, 8), 0, 0, false);
        SelectionTestHandler handler = new SelectionTestHandler();
        for (int index = 0; index < 10000; index++) {
            boolean selected = index == 37 || index == 9001;
            handler.arecipes.add(handler.new Row(selected ? hit : miss, selected));
        }
        BitSet selected = selection
            .selectIndexes(handler.arecipes, recipe -> NEIRecipeText.matchesOutputs("general", recipe, selection));
        check(selected.cardinality() == 2 && selected.get(37) && selected.get(9001), "Original indexes retained");
        check(ownershipCalls.get() == 2, "Ten thousand outputs need only two item/metadata ownership lookups");
        List<Map<String, Object>> tanks = NEIFluidTankDumper.dump(handler, selected::get);
        check(handler.tankReads == 2 && tanks.size() == 2, "Only matched tanks may be inspected");
        check(
            tanks.get(0)
                .get("recipe")
                .equals(37)
                && tanks.get(1)
                    .get("recipe")
                    .equals(9001),
            "Tank records must retain native indexes");
        check(handler.arecipes.size() == 10000, "Do not prune the handler's native recipe list");
        check(!selection.matches((ItemStack) null), "Null outputs do not match");
        check(
            !selection.matches(new ItemStack(new Item(), 1, 7)),
            "Same metadata on a different item must not share cache");
        check(
            selection.matchesItems(new ItemStack[] { null, miss.items[0], hit.items[0] }),
            "Candidate outputs match any item");
        ItemStack variant = new ItemStack(shared, 64, 7);
        variant.stackTagCompound = new net.minecraft.nbt.NBTTagCompound();
        variant.stackTagCompound.setString("variant", "test");
        check(
            selection.matches(variant) && ownershipCalls.get() == 3,
            "Amount/NBT must not repeat ownership resolution");
        check(
            !new RecipeSelection(true, stack -> false).matches(hit.items[0]),
            "Ownership cache stays within one export");
        RecipeSelection full = new RecipeSelection(
            false,
            stack -> { throw new AssertionError("Full export queried owner"); });
        check(
            full.selectIndexes(
                handler.arecipes,
                recipe -> { throw new AssertionError("Full export read outputs twice"); })
                .cardinality() == 10000,
            "Full export bypasses output preselection");
        SelectionTestHandler.Row special = handler.new Row(hit, false) {

            @Override
            public PositionedStack getResult() {
                throw new AssertionError("Special outputs must not rely on the primary display slot");
            }
        };
        for (String source : Arrays.asList(
            "alkalus.main.nei.NEI_Handler_Distillery",
            "com.pam.harvestcraft.AnimalTrapRecipeHandler",
            "com.pam.harvestcraft.FishTrapRecipeHandler")) {
            check(NEIRecipeText.matchesOutputs(source, special, selection), "Specialized output fields: " + source);
        }
        emptyFiles();
        System.out.println(
            "Recipe selection regression passed: 10000 rows, 2 tank reads, 2 initial ownership lookups; empty-file lifecycle passed");
    }

    private static void emptyFiles() throws Exception {
        Path root = Files.createTempDirectory("recipe-files-regression-");
        Path file = root.resolve("nested/category.json");
        Path sibling = root.resolve("sibling.json");
        try {
            JsonObject category = new JsonObject();
            category.add("recipes", new JsonArray());
            check(!RecipeFiles.prepare(file.toFile(), category), "Empty categories are skipped");
            check(!Files.exists(file.getParent()), "Empty category must not create directories");
            JsonArray records = new JsonArray();
            records.add(new JsonObject());
            category.add("recipes", records);
            check(
                RecipeFiles.prepare(file.toFile(), category) && Files.isDirectory(file.getParent()),
                "Nonempty category prepares parent");
            check(!Files.exists(file), "Preparation must not truncate or create the output file");
            Files.write(file, Collections.singletonList("old recipe"));
            Files.write(sibling, Collections.singletonList("unrelated"));
            category.add("recipes", new JsonArray());
            check(
                !RecipeFiles.prepare(file.toFile(), category) && !Files.exists(file),
                "Empty export removes stale category");
            check(Files.exists(sibling), "Do not remove other export files");
            category.add("recipes", JsonNull.INSTANCE);
            check(!RecipeFiles.prepare(file.toFile(), category), "Null recipe storage produces no file");
            check(!RecipeFiles.prepare(file.toFile(), new JsonObject()), "Missing recipe storage produces no file");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(sibling);
            Files.deleteIfExists(file.getParent());
            Files.deleteIfExists(root);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
