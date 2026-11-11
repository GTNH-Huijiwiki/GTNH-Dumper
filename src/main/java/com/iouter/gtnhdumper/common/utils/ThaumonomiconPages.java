package com.iouter.gtnhdumper.common.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.glease.tc4tweak.api.infusionrecipe.EnhancedInfusionRecipe;
import net.glease.tc4tweak.api.infusionrecipe.InfusionRecipeExt;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.nbt.NBTBase;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.iouter.gtnhdumper.GTNHDumper;
import com.iouter.gtnhdumper.common.recipe.base.RecipeItem;

import codechicken.nei.NEIServerUtils;
import cpw.mods.fml.common.ObfuscationReflectionHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.InfusionEnchantmentRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumcraft.api.research.ResearchPage;

/**
 * Turns Thaumonomicon pages into plain maps for JSON.
 *
 * <p>
 * Every page is {@code {"type": ..., ...}}. Text keeps Thaumcraft's markup ({@code <BR>
 * }, {@code <LINE>},
 * {@code <IMG>} and the {@code §} format codes) unchanged. Item slots use the same shapes as the recipe export: a
 * {@link RecipeItem}, an array of alternatives, or {@code #oredict}; empty slots are null.
 */
public final class ThaumonomiconPages {

    private ThaumonomiconPages() {}

    public static List<Map<String, Object>> pages(ResearchPage[] pages, ThaumonomiconAssets assets) {
        if (pages == null) return null;
        List<Map<String, Object>> result = new ArrayList<>(pages.length);
        for (ResearchPage page : pages) {
            if (page == null) continue;
            try {
                result.add(page(page, assets));
            } catch (RuntimeException | LinkageError e) {
                // One odd addon recipe must not lose the whole book.
                GTNHDumper.LOG.warn("Thaumonomicon page of type {} could not be exported", page.type, e);
                Map<String, Object> failed = new LinkedHashMap<>();
                failed.put("type", "unsupported");
                failed.put("page_type", String.valueOf(page.type));
                failed.put("error", e.toString());
                result.add(failed);
            }
        }
        return result;
    }

    private static Map<String, Object> page(ResearchPage page, ThaumonomiconAssets assets) {
        Map<String, Object> map = new LinkedHashMap<>();
        switch (page.type) {
            case TEXT:
                map.put("type", "text");
                text(map, page, assets);
                break;
            case TEXT_CONCEALED:
                map.put("type", "text_concealed");
                map.put("research", page.research);
                text(map, page, assets);
                break;
            case IMAGE:
                map.put("type", "image");
                map.put("image", assets.add(page.image));
                text(map, page, assets);
                break;
            case ASPECTS:
                map.put("type", "aspects");
                map.put("aspects", aspects(page.aspects));
                break;
            case NORMAL_CRAFTING:
                map.put("type", "normal_crafting");
                map.put("recipes", recipes(page.recipe, ThaumonomiconPages::normalRecipe));
                break;
            case ARCANE_CRAFTING:
                map.put("type", "arcane_crafting");
                map.put("recipes", recipes(page.recipe, ThaumonomiconPages::arcaneRecipe));
                break;
            case CRUCIBLE_CRAFTING:
                map.put("type", "crucible_crafting");
                map.put("recipes", recipes(page.recipe, ThaumonomiconPages::crucibleRecipe));
                break;
            case INFUSION_CRAFTING:
                map.put("type", "infusion_crafting");
                map.put("recipes", recipes(page.recipe, ThaumonomiconPages::infusionRecipe));
                break;
            case INFUSION_ENCHANTMENT:
                map.put("type", "infusion_enchantment");
                map.put("recipes", recipes(page.recipe, ThaumonomiconPages::enchantmentRecipe));
                break;
            case COMPOUND_CRAFTING:
                map.put("type", "compound_crafting");
                compoundRecipe(map, page.recipe);
                break;
            case SMELTING:
                map.put("type", "smelting");
                smeltingRecipe(map, page);
                break;
            default:
                map.put("type", "unsupported");
                map.put("page_type", String.valueOf(page.type));
        }
        return map;
    }

    private static void text(Map<String, Object> map, ResearchPage page, ThaumonomiconAssets assets) {
        String translated = page.getTranslatedText();
        if (page.text != null && !page.text.equals(translated)) map.put("text_key", page.text);
        map.put("text", translated);
        assets.addTextImages(translated);
    }

