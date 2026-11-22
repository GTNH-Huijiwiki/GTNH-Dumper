package com.iouter.gtnhdumper.common.recipe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;

import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.RecipeItem;

import codechicken.nei.recipe.IRecipeHandler;
import cpw.mods.fml.common.Loader;

/**
 * NEI Custom Diagram 的“世界生成战利品”：每个箱子战利品表导出为一条配方，没有输入，输出为带概率的物品。
 * <p>
 * 数据与该图表相同：Forge 的 {@link ChestGenHooks} 注册表（TooMuchLoot 改写的就是它），
 * 再加上图表自带的 Roguelike Dungeons、暮色森林补充表。输出物品的 {@code chance} 是每次抽取命中该条目的概率，
 * 单位与其他配方一致（10000 表示 100%）；每个箱子抽取 {@code rollsMin}～{@code rollsMax} 次（两端都可取到），每次按权重抽一个条目，
 * 堆叠数量在该条目提示的数量范围内均匀取值（上限即输出的 {@code amount}）。
 * 结构名保存为 {@code structure}（英文键），有本地化名时另存 {@code structureName}。
 */
public class WorldgenLootHandlerRecipe extends BaseHandlerRecipe {

    private static final String GROUP_ID_SUFFIX = ".forge.worldgenloot";
    private static final String DIAGRAM_PACKAGE = "com.github.dcysteine.neicustomdiagram.generators.forge.worldgenloot.";
    private static final String LANG_PREFIX = "neicustomdiagram.generators.forge.worldgenloot.forge.loot.";

    public WorldgenLootHandlerRecipe(IRecipeHandler handler) {
        super(handler);
    }

    public static boolean supports(IRecipeHandler handler) {
        return handler.getClass()
            .getName()
            .startsWith("com.github.dcysteine.neicustomdiagram.") && handler.getHandlerId() != null
            && handler.getHandlerId()
                .endsWith(GROUP_ID_SUFFIX);
    }

    /** 一个战利品表：英文键、本地化名、抽取次数（未知为 null）和条目。 */
    private static final class Table {

        final String key;
        final String localizedName;
        final Integer rollsMin;
        final Integer rollsMax;
        final String origin;
        final List<Entry> entries = new ArrayList<>();

        Table(String key, String localizedName, Integer rollsMin, Integer rollsMax, String origin) {
            this.key = key;
            this.localizedName = localizedName;
            this.rollsMin = rollsMin;
            this.rollsMax = rollsMax;
            this.origin = origin;
        }
    }

    private static final class Entry {

        final ItemStack stack;
        final int min;
        final int max;
        final int weight;

        Entry(ItemStack stack, int min, int max, int weight) {
            this.stack = stack;
            this.min = min;
            this.max = max;
            this.weight = weight;
        }
    }

