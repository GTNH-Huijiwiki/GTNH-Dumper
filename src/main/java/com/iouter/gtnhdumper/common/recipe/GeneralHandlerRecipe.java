package com.iouter.gtnhdumper.common.recipe;

import java.util.ArrayList;
import java.util.List;

import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.utils.RecipeCoverage;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.Utils;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

public class GeneralHandlerRecipe extends BaseHandlerRecipe {

    public GeneralHandlerRecipe(IRecipeHandler handler) {
        super(handler);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        if (handler.getClass()
            .getName()
            .equals("tconstruct.plugins.nei.RecipeHandlerMelting")) {
            if (selection.isActive()) return java.util.Collections.emptyList(); // Fluid outputs only.
            try {
                return com.iouter.gtnhdumper.common.utils.TConstructMeltingRecipes.read();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read registered smeltery recipes", e);
            }
        }
        List<java.util.Map<String, Object>> recipes = new ArrayList<>();
        if (!(handler instanceof TemplateRecipeHandler recipeHandler)) {
            RecipeCoverage.unsupported(
                handler.getHandlerId(),
                "No adapter for non-TemplateRecipeHandler; this view is excluded from recipe and variant coverage");
            return null;
        }
        try {
            TemplateRecipeHandler loaded = (TemplateRecipeHandler) com.iouter.gtnhdumper.common.dumper.NEILayoutDumper
                .loadHandler(recipeHandler);
            // 固定显示状态；依赖输入颜色的旗帜在下方单独展开成配对。
            loaded.cycleticks = 0;
            if (loaded.getClass()
                .getName()
                .equals("ganymedes01.etfuturum.compat.nei.BannerPatternHandler")) {
                return com.iouter.gtnhdumper.common.utils.BannerPatternRecipes.read(loaded, selection);
            }
            recipeHandler.arecipes.clear();
            recipeHandler.arecipes.addAll(loaded.arecipes);
        } catch (Exception e) {
            RecipeItemVariants.reportFailure(
                handler.getClass()
                    .getName() + " loading",
                e);
            return null;
        }
        java.util.BitSet selected = selection.selectIndexes(
            recipeHandler.arecipes,
            recipe -> com.iouter.gtnhdumper.common.utils.NEIRecipeText.matchesOutputs(
                handler.getClass()
                    .getName(),
                recipe,
                selection));
        java.util.Map<Integer, java.util.Map<String, Object>> tanks = com.iouter.gtnhdumper.common.utils.RecipeTankData
            .read(recipeHandler, selected::get);
        for (int recipeIndex = selected.nextSetBit(0); recipeIndex
            >= 0; recipeIndex = selected.nextSetBit(recipeIndex + 1)) {
            TemplateRecipeHandler.CachedRecipe recipe = recipeHandler.arecipes.get(recipeIndex);
            ArrayList<Object> otherItems;
            try {
                otherItems = Utils.getRecipeItems(recipe.getOtherStacks());
                if (otherItems.isEmpty()) otherItems = null;
            } catch (Exception e) {
                otherItems = null;
            }
            java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("inputItems", Utils.getRecipeItems(recipe.getIngredients()));
            data.put("outputItems", Utils.getRecipeItems(recipe.getResult()));
            data.put("otherItems", otherItems);
            if (tanks.containsKey(recipeIndex)) data.putAll(tanks.get(recipeIndex));
            com.iouter.gtnhdumper.common.utils.NEIRecipeText.enrich(recipeHandler, recipe, recipeIndex, data);
            recipes.add(data);
        }
        return recipes;
    }
}
