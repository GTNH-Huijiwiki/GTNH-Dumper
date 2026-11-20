package com.iouter.gtnhdumper.common.utils;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/**
 * Object aspects per item, computed once per game session.
 * <p>
 * Thaumcraft caches generated aspects only when generation succeeds. Items without aspects therefore repeat a scan of
 * every registered object tag and a recipe search on each lookup. The item-variant scan, the recipe export and the item
 * panel all ask for every NEI item, so a full export used to pay that cost three times.
 */
public final class AspectTagsCache {

    private static final Object NONE = new Object();
    private static final Map<RecipeItemVariants.Key, Object> OBJECT_TAGS = new HashMap<>();

    private AspectTagsCache() {}

    /** Same result as {@code ThaumcraftCraftingManager.getObjectTags}; callers must not modify it. */
    public static synchronized AspectList objectTags(ItemStack stack) {
        RecipeItemVariants.Key key = new RecipeItemVariants.Key(stack);
        Object cached = OBJECT_TAGS.get(key);
        if (cached == null) {
            AspectList tags = ThaumcraftCraftingManager.getObjectTags(stack);
            cached = tags == null ? NONE : tags;
            OBJECT_TAGS.put(key, cached);
        }
        return cached == NONE ? null : (AspectList) cached;
    }
}
