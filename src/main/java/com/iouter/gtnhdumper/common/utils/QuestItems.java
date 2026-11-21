package com.iouter.gtnhdumper.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.iouter.gtnhdumper.GTNHDumper;

import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api2.storage.DBEntry;
import betterquesting.questing.QuestDatabase;
import bq_standard.rewards.IRewardItemOutput;
import bq_standard.tasks.ITaskItemInput;

/**
 * Item stacks shown by the quest book: quest icons, item tasks and item rewards. Rewards often carry their own NBT
 * (renamed tools, quest papers) that no recipe shows, so the item scan adds them, and {@code itemKeys.json} tells the
 * tool chain which exported item each quest stack is (null for wildcard stacks and stacks not exported).
 * <p>
 * Only call when Better Questing is loaded.
 */
public final class QuestItems {

    private QuestItems() {}

    private interface Visitor {

        /** {@code source} is the task or reward object, null for the icon. */
        void visit(String questId, String kind, int index, List<ItemStack> stacks, Object source);
    }

    /** Quest ID as the tool chain writes it: both UUID halves, big-endian, URL-safe Base64 without padding. */
    static String questId(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(buffer.array());
    }

    /** A boolean option of a task (TaskRetrieval and TaskCrafting have public ignoreNBT / partialMatch fields). */
    private static boolean flag(Object source, String name, boolean fallback) {
        if (source == null) return fallback;
        try {
            return source.getClass()
                .getField(name)
                .getBoolean(source);
        } catch (ReflectiveOperationException e) {
            return fallback;
        }
    }

    private static List<ItemStack> baseStacks(List<BigItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>(stacks.size());
        for (BigItemStack stack : stacks) result.add(stack == null ? null : stack.getBaseStack());
        return result;
    }

    private static void visit(Visitor visitor) {
        QuestDatabase.INSTANCE.orderedEntries()
            .forEach(entry -> {
                String id = questId(entry.getKey());
                IQuest quest = entry.getValue();
                BigItemStack icon = quest.getProperty(NativeProps.ICON);
                if (icon != null) visitor.visit(id, "icon", 0, Collections.singletonList(icon.getBaseStack()), null);
                for (DBEntry<ITask> task : quest.getTasks()
                    .getEntries()) {
                    if (task.getValue() instanceof ITaskItemInput input)
                        visitor.visit(id, "tasks", task.getID(), baseStacks(input.getItemInputs()), input);
                }
                for (DBEntry<IReward> reward : quest.getRewards()
                    .getEntries()) {
                    if (reward.getValue() instanceof IRewardItemOutput output)
                        visitor.visit(id, "rewards", reward.getID(), baseStacks(output.getItemOutputs()), output);
                }
            });
    }

    /** Adds every quest stack to the running item scan. */
    public static void capture() {
        try {
            visit(
                (id, kind, index, stacks, source) -> {
                    for (ItemStack stack : stacks) if (stack != null) RecipeItemVariants.capture(stack);
                });
        } catch (Exception | LinkageError e) {
            RecipeItemVariants.reportFailure("quest items", e);
        }
    }

    /**
     * Writes {questId: {icon: key, tasks: {index: [key…]}, rewards: {index: [key…]}, taskMatches: {index: [[key…]…]}}}.
     * {@code taskMatches} lists, per task stack, every exported item the task accepts (Better Questing's
     * {@code StackMatch} with the task's ignoreNBT / partialMatch). Keys follow the order of
     * {@code requiredItems}, {@code rewards} and {@code choices} in the quest files; null where no exported item
     * matches.
     */
    public static int writeKeys(File file) throws IOException {
        // NBTTagCompound.toString() follows the map's iteration order, which a copy may change, so the short key of
        // a quest stack can differ from the exported item's. Match by item, damage and NBT content instead and use
        // the key the item export wrote. Wildcard stacks match no exported item and get null.
        Map<RecipeItemVariants.Key, String> exported = new HashMap<>();
        Map<Item, List<ItemStack>> byItem = new HashMap<>();
        for (ItemStack stack : AllItemStacks.getAllItemStacks()) {
            exported.putIfAbsent(new RecipeItemVariants.Key(stack), Utils.getItemStackShortKey(stack));
            byItem.computeIfAbsent(stack.getItem(), item -> new ArrayList<>())
                .add(stack);
        }
        JsonObject root = new JsonObject();
        int[] count = { 0 };
        visit((id, kind, index, stacks, source) -> {
            JsonObject quest = root.has(id) ? root.getAsJsonObject(id) : new JsonObject();
            root.add(id, quest);
            JsonArray keys = new JsonArray();
            JsonArray matches = new JsonArray();
            boolean checkNbt = !flag(source, "ignoreNBT", false);
            boolean partial = flag(source, "partialMatch", true);
            for (ItemStack stack : stacks) {
                String key = stack == null || stack.getItem() == null ? null
                    : exported.get(new RecipeItemVariants.Key(stack));
                keys.add(key == null ? JsonNull.INSTANCE : new JsonPrimitive(key));
                count[0]++;
                if (!kind.equals("tasks")) continue;
                // Exported items the task accepts for this stack, by Better Questing's own comparison.
                JsonArray accepted = new JsonArray();
                if (stack != null && stack.getItem() != null) {
                    for (ItemStack candidate : byItem.getOrDefault(stack.getItem(), Collections.emptyList())) {
                        if (ItemComparison.StackMatch(stack, candidate, checkNbt, partial))
                            accepted.add(new JsonPrimitive(exported.get(new RecipeItemVariants.Key(candidate))));
                    }
                }
                matches.add(accepted);
            }
            if (kind.equals("icon")) {
                quest.add("icon", keys.size() > 0 ? keys.get(0) : JsonNull.INSTANCE);
                return;
            }
            JsonObject group = quest.has(kind) ? quest.getAsJsonObject(kind) : new JsonObject();
            quest.add(kind, group);
            group.add(String.valueOf(index), keys);
            if (kind.equals("tasks")) {
                JsonObject rules = quest.has("taskMatches") ? quest.getAsJsonObject("taskMatches") : new JsonObject();
                quest.add("taskMatches", rules);
                rules.add(String.valueOf(index), matches);
            }
        });
        Files.createDirectories(
            file.getParentFile()
                .toPath());
        Files.write(
            file.toPath(),
            GTNHDumper.GSON.toJson(root)
                .getBytes(StandardCharsets.UTF_8));
        return count[0];
    }
}