    @Override
    public List<?> getRecipes(IRecipeHandler handler) {
        List<Table> tables = new ArrayList<>(forgeTables());
        try {
            if (Loader.isModLoaded("Roguelike"))
                tables.addAll(diagramTables("RoguelikeDungeonsLootTables", "roguelike"));
            if (Loader.isModLoaded("TwilightForest"))
                tables.addAll(diagramTables("TwilightForestLootTables", "twilightforest"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read NEI Custom Diagram supplementary loot tables", e);
        }
        List<Map<String, Object>> recipes = new ArrayList<>();
        for (Table table : tables) {
            if (table.entries.isEmpty()) continue;
            if (selection.isActive()) {
                boolean matches = false;
                for (Entry entry : table.entries) {
                    if (selection.matches(entry.stack)) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) continue;
            }
            recipes.add(toRecipe(table));
        }
        return recipes;
    }

    @SuppressWarnings("unchecked")
    private static List<Table> forgeTables() {
        Map<String, ChestGenHooks> registry;
        try {
            Field field = ChestGenHooks.class.getDeclaredField("chestInfo");
            field.setAccessible(true);
            registry = (Map<String, ChestGenHooks>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read Forge chest loot registry", e);
        }
        // 注册表是 HashMap，顺序不稳定；按英文键排序。
        List<Table> tables = new ArrayList<>();
        for (Map.Entry<String, ChestGenHooks> registered : new TreeMap<>(registry).entrySet()) {
            ChestGenHooks hooks = registered.getValue();
            WeightedRandomChestContent[] contents = hooks.getItems(new Random());
            if (contents == null) continue;
            String key = registered.getKey();
            String langKey = LANG_PREFIX + key;
            Table table = new Table(
                key,
                StatCollector.canTranslate(langKey) ? StatCollector.translateToLocal(langKey) : null,
                hooks.getMin(),
                // Forge 的 getCount 为 min + nextInt(max - min)，上限不含；换算成包含上限的真实最大次数。
                hooks.getMin() < hooks.getMax() ? hooks.getMax() - 1 : hooks.getMin(),
                "forge");
            for (WeightedRandomChestContent content : contents) {
                if (content.theItemId == null || content.theItemId.getItem() == null) continue;
                table.entries.add(
                    new Entry(
                        content.theItemId,
                        content.theMinimumChanceToGenerateItem,
                        content.theMaximumChanceToGenerateItem,
                        content.itemWeight));
            }
            tables.add(table);
        }
        return tables;
    }

    /** 调用图表自带的 Roguelike/暮色森林提取器；它们没有英文键，名称沿用图表按当前语言生成的标题。 */
    private static List<Table> diagramTables(String className, String origin) throws ReflectiveOperationException {
        Method get = Class.forName(DIAGRAM_PACKAGE + className)
            .getDeclaredMethod("get");
        get.setAccessible(true);
        List<Table> tables = new ArrayList<>();
        for (Object loot : (List<?>) get.invoke(null)) {
            String name = (String) invoke(loot, "name");
            Table table = new Table(name, null, null, null, origin);
            for (Object entry : (List<?>) invoke(loot, "entries")) {
                Object component = invoke(entry, "item");
                ItemStack stack = (ItemStack) invoke(component, "stack");
                table.entries.add(
                    new Entry(
                        stack,
                        ((Number) invoke(entry, "min")).intValue(),
                        ((Number) invoke(entry, "max")).intValue(),
                        ((Number) invoke(entry, "weight")).intValue()));
            }
            tables.add(table);
        }
        return tables;
    }

    private static Object invoke(Object target, String method) throws ReflectiveOperationException {
        Method m = target.getClass()
            .getMethod(method);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private static Map<String, Object> toRecipe(Table table) {
        long totalWeight = 0;
        for (Entry entry : table.entries) totalWeight += entry.weight;
        List<Object> outputs = new ArrayList<>();
        for (Entry entry : table.entries) {
            ItemStack stack = entry.stack.copy();
            stack.stackSize = Math.max(entry.max, 1);
            RecipeItem item = new RecipeItem(stack);
            if (totalWeight > 0) {
                // 每次抽取命中该条目的概率；10000 = 100%。
                item.chance = Math.round(entry.weight * 10000d / totalWeight * 1e6) / 1e6;
            }
            item.withTooltip(entry.min == entry.max ? "数量：" + entry.max : "数量：" + entry.min + "～" + entry.max);
            outputs.add(item);

        }
        Map<String, Object> recipe = new LinkedHashMap<>();
        recipe.put("outputItems", outputs);
        recipe.put("structure", table.key);
        if (table.localizedName != null) recipe.put("structureName", table.localizedName);
        recipe.put("lootSource", table.origin);
        if (table.rollsMin != null) recipe.put("rollsMin", table.rollsMin);
        if (table.rollsMax != null) recipe.put("rollsMax", table.rollsMax);
        recipe.put("totalWeight", totalWeight);
        return recipe;
    }
}
