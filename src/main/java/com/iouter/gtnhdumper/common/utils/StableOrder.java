package com.iouter.gtnhdumper.common.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 导出顺序的稳定键。游戏里不少集合按对象哈希或注册时机迭代（矿辞成员、HashMap 配方表、产物表），
 * 每次启动顺序不同；这里只给“顺序本身没有含义”的列表规定确定的顺序，不增删内容。
 */
public final class StableOrder {

    private StableOrder() {}

    /**
     * 物品的稳定次序：所属模组的加载顺序（原版在前）、注册名、damage、NBT 文本、数量。
     * 不用数字 ID：按 HashMap 注册物品的模组（如 ironchestminecarts）每次分到的 ID 不同。
     */
    public static final Comparator<ItemStack> ITEMS = Comparator.comparingInt((ItemStack stack) -> modRank(item(stack)))
        .thenComparing(stack -> registryName(item(stack)))
        .thenComparingInt(
            stack -> stack == null || stack.getItem() == null ? 0
                : codechicken.lib.inventory.InventoryUtils.actualDamage(stack))
        .thenComparing(
            stack -> stack == null || stack.stackTagCompound == null ? "" : stack.stackTagCompound.toString())
        .thenComparingInt(stack -> stack == null ? 0 : stack.stackSize);

    private static final Map<Item, String> NAMES = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile Map<String, Integer> modOrder;

    private static Item item(ItemStack stack) {
        return stack == null ? null : stack.getItem();
    }

    private static String registryName(Item item) {
        if (item == null) return "￿";
        return NAMES.computeIfAbsent(item, key -> {
            String name = Item.itemRegistry.getNameForObject(key);
            return name == null ? "￿" + key.getClass()
                .getName() : name;
        });
    }

    private static int modRank(Item item) {
        if (item == null) return Integer.MAX_VALUE;
        String name = registryName(item);
        int colon = name.indexOf(':');
        String mod = colon < 0 ? "minecraft" : name.substring(0, colon);
        if (mod.equals("minecraft")) return -1;
        Map<String, Integer> order = modOrder;
        if (order == null) {
            order = new java.util.HashMap<>();
            int index = 0;
            for (cpw.mods.fml.common.ModContainer container : cpw.mods.fml.common.Loader.instance()
                .getActiveModList()) order.putIfAbsent(container.getModId(), index++);
            modOrder = order;
        }
        return order.getOrDefault(mod, Integer.MAX_VALUE - 1);
    }

    /** 同一槽位的候选物品；keepFirst 保留配方自身登记的那一项在首位（GT 的统一化物品）。 */
    public static ItemStack[] candidates(ItemStack[] stacks, boolean keepFirst) {
        if (stacks == null || stacks.length < 2) return stacks;
        ItemStack[] sorted = stacks.clone();
        Arrays.sort(sorted, keepFirst ? 1 : 0, sorted.length, ITEMS);
        return sorted;
    }

    /** 配方列表来自 HashMap/HashSet 或按哈希顺序注册的处理器；按配方 JSON 排序。 */
    private static final Set<String> UNORDERED_RECIPE_SOURCES = new java.util.HashSet<>(
        Arrays.asList(
            "WayofTime.alchemicalWizardry.client.nei.NEICalcinatorHandler",
            "binnie.extratrees.nei.NEIHandlerLumbermill",
            "binnie.genetics.nei.AcclimatiserRecipeHandler",
            "forestry.factory.recipes.nei.NEIHandlerBottler",
            "forestry.factory.recipes.nei.NEIHandlerCarpenter",
            "forestry.factory.recipes.nei.NEIHandlerCentrifuge",
            "forestry.factory.recipes.nei.NEIHandlerFabricator",
            "forestry.factory.recipes.nei.NEIHandlerFermenter",
            "forestry.factory.recipes.nei.NEIHandlerMoistener",
            "forestry.factory.recipes.nei.NEIHandlerSqueezer",
            "forestry.factory.recipes.nei.NEIHandlerStill",
            "de.katzenpapst.amunra.nei.recipehandler.ARNasaWorkbenchShuttle",
            "galaxyspace.core.nei.rocket.RocketT1RecipeHandler",
            "galaxyspace.core.nei.rocket.RocketT2RecipeHandler",
            "galaxyspace.core.nei.rocket.RocketT3RecipeHandler",
            "galaxyspace.core.nei.rocket.RocketT4RecipeHandler",
            "galaxyspace.core.nei.rocket.RocketT5RecipeHandler",
            "galaxyspace.core.nei.rocket.RocketT6RecipeHandler",
            "galaxyspace.core.nei.rocket.RocketT7RecipeHandler",
            "galaxyspace.core.nei.rocket.RocketT8RecipeHandler",
            "micdoodle8.mods.galacticraft.core.nei.BuggyRecipeHandler",
            "micdoodle8.mods.galacticraft.core.nei.CircuitFabricatorRecipeHandler",
            "micdoodle8.mods.galacticraft.core.nei.RocketT1RecipeHandler",
            "micdoodle8.mods.galacticraft.planets.mars.nei.CargoRocketRecipeHandler",
            "micdoodle8.mods.galacticraft.planets.mars.nei.RocketT2RecipeHandler",
            "micdoodle8.mods.galacticraft.planets.asteroids.nei.RocketT3RecipeHandler",
            "ic2.neiIntegration.core.recipehandler.FluidCannerRecipeHandler",
            "ic2.neiIntegration.core.recipehandler.ScrapboxRecipeHandler",
            "ic2.neiIntegration.core.recipehandler.SolidCannerRecipeHandler",
            "tconstruct.plugins.nei.RecipeHandlerToolMaterials",
            "thaumic.tinkerer.client.nei.NEIInfusedSeedRecipeHandler",
            "appeng.integration.modules.NEIHelpers.NEIInscriberRecipeHandler",
            "ganymedes01.etfuturum.compat.nei.BlastFurnaceRecipeHandler",
            "ganymedes01.etfuturum.compat.nei.SmokerRecipeHandler",
            "net.bdew.gendustry.nei.LiquifierHandler",
            "net.bdew.gendustry.nei.MutagenProducerHandler",
            "com.pam.harvestcraft.AnimalTrapRecipeHandler",
            "codechicken.nei.recipe.BrewingRecipeHandler",
            "codechicken.nei.recipe.FurnaceRecipeHandler",
            "codechicken.nei.recipe.RepairRecipeHandler",
            "codechicken.nei.recipe.ShapedRecipeHandler",
            "codechicken.nei.recipe.ShapelessRecipeHandler",
            "com.rwtema.extrautils.nei.MicroBlocksHandler",
            "com.rwtema.extrautils.nei.FMPMicroBlocksHandler",
            "com.gtnewhorizon.cropsnh.compatibility.NEI.NEICropsNHCropstickBreedingHandler"));

