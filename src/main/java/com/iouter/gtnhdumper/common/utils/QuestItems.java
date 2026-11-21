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
import betterquesting.api2.storage.DBEntry;
import betterquesting.questing.QuestDatabase;
import bq_standard.rewards.IRewardItemOutput;
import bq_standard.tasks.ITaskItemInput;

/**
 * Item stacks shown by the quest book: quest icons, item tasks and item rewards. Rewards often carry their own NBT
 * (renamed tools, quest papers) that no recipe shows, so the item scan adds them, and {@code itemKeys.json} tells the
 * tool chain which exported item each quest stack is.
 * <p>
 * Only call when Better Questing is loaded.
 */
public final class QuestItems {

    private QuestItems() {}

    private interface Visitor {

        void visit(String questId, String kind, int index, List<ItemStack> stacks);
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
                if (icon != null) visitor.visit(id, "icon", 0, Collections.singletonList(icon.getBaseStack()));
                for (DBEntry<ITask> task : quest.getTasks()
                    .getEntries()) {
                    if (task.getValue() instanceof ITaskItemInput input)
                        visitor.visit(id, "tasks", task.getID(), baseStacks(input.getItemInputs()));
                }
                for (DBEntry<IReward> reward : quest.getRewards()
                    .getEntries()) {
                    if (reward.getValue() instanceof IRewardItemOutput output)
                        visitor.visit(id, "rewards", reward.getID(), baseStacks(output.getItemOutputs()));
                }
            });
    }

    /**
     * Keys recorded when the item scan copied the stacks. Some items rewrite the NBT of the live quest stack later
     * (Tinkers' tools do), so {@link #writeKeys} reuses these to match the exported item exactly.
     */
    private static final Map<String, List<String>> SCANNED = new HashMap<>();

    private static String slot(String id, String kind, int index) {
        return id + "/" + kind + "/" + index;
    }

    /** Adds every quest stack to the running item scan. */
    public static void capture() {
        try {
            SCANNED.clear();
            visit((id, kind, index, stacks) -> {
                List<String> keys = new ArrayList<>(stacks.size());
                for (ItemStack stack : stacks) {
                    if (stack == null || stack.getItem() == null) {
                        keys.add(null);
                        continue;
                    }
                    ItemStack copy = stack.copy();
                    keys.add(Utils.getItemStackShortKey(copy));
                    RecipeItemVariants.capture(copy);
                }
                SCANNED.put(slot(id, kind, index), keys);
            });
        } catch (Exception | LinkageError e) {
            RecipeItemVariants.reportFailure("quest items", e);
        }
    }

    /**
     * Writes {questId: {icon: key, tasks: {index: [key…]}, rewards: {index: [key…]}}}. Keys follow the order of
     * {@code requiredItems}, {@code rewards} and {@code choices} in the quest files; null where a stack is empty.
     */
    public static int writeKeys(File file) throws IOException {
        JsonObject root = new JsonObject();
        int[] count = { 0 };
        visit((id, kind, index, stacks) -> {
            JsonObject quest = root.has(id) ? root.getAsJsonObject(id) : new JsonObject();
            root.add(id, quest);
            JsonArray keys = new JsonArray();
            List<String> scanned = SCANNED.get(slot(id, kind, index));
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack stack = stacks.get(i);
                String key = scanned != null && scanned.size() == stacks.size() ? scanned.get(i)
                    : stack == null || stack.getItem() == null ? null : Utils.getItemStackShortKey(stack);
                keys.add(key == null ? JsonNull.INSTANCE : new JsonPrimitive(key));
                count[0]++;
            }
            if (kind.equals("icon")) {
                quest.add("icon", keys.size() > 0 ? keys.get(0) : JsonNull.INSTANCE);
                return;
            }
            JsonObject group = quest.has(kind) ? quest.getAsJsonObject(kind) : new JsonObject();
            quest.add(kind, group);
            group.add(String.valueOf(index), keys);
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
