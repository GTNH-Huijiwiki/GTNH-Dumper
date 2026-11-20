package com.iouter.gtnhdumper.common.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.cropsnh.api.ICropCard;
import com.gtnewhorizon.cropsnh.compatibility.NEI.NEICropsNHCropHandler;
import com.gtnewhorizon.cropsnh.farming.registries.CropRegistry;
import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.RecipeUtil;
import com.iouter.gtnhdumper.common.utils.Utils;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

public class CropsNHHandlerRecipe extends BaseHandlerRecipe {

    public CropsNHHandlerRecipe(NEICropsNHCropHandler handler) {
        super(handler);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        List<Map<String, Object>> recipes = new ArrayList<>();
        if (!(handler instanceof NEICropsNHCropHandler recipeHandler)) {
            return null;
        }
        recipeHandler.loadCraftingRecipes(recipeHandler.getOverlayIdentifier(), (Object) null);
        for (TemplateRecipeHandler.CachedRecipe recipe : recipeHandler.arecipes) {
            try {
                ItemStack seed = recipe.getIngredients()
                    .get(0).items[0];
                ICropCard cropCard = CropRegistry.instance.get(seed);
                if (!selection.matchesItems(
                    cropCard.getDropTable()
                        .keySet()))
                    continue;
                ArrayList<Object> otherItems = getRecipeItems(cropCard);
                if (otherItems.isEmpty()) otherItems = null;
                Map<String, Object> data = new java.util.LinkedHashMap<>();
                data.put("inputItems", Utils.getRecipeItems(recipe.getIngredients()));
                data.put("outputItems", otherItems);
                try {
                    data.put(
                        "additionalInformation",
                        sortedBiomeLine(cropCard, (List<?>) NEIDisplayHandlerRecipe.field(recipe, "textLines")));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot read CropsNH crop information", e);
                }
                recipes.add(data);

            } catch (RuntimeException | LinkageError e) {
                if (!RecipeItemVariants.isCollecting()) throw e;
                RecipeItemVariants.reportFailure("CropsNHHandlerRecipe.java entry", e);
            }
        }
        return recipes;
    }

    /** 喜好群系来自 HashSet，NEI 文本里的顺序每次启动不同；同一行改为按群系类型的声明顺序列出。 */
    private static List<Object> sortedBiomeLine(ICropCard crop, List<?> lines) {
        String key = "cropsnh_nei.crops.likes";
        java.util.function.Function<java.util.stream.Stream<net.minecraftforge.common.BiomeDictionary.Type>, String> line = types -> net.minecraft.util.StatCollector
            .translateToLocalFormatted(
                key,
                types
                    .map(
                        type -> net.minecraft.util.StatCollector.translateToLocal(
                            "cropsnh_tooltip.biomeTag." + type.name()
                                .toUpperCase()))
                    .collect(java.util.stream.Collectors.joining(", ")));
        String shown = line.apply(
            crop.getLikedBiomeTags()
                .stream());
        String sorted = line.apply(
            crop.getLikedBiomeTags()
                .stream()
                .sorted());
        List<Object> result = new ArrayList<>(lines);
        result.replaceAll(text -> shown.equals(text) ? sorted : text);
        return result;
    }

    private static ArrayList<Object> getRecipeItems(ICropCard cropCard) {
        ArrayList<Object> items = new ArrayList<>();
        for (Map.Entry<ItemStack, Integer> drop : cropCard.getDropTable()
            .entrySet()
            .stream()
            .sorted(
                java.util.Comparator.comparing((Map.Entry<ItemStack, Integer> entry) -> entry.getValue())
                    .reversed()
                    .thenComparing(Map.Entry::getKey, com.iouter.gtnhdumper.common.utils.StableOrder.ITEMS))
            .collect(java.util.stream.Collectors.toList())) {
            items.add(RecipeUtil.getRecipeItems(new ItemStack[] { drop.getKey() }, drop.getValue()));
        }
        return items;
    }
}