    /** 这些处理器的 otherItems/outputItems 是产物表（HashMap），不是机器的固定槽位。 */
    private static final Set<String> UNORDERED_PRODUCT_SOURCES = new java.util.HashSet<>(
        Arrays.asList(
            "forestry.factory.recipes.nei.NEIHandlerCentrifuge",
            "net.bdew.neiaddons.forestry.bees.BeeProduceHandler"));

    public static boolean unorderedRecipes(codechicken.nei.recipe.IRecipeHandler handler) {
        return matches(UNORDERED_RECIPE_SOURCES, handler);
    }

    public static boolean unorderedProducts(codechicken.nei.recipe.IRecipeHandler handler) {
        return matches(UNORDERED_PRODUCT_SOURCES, handler);
    }

    /** 按实现类或处理器 ID（导出文件里的 source）匹配；银河火箭等处理器共用一个实现类。 */
    private static boolean matches(Set<String> sources, codechicken.nei.recipe.IRecipeHandler handler) {
        return sources.contains(
            handler.getClass()
                .getName())
            || sources.contains(handler.getHandlerId());
    }

    /** 产物按概率从高到低，同概率按 JSON 文本；只重排顶层元素。 */
    public static void sortProducts(JsonObject recipe) {
        for (String field : new String[] { "outputItems", "otherItems" }) {
            JsonElement value = recipe.get(field);
            if (value == null || !value.isJsonArray()) continue;
            List<JsonElement> items = new ArrayList<>();
            value.getAsJsonArray()
                .forEach(items::add);
            items.sort(
                Comparator.comparingInt(StableOrder::chance)
                    .reversed()
                    .thenComparing(JsonElement::toString));
            JsonArray sorted = new JsonArray();
            items.forEach(sorted::add);
            recipe.add(field, sorted);
        }
    }

    private static int chance(JsonElement element) {
        if (element != null && element.isJsonObject()
            && element.getAsJsonObject()
                .has("chance"))
            return element.getAsJsonObject()
                .get("chance")
                .getAsInt();
        return 10000;
    }

    /**
     * 先按 primary（如 GT 的 NEI 排序规则）排序，相等时按序列化后的 JSON 文本，结果与原始迭代顺序无关。
     *
     * @return 排序后的下标
     */
    public static Integer[] order(List<?> sources, List<JsonElement> json, Comparator<Object> primary) {
        Integer[] indexes = new Integer[json.size()];
        for (int i = 0; i < indexes.length; i++) indexes[i] = i;
        Map<JsonElement, String> text = new IdentityHashMap<>();
        Comparator<Integer> byJson = Comparator
            .comparing(index -> text.computeIfAbsent(json.get(index), JsonElement::toString));
        Comparator<Integer> comparator = primary == null ? byJson
            : ((Comparator<Integer>) (a, b) -> primary.compare(sources.get(a), sources.get(b))).thenComparing(byJson);
        Arrays.sort(indexes, comparator);
        return indexes;
    }
}