    /** A page holds one recipe or an array of alternatives that the book cycles through. */
    private static List<Map<String, Object>> recipes(Object recipe,
        java.util.function.Function<Object, Map<String, Object>> convert) {
        List<Object> list;
        if (recipe instanceof Object[]) list = Arrays.asList((Object[]) recipe);
        else list = recipe == null ? Collections.emptyList() : Collections.singletonList(recipe);
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o == null) continue;
            result.add(convert.apply(o));
        }
        return result;
    }

    // ---- crafting ----

    private static Map<String, Object> normalRecipe(Object o) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (o instanceof ShapedRecipes) {
            ShapedRecipes r = (ShapedRecipes) o;
            map.put("shape", "shaped");
            map.put("width", r.recipeWidth);
            map.put("height", r.recipeHeight);
            map.put("inputs", items(r.recipeItems));
        } else if (o instanceof ShapedOreRecipe) {
            ShapedOreRecipe r = (ShapedOreRecipe) o;
            map.put("shape", "shaped");
            map.put("width", ObfuscationReflectionHelper.getPrivateValue(ShapedOreRecipe.class, r, "width"));
            map.put("height", ObfuscationReflectionHelper.getPrivateValue(ShapedOreRecipe.class, r, "height"));
            map.put("inputs", items(r.getInput()));
        } else if (o instanceof ShapelessRecipes) {
            map.put("shape", "shapeless");
            map.put("inputs", items(((ShapelessRecipes) o).recipeItems.toArray()));
        } else if (o instanceof ShapelessOreRecipe) {
            map.put("shape", "shapeless");
            map.put(
                "inputs",
                items(
                    ((ShapelessOreRecipe) o).getInput()
                        .toArray()));
        } else {
            // The book itself only draws the four vanilla/Forge types above.
            map.put("shape", "unsupported");
            map.put(
                "class",
                o.getClass()
                    .getName());
        }
        if (o instanceof IRecipe) map.put("output", stack(((IRecipe) o).getRecipeOutput()));
        return map;
    }

    private static Map<String, Object> arcaneRecipe(Object o) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (o instanceof ShapedArcaneRecipe) {
            ShapedArcaneRecipe r = (ShapedArcaneRecipe) o;
            map.put("shape", "shaped");
            map.put("width", r.width);
            map.put("height", r.height);
            map.put("inputs", items(r.getInput()));
        } else if (o instanceof ShapelessArcaneRecipe) {
            map.put("shape", "shapeless");
            map.put(
                "inputs",
                items(
                    ((ShapelessArcaneRecipe) o).getInput()
                        .toArray()));
        } else {
            map.put("shape", "unsupported");
            map.put(
                "class",
                o.getClass()
                    .getName());
        }
        if (o instanceof IArcaneRecipe) {
            IArcaneRecipe r = (IArcaneRecipe) o;
            map.put("research", r.getResearch());
            map.put("aspects", aspects(r.getAspects()));
            map.put("output", stack(r.getRecipeOutput()));
        }
        return map;
    }

    private static Map<String, Object> crucibleRecipe(Object o) {
        CrucibleRecipe r = (CrucibleRecipe) o;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("research", r.key);
        map.put("catalyst", item(r.catalyst));
        map.put("aspects", aspects(r.aspects));
        map.put("output", stack(r.getRecipeOutput()));
        return map;
    }

    private static Map<String, Object> infusionRecipe(Object o) {
        InfusionRecipe r = (InfusionRecipe) o;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("research", r.getResearch());
        map.put("instability", r.getInstability());
        map.put("aspects", aspects(r.getAspects()));
        // tc4tweaks resolves ore-dictionary and fuzzy ingredients; plain Thaumcraft only has the concrete stacks.
        Object central = null;
        List<Object> components = null;
        try {
            Tc4Tweaks.Resolved resolved = Tc4Tweaks.resolve(r);
            central = resolved.central;
            components = resolved.components;
        } catch (RuntimeException | LinkageError e) {
            GTNHDumper.LOG.debug("tc4tweaks infusion conversion unavailable, using plain stacks", e);
        }
        if (components == null) {
            central = stack(r.getRecipeInput());
            components = items(r.getComponents());
        }
        map.put("central", central);
        map.put("components", components);
        map.put("output", infusionOutput(r));
        return map;
    }

    /** Result is an ItemStack, or {nbtKey, NBTBase} meaning "the central item with this tag added". */
    private static Object infusionOutput(InfusionRecipe r) {
        Object out = r.getRecipeOutput();
        if (out instanceof ItemStack) return stack((ItemStack) out);
        if (out instanceof Object[] && r.getRecipeInput() != null) {
            Object[] pair = (Object[]) out;
            ItemStack result = r.getRecipeInput()
                .copy();
            result.setTagInfo((String) pair[0], (NBTBase) pair[1]);
            return stack(result);
        }
        return null;
    }

    private static Map<String, Object> enchantmentRecipe(Object o) {
        InfusionEnchantmentRecipe r = (InfusionEnchantmentRecipe) o;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("research", r.research);
        map.put("enchantment_id", r.enchantment.effectId);
        map.put("enchantment", StatCollector.translateToLocal(r.enchantment.getName()));
        map.put("max_level", r.enchantment.getMaxLevel());
        map.put("instability", r.instability);
        map.put("xp_per_level", r.recipeXP);
        map.put("aspects_per_level", aspects(r.aspects));
        map.put("components", items(r.components));
        return map;
    }

    /**
     * Construct page: {AspectList cost, dx, dy, dz, List<ItemStack> blocks}. The list is ordered layer by layer
     * (y), then z from high to low, then x from high to low, which is the order the book draws it in.
     */
    private static void compoundRecipe(Map<String, Object> map, Object recipe) {
        List<?> r = (List<?>) recipe;
        int dx = (Integer) r.get(1);
        int dy = (Integer) r.get(2);
        int dz = (Integer) r.get(3);
        map.put("aspects", aspects((AspectList) r.get(0)));
        map.put("size_x", dx);
        map.put("size_y", dy);
        map.put("size_z", dz);
        List<?> source = (List<?>) r.get(4);
        List<Map<String, Object>> blocks = new ArrayList<>();
        int count = 0;
        for (int y = 0; y < dy; y++) {
            for (int z = dz - 1; z >= 0; z--) {
                for (int x = dx - 1; x >= 0; x--) {
                    Object slot = count < source.size() ? source.get(count) : null;
                    count++;
                    Object item = item(slot);
                    if (item == null) continue;
                    Map<String, Object> block = new LinkedHashMap<>();
                    block.put("x", x);
                    block.put("y", y);
                    block.put("z", z);
                    block.put("item", item);
                    blocks.add(block);
                }
            }
        }
        map.put("blocks", blocks);
    }

    private static void smeltingRecipe(Map<String, Object> map, ResearchPage page) {
        ItemStack in = (ItemStack) page.recipe;
        map.put("input", stack(in));
        ItemStack out = page.recipeOutput;
        if (out == null && in != null) out = FurnaceRecipes.smelting()
            .getSmeltingResult(in);
        map.put("output", stack(out));
    }

    // ---- shared helpers ----

    /** Aspect tag ({@code ignis}) to amount, in the list's own order. */
    public static Map<String, Integer> aspects(AspectList list) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (list == null || list.aspects == null) return map;
        for (Map.Entry<Aspect, Integer> entry : list.aspects.entrySet()) {
            if (entry.getKey() == null) continue;
            map.put(
                entry.getKey()
                    .getTag(),
                entry.getValue());
        }
        return map;
    }

    public static RecipeItem stack(ItemStack stack) {
        return stack == null ? null : new RecipeItem(stack);
    }

    public static Object item(Object o) {
        if (o == null) return null;
        // Construct pages list ore-dictionary names as plain strings, which NEI's extractor rejects.
        if (o instanceof String) o = OreDictionary.getOres((String) o)
            .toArray(new ItemStack[0]);
        ItemStack[] stacks = NEIServerUtils.extractRecipeItems(o);
        if (stacks == null || stacks.length == 0) return null;
        return RecipeUtil.getRecipeItems(stacks);
    }

    public static List<Object> items(Object[] slots) {
        List<Object> list = new ArrayList<>(slots == null ? 0 : slots.length);
        if (slots == null) return list;
        for (Object slot : slots) list.add(item(slot));
        return list;
    }

    /** Isolated so a missing tc4tweaks fails here and nowhere else. */
    private static final class Tc4Tweaks {

        static final class Resolved {

            Object central;
            List<Object> components;
        }

        static Resolved resolve(InfusionRecipe recipe) {
            EnhancedInfusionRecipe converted = InfusionRecipeExt.get()
                .convert(recipe);
            Resolved resolved = new Resolved();
            resolved.central = item(
                converted.getCentral()
                    .getRepresentativeStacks());
            resolved.components = new ArrayList<>();
            converted.getComponentsExt()
                .stream()
                .map(c -> item(c.getRepresentativeStacks()))
                .filter(Objects::nonNull)
                .forEach(resolved.components::add);
            return resolved;
        }
    }
}
