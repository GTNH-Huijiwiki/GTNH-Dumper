package com.iouter.gtnhdumper.common.recipe;

import java.util.ArrayList;
import java.util.List;

import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.BaseRecipe;
import com.iouter.gtnhdumper.common.utils.Utils;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

public class VendingMachineHandlerRecipe extends BaseHandlerRecipe {

    public VendingMachineHandlerRecipe(IRecipeHandler handler) {
        super(handler);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        TemplateRecipeHandler recipeHandler = (TemplateRecipeHandler) handler;
        recipeHandler.loadCraftingRecipes(recipeHandler.getOverlayIdentifier(), (Object) null);
        List<BaseRecipe> recipes = new ArrayList<>();
        for (TemplateRecipeHandler.CachedRecipe recipe : recipeHandler.arecipes) {
            if (selection.isActive() && !selection.matchesStacks(recipe.getOtherStacks())) continue;
            // Vending Machine represents a trade with no primary result. Its actual outputs are exposed through
            // getOtherStacks(), unlike conventional NEI recipes handled by GeneralHandlerRecipe.
            ArrayList<Object> outputItems = Utils.getRecipeItems(recipe.getOtherStacks());
            if (outputItems.isEmpty()) outputItems = null;
            recipes.add(new BaseRecipe(Utils.getRecipeItems(recipe.getIngredients()), null, outputItems, null, null));
        }
        return recipes;
    }
}
