package com.iouter.gtnhdumper.common.utils;

import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;

/** Cheap, export-local output selection before input conversion, text extraction or serialization. */
public final class RecipeSelection {

    private final boolean active;
    private final Predicate<ItemStack> owner;
    private final Map<Item, Map<Integer, Boolean>> ownership = new IdentityHashMap<>();

    public RecipeSelection(boolean active, Predicate<ItemStack> owner) {
        this.active = active;
        this.owner = owner;
    }

    public static RecipeSelection current() {
        if (RecipeItemVariants.isCollecting()) return new RecipeSelection(false, null);
        ModFilter filter = ModFilter.current();
        return new RecipeSelection(filter.isActive(), filter::matchesItem);
    }

    public boolean isActive() {
        return active;
    }

    public boolean matches(ItemStack item) {
        if (!active) return true;
        if (item == null || item.getItem() == null) return false;
        // Existing ownership rules depend on item + metadata, never amount or NBT.
        return ownership.computeIfAbsent(item.getItem(), ignored -> new HashMap<>())
            .computeIfAbsent(item.getItemDamage(), ignored -> owner.test(item));
    }

    public boolean matchesItems(Iterable<ItemStack> items) {
        if (!active) return true;
        if (items != null) for (ItemStack item : items) if (matches(item)) return true;
        return false;
    }

    public boolean matchesItems(ItemStack[] items) {
        if (!active) return true;
        if (items != null) for (ItemStack item : items) if (matches(item)) return true;
        return false;
    }

    public boolean matchesStack(PositionedStack stack) {
        return !active || stack != null && matchesItems(stack.items);
    }

    public boolean matchesStacks(Iterable<? extends PositionedStack> stacks) {
        if (!active) return true;
        if (stacks != null) for (PositionedStack stack : stacks) if (matchesStack(stack)) return true;
        return false;
    }

    /** Retain original indexes; pruning arecipes would break handler-specific text/tank lookups. */
    public static <T> BitSet indexes(List<T> recipes, Predicate<T> matches) {
        BitSet selected = new BitSet(recipes.size());
        for (int i = 0; i < recipes.size(); i++) if (matches.test(recipes.get(i))) selected.set(i);
        return selected;
    }

    public <T> BitSet selectIndexes(List<T> recipes, Predicate<T> matches) {
        if (active) return indexes(recipes, matches);
        BitSet all = new BitSet(recipes.size());
        all.set(0, recipes.size());
        return all;
    }
}
