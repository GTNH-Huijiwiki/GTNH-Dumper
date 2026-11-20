package com.iouter.gtnhdumper.common.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import codechicken.nei.NEIClientUtils;
import cpw.mods.fml.common.registry.GameData;
import gregtech.api.items.CircuitComponentFakeItem;
import gregtech.common.tileentities.machines.multi.nanochip.util.CircuitComponent;

public class AllItemStacks {

    private static List<ItemStack> allItemStacks;
    // getSubItems may return stacks backed by a different, unregistered Item. Keep the
    // registered source that produced each exact stack so filtered exports retain its owner.
    private static Map<ItemStack, String> registryNames;

    private AllItemStacks() {}

    static void invalidateRecipeScope() {
        allItemStacks = null;
        registryNames = null;
    }

    public static List<ItemStack> getAllItemStacks() {
        List<ItemStack> result = allItemStacks;
        if (result == null) {
            int creativeFailures = 0;
            List<ItemStack> itemStacks = new ArrayList<>();
            Map<ItemStack, String> itemRegistryNames = new IdentityHashMap<>();
            for (Object temp : GameData.getItemRegistry()) {
                if (!(temp instanceof Item item)) continue;
                String registryName = GameData.getItemRegistry()
                    .getNameForObject(item);
                List<ItemStack> sub = new ArrayList<>();
                try {
                    if (item == CircuitComponentFakeItem.INSTANCE) {
                        for (CircuitComponent component : CircuitComponent.VALUES) {
                            sub.add(component.getFakeStack(1));
                        }
                    } else {
                        item.getSubItems(item, CreativeTabs.tabAllSearch, sub);
                    }
                } catch (Exception | LinkageError e) {
                    creativeFailures++;
                    RecipeItemVariants.reportFailure("creative items: " + registryName, e);
                }
                for (ItemStack itemStack : sub) {
                    if (Utils.isStackInvalid(itemStack)) {
                        continue;
                    }
                    itemStacks.add(itemStack);
                    itemRegistryNames.put(itemStack, registryName);
                }
            }
            Set<RecipeItemVariants.Key> known = new HashSet<>();
            for (ItemStack stack : itemStacks) known.add(new RecipeItemVariants.Key(stack));
            for (ItemStack stack : RecipeItemVariants.get()) {
                if (known.add(new RecipeItemVariants.Key(stack))) {
                    itemStacks.add(stack);
                    itemRegistryNames.put(
                        stack,
                        GameData.getItemRegistry()
                            .getNameForObject(stack.getItem()));
                }
            }
            // 配方扫描补充的变体按扫描顺序追加，少数物品（刷怪笼、药水等）的子物品也来自 HashMap，
            // 每次启动顺序不同。按 StableOrder.ITEMS 排列。
            itemStacks.sort(StableOrder.ITEMS);
            registryNames = itemRegistryNames;
            result = itemStacks;
            if (creativeFailures == 0 && RecipeItemVariants.isComplete()) allItemStacks = result;
            if (creativeFailures > 0) {
                NEIClientUtils.printChatMessage(
                    new ChatComponentText(
                        "Creative item enumeration incomplete: " + creativeFailures
                            + " failures. See log; the next item/icon export will retry."));
            }
        }
        RecipeItemVariants.warnIfIncomplete();
        return result;
    }

    public static List<ItemStack> getFilteredItemStacks() {
        ModFilter filter = ModFilter.current();
        if (!filter.isActive()) return getAllItemStacks();
        RecipeItemVariants.get();
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : getAllItemStacks()) {
            if (filter.matchesItem(stack, registryNames.get(stack)) || RecipeItemVariants.isRecipeDependency(stack))
                result.add(stack);
        }
        return result;
    }
}
