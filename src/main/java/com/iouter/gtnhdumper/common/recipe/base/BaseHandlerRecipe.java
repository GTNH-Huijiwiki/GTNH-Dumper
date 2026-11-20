package com.iouter.gtnhdumper.common.recipe.base;

import static com.iouter.gtnhdumper.GTNHDumper.GSON;

import java.util.Comparator;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.google.common.base.Objects;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.RecipeSelection;
import com.iouter.gtnhdumper.common.utils.RecipeUtil;
import com.iouter.gtnhdumper.common.utils.StableOrder;

import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeCatalysts;
import codechicken.nei.recipe.TemplateRecipeHandler;

public abstract class BaseHandlerRecipe {

    private final JsonObject json = new JsonObject();
    protected final RecipeSelection selection = RecipeSelection.current();

    public BaseHandlerRecipe(IRecipeHandler handler) {
        if (RecipeItemVariants.isCollecting()) {
            // Decoration failures must not prevent visiting the actual recipe storage.
            try {
                RecipeCatalysts.getRecipeCatalysts(handler)
                    .forEach(RecipeItemVariants::capture);
            } catch (Exception | LinkageError e) {
                RecipeItemVariants.reportFailure(
                    handler.getClass()
                        .getName() + " catalysts",
                    e);
            }
            try {
                String overlay = handler instanceof TemplateRecipeHandler ? handler.getOverlayIdentifier() : null;
                HandlerInfo info = GuiRecipeTab
                    .getHandlerInfo(handler.getHandlerId(), Objects.firstNonNull(overlay, "null"));
                if (info != null) RecipeItemVariants.capture(info.getItemStack());
            } catch (Exception | LinkageError e) {
                RecipeItemVariants.reportFailure(
                    handler.getClass()
                        .getName() + " marked item",
                    e);
            }
            // Some adapters return raw ItemStacks; retain their normal serializer capture path.
            json.add("recipes", serializeRecipes(handler));
            return;
        }
        json.addProperty("name", handler.getRecipeName());
        JsonArray catalysts = new JsonArray();

        RecipeCatalysts.getRecipeCatalysts(handler)
            .forEach(p -> catalysts.add(GSON.toJsonTree(RecipeUtil.getRecipeItems(p))));

        json.add("catalysts", catalysts);

        final String handlerName = handler.getHandlerId();
        final String handlerId = Objects
            .firstNonNull(handler instanceof TemplateRecipeHandler ? handler.getOverlayIdentifier() : null, "null");

        json.addProperty("identifier", handlerId);
        json.addProperty("source", handlerName);
        HandlerInfo info = GuiRecipeTab.getHandlerInfo(handlerName, handlerId);
        final ItemStack markedItemStack = info != null ? info.getItemStack() : null;
        json.add("markedItem", markedItemStack != null ? GSON.toJsonTree(markedItemStack) : GSON.toJsonTree("null"));

        addJsonObject(json, handler);

        json.add("recipes", serializeRecipes(handler));
    }

    private JsonElement serializeRecipes(IRecipeHandler handler) {
        List<?> recipes = getRecipes(handler);
        if (recipes == null) return JsonNull.INSTANCE;
        List<JsonElement> serialized = new java.util.ArrayList<>(recipes.size());
        for (int index = 0; index < recipes.size(); index++) {
            Object recipe = recipes.get(index);
            try {
                serialized.add(GSON.toJsonTree(recipe));
            } catch (StackOverflowError error) {
                // Gson's recursive frames erase the caller from the crash stack; retain the exact source record.
                throw new IllegalStateException(
                    "Recursive recipe serialization: handler=" + handler.getHandlerId()
                        + "; overlay="
                        + (handler instanceof TemplateRecipeHandler ? handler.getOverlayIdentifier() : null)
                        + "; source="
                        + handler.getClass()
                            .getName()
                        + "; recipeIndex="
                        + index
                        + "; recordType="
                        + (recipe == null ? "null"
                            : recipe.getClass()
                                .getName()),
                    error);
            }
        }
        JsonArray result = new JsonArray();
        // 采集物品变体时只关心内容；写文件时再固定顺序。
        if (RecipeItemVariants.isCollecting()) {
            serialized.forEach(result::add);
            return result;
        }
        if (StableOrder.unorderedProducts(handler)) {
            for (JsonElement element : serialized) {
                if (element.isJsonObject()) StableOrder.sortProducts(element.getAsJsonObject());
            }
        }
        Comparator<Object> order = recipeOrder(handler);
        if (order == null) {
            serialized.forEach(result::add);
        } else {
            for (int index : StableOrder.order(recipes, serialized, order == ANY_ORDER ? null : order))
                result.add(serialized.get(index));
        }
        return result;
    }

    /** 源列表没有可依赖的顺序，只按 JSON 文本排序。 */
    protected static final Comparator<Object> ANY_ORDER = (a, b) -> 0;

    /**
     * 配方顺序：null 保留处理器给出的顺序；否则按返回的比较器排序，相等时按 JSON 文本。
     * 用于来源是 HashMap 或按哈希顺序注册的处理器。
     */
    protected Comparator<Object> recipeOrder(IRecipeHandler handler) {
        return StableOrder.unorderedRecipes(handler) ? ANY_ORDER : null;
    }

    public void addJsonObject(JsonObject json, IRecipeHandler handler) {

    }

    public abstract List<?> getRecipes(IRecipeHandler handler);

    public JsonObject build() {
        return json;
    }
}
