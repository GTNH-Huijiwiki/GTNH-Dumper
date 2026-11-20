package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

/** Loads the recipes behind a registered NEI handler, for handlers that only answer specific queries. */
public final class NEIHandlerLoader {

    private NEIHandlerLoader() {}

    /** 按处理器的实际查询标识加载；避免工作台仅导出空列表。 */
    public static IRecipeHandler loadHandler(IRecipeHandler registered) throws Exception {
        if (!(registered instanceof TemplateRecipeHandler template)) {
            String className = registered.getClass()
                .getName();
            if (!className.startsWith("com.github.dcysteine.neicustomdiagram.")) {
                throw new IllegalArgumentException(
                    "Item-query/interactive view: registered instance is not initialized; requires a concrete query adapter");
            }
            // DiagramGroup 等实现标准 ICraftingHandler，按 groupId 或 all 加载整组。
            if (registered instanceof ICraftingHandler crafting) {
                Set<String> diagramIds = new LinkedHashSet<>();
                diagramIds.add(registered.getHandlerId());
                diagramIds.add(registered.getOverlayIdentifier());
                diagramIds.add("all");
                // 末影储存概览以专用 global/personal 标识加载；空存档也返回真实的无数据界面。
                if (className.endsWith(".CustomDiagramGroup")) {
                    Field behaviors = registered.getClass()
                        .getDeclaredField("customBehaviorMap");
                    behaviors.setAccessible(true);
                    for (Object key : ((Map<?, ?>) behaviors.get(registered)).keySet()) {
                        diagramIds.add((String) key);
                    }
                }
                for (String id : diagramIds) {
                    if (id == null || id.isEmpty()) continue;
                    IRecipeHandler loaded = crafting.getRecipeHandler(id);
                    if (loaded.numRecipes() > 0) return loaded;
                }
            }
            throw new IllegalArgumentException(
                "Identifier/all loaders returned no views; this handler needs an item query or interactive state");
        }
        if (template.getClass()
            .getName()
            .equals("galaxyspace.core.nei.CustomRockRecipeHandler")) {
            TemplateRecipeHandler candidate = NEIHandlerInstances.create(template);
            candidate.loadCraftingRecipes(
                (String) template.getClass()
                    .getField("recipeId")
                    .get(template));
            return candidate;
        }
        if (template.getClass()
            .getName()
            .equals("com.science.gtnl.client.nei.NuclearReactorGuideHandler")) {
            // This guide has no crafting/all query; its usage query is keyed by the reactor controller.
            Object controller = Class.forName("com.science.gtnl.utils.enums.GTNLItemList")
                .getField("NuclearReactor")
                .get(null);
            TemplateRecipeHandler candidate = template.newInstance();
            candidate.loadUsageRecipes(((gregtech.api.interfaces.IItemContainer) controller).get(1));
            return candidate;
        }
        IRecipeHandler loaded = template.getAllRecipeHandler();
        if (loaded.numRecipes() > 0) return loaded;
        Set<String> ids = new LinkedHashSet<>();
        ids.add(template.getOverlayIdentifier());
        ids.add(template.getHandlerId());
        for (String name : new String[] { "getRecipeId", "getRecipeID", "getRecipeIdentifier" }) {
            for (Class<?> type = template.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Method getter = type.getDeclaredMethod(name);
                    if (getter.getReturnType() != String.class) continue;
                    getter.setAccessible(true);
                    ids.add((String) getter.invoke(template));
                    break;
                } catch (NoSuchMethodException ignored) {
                    // Try the superclass that owns this identifier accessor.
                }
            }
        }
        for (String id : ids) {
            if (id == null || id.isEmpty()) continue;
            TemplateRecipeHandler candidate = template.newInstance();
            candidate.loadCraftingRecipes(id);
            if (candidate.numRecipes() > 0) return candidate;
        }
        // 部分工作台处理器只实现物品查询。用同模组注册配方的真实输出触发加载。
        String name = template.getClass()
            .getName();
        String recipePrefix = name.startsWith("mrtjp.projectred.") ? "mrtjp.projectred."
            : name.startsWith("gcewing.projectblue.") ? "gcewing.projectblue."
                : name.startsWith("ic2.") && name.contains("Shapeless") ? "ic2."
                    : name.startsWith("com.bymarcin.openglasses.") ? "com.bymarcin.openglasses." : null;
        if (recipePrefix != null) {
            for (Object value : CraftingManager.getInstance()
                .getRecipeList()) {
                if (!(value instanceof IRecipe recipe) || !value.getClass()
                    .getName()
                    .startsWith(recipePrefix)) continue;
                ItemStack output = recipe.getRecipeOutput();
                if (output == null || output.getItem() == null) continue;
                TemplateRecipeHandler candidate = template.newInstance();
                candidate.loadCraftingRecipes(output);
                if (candidate.numRecipes() > 0) return candidate;
            }
        }
        return loaded;
    }
}
