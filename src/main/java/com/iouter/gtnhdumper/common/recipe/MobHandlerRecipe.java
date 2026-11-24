package com.iouter.gtnhdumper.common.recipe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.EntityLiving;
import net.minecraft.item.ItemStack;

import com.iouter.gtnhdumper.GTNHDumper;
import com.iouter.gtnhdumper.common.recipe.base.BaseHandlerRecipe;
import com.iouter.gtnhdumper.common.recipe.base.RecipeItem;
import com.iouter.gtnhdumper.common.utils.RecipeUtil;
import com.iouter.gtnhdumper.common.utils.Utils;
import com.kuba6000.mobsinfo.api.IChanceModifier;
import com.kuba6000.mobsinfo.api.MobDrop;
import com.kuba6000.mobsinfo.api.SpawnInfo;
import com.kuba6000.mobsinfo.nei.MobHandler;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler.CachedRecipe;
import lombok.Setter;
import lombok.experimental.Accessors;

public class MobHandlerRecipe extends BaseHandlerRecipe {

    private static final Class<?> mobCachedRecipeClass;
    private static final Field mobField;
    private static final Field mobnameField;
    private static final Field localizedNameField;
    private static final Field mOutputsField;
    private static final Field mInputField;
    private static final Field infernaltypeField;
    private static final Field ingredientField;
    private static final Field modField;
    private static final Field maxHealthField;
    private static final Field isUsableInVialField;
    private static final Field isPeacefulAllowedField;
    private static final Field additionalInformationField;
    private static final Field spawnListField;
    private static final Field isBossField;

    static {
        try {
            mobCachedRecipeClass = Class.forName("com.kuba6000.mobsinfo.nei.MobHandler$MobCachedRecipe");
            mobField = mobCachedRecipeClass.getField("mob");
            mobnameField = mobCachedRecipeClass.getField("mobname");
            localizedNameField = mobCachedRecipeClass.getField("localizedName");
            mOutputsField = mobCachedRecipeClass.getField("mOutputs");
            mInputField = mobCachedRecipeClass.getField("mInput");
            infernaltypeField = mobCachedRecipeClass.getField("infernaltype");
            ingredientField = mobCachedRecipeClass.getField("ingredient");
            modField = mobCachedRecipeClass.getField("mod");
            maxHealthField = mobCachedRecipeClass.getField("maxHealth");
            isUsableInVialField = mobCachedRecipeClass.getField("isUsableInVial");
            isPeacefulAllowedField = mobCachedRecipeClass.getField("isPeacefulAllowed");
            additionalInformationField = mobCachedRecipeClass.getField("additionalInformation");
            spawnListField = mobCachedRecipeClass.getField("spawnList");
            isBossField = mobCachedRecipeClass.getField("isBoss");

            mobField.setAccessible(true);
            mobnameField.setAccessible(true);
            localizedNameField.setAccessible(true);
            mOutputsField.setAccessible(true);
            mInputField.setAccessible(true);
            infernaltypeField.setAccessible(true);
            ingredientField.setAccessible(true);
            modField.setAccessible(true);
            maxHealthField.setAccessible(true);
            isUsableInVialField.setAccessible(true);
            isPeacefulAllowedField.setAccessible(true);
            additionalInformationField.setAccessible(true);
            spawnListField.setAccessible(true);
            isBossField.setAccessible(true);
        } catch (Exception e) {
            GTNHDumper.LOG.error(e);
            throw new RuntimeException(e);
        }
    }

    public MobHandlerRecipe(MobHandler handler) {
        super(handler);
    }

    @Override
    public List<?> getRecipes(IRecipeHandler baseHandler) {
        List<MobRecipe> recipes = new ArrayList<>();
        // The NEI page's spawn summary compares a mob's places with every place MobsInfo knows of.
        int spawnTotal = SpawnInfo.getAllKnownInfos()
            .size();
        MobHandler handler = (MobHandler) baseHandler;
        handler.loadCraftingRecipes(handler.getOverlayIdentifier(), (Object) null);
        for (CachedRecipe cachedRecipe : handler.arecipes) {
            if (!mobCachedRecipeClass.isInstance(cachedRecipe)) {
                continue;
            }
            if (selection.isActive() && !selection.matchesStacks(getMOutputs(cachedRecipe))) continue;
            String[] spawnList;
            if (getSpawnList(cachedRecipe).isEmpty()) {
                spawnList = null;
            } else {
                spawnList = getSpawnList(cachedRecipe).stream()
                    .map(SpawnInfo::getInfo)
                    .toArray(String[]::new);
            }
            EecInfo eec = eecRecipes().get(mobKey(cachedRecipe));
            boolean[] eecUsed = new boolean[eec == null ? 0 : eec.outputs.size()];
            List<Object> normal = new ArrayList<>();
            List<Object> rare = new ArrayList<>();
            List<Object> additional = new ArrayList<>();
            List<Object> infernal = new ArrayList<>();
            List<PositionedStack> drops = getMOutputs(cachedRecipe);
            // The NEI stacks keep the numeric chance but not the flags and modifiers; those live on MobsInfo's own
            // drops, matched here by type, chance and item.
            List<MobDrop> rawDrops = rawDrops(cachedRecipe);
            for (PositionedStack positionedStack : drops) {
                if (!(positionedStack instanceof MobHandler.MobPositionedStack mobStack)) {
                    continue;
                }
                MobDrop rawDrop = takeDrop(rawDrops, mobStack);
                Object mobs = toMobItem(mobStack, rawDrop, eec == null ? -1 : eecIndex(eec, eecUsed, rawDrop));
                switch (mobStack.type) {
                    case Normal: {
                        normal.add(mobs);
                        break;
                    }
                    case Rare: {
                        rare.add(mobs);
                        break;
                    }
                    case Additional: {
                        additional.add(mobs);
                        break;
                    }
                    case Infernal: {
                        infernal.add(mobs);
                        break;
                    }
                }
            }
            if (normal.isEmpty()) {
                normal = null;
            }
            if (rare.isEmpty()) {
                rare = null;
            }
            if (additional.isEmpty()) {
                additional = null;
            }
            if (infernal.isEmpty()) {
                infernal = null;
            }
            recipes.add(
                new MobRecipe().setEntityName(entityId(cachedRecipe))
                    .setLocalizedName(getLocalizedName(cachedRecipe))
                    .setMod(getMod(cachedRecipe))
                    .setMaxHealth(getMaxHealth(cachedRecipe))
                    .setInfernalType(getInfernaltype(cachedRecipe))
                    .setSpawnList(spawnList)
                    .setSpawnTotal(spawnTotal)
                    .setBoss(!getIsBoss(cachedRecipe).isEmpty())
                    .setPeacefulAllowed(isPeacefulAllowed(cachedRecipe))
                    .setUsableInVial(isUsableInVial(cachedRecipe))
                    .setAdditionalInformation(
                        getAdditionalInformation(cachedRecipe).isEmpty() ? null
                            : getAdditionalInformation(cachedRecipe))
                    .setEecEut(eec == null ? null : eec.eut)
                    .setEecDuration(eec == null ? null : eec.duration)
                    .setInputItems(getMInput(cachedRecipe))
                    .setNormalDrops(normal)
                    .setRareDrops(rare)
                    .setAdditionalDrops(additional)
                    .setInfernalDrops(infernal));
        }
        return recipes;
    }

    public static EntityLiving getMob(Object obj) {
        try {
            return (EntityLiving) mobField.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getMobname(Object obj) {
        try {
            return (String) mobnameField.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getLocalizedName(Object obj) {
        try {
            return (String) localizedNameField.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<PositionedStack> getMOutputs(Object obj) {
        try {
            return (List<PositionedStack>) mOutputsField.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<ItemStack> getMInput(Object obj) {
        try {
            return (List<ItemStack>) mInputField.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getInfernaltype(Object obj) {
        try {
            return infernaltypeField.getInt(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static PositionedStack getIngredient(Object obj) {
        try {
            return (PositionedStack) ingredientField.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getMod(Object obj) {
        try {
            return (String) modField.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static float getMaxHealth(Object obj) {
        try {
            return maxHealthField.getFloat(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isUsableInVial(Object obj) {
        try {
            return isUsableInVialField.getBoolean(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isPeacefulAllowed(Object obj) {
        try {
            return isPeacefulAllowedField.getBoolean(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<String> getAdditionalInformation(Object obj) {
        try {
            return (List<String>) additionalInformationField.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Set<SpawnInfo> getSpawnList(Object obj) {
        try {
            return (Set<SpawnInfo>) spawnListField.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getIsBoss(Object obj) {
        try {
            return (String) isBossField.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static MobItem transferToMobItem(RecipeItem item, MobHandler.MobPositionedStack mobStack, MobDrop raw,
        int eecOrder) {
        MobItem mob = new MobItem(item);
        if (eecOrder >= 0) mob.eecOrder = eecOrder;
        // 1/100 %: 10000 is omitted (always); 0 with variableChance means the chance depends on the modifiers.
        mob.withChance(mobStack.chance);
        if (raw != null) {
            if (raw.lootable) mob.lootable = true;
            if (raw.playerOnly) mob.playerOnly = true;
            if (raw.variableChance) mob.variableChance = true;
            if (raw.chanceModifiers != null && !raw.chanceModifiers.isEmpty()) {
                mob.chanceModifiers = new ArrayList<>();
                for (IChanceModifier modifier : raw.chanceModifiers) mob.chanceModifiers.add(describe(modifier));
            }
            if (raw.additionalInfo != null && !raw.additionalInfo.isEmpty()) {
                mob.additionalInfo = new ArrayList<>(raw.additionalInfo);
            }
        }
        mob.setRandomdamage(mobStack.randomdamage);
        mob.setDamages(compressToRanges(mobStack.damages));
        mob.setEnchantable(mobStack.enchantable);
        if (mobStack.enchantable) {
            mob.setEnchantmentLevel(mobStack.enchantmentLevel);
        }
        if (mobStack.extraTooltip != null) {
            if (mob.tooltip == null) {
                mob.tooltip = new ArrayList<>();
            }
            mob.tooltip.addAll(mobStack.extraTooltip);
        }
        return mob;
    }

    private static Object toMobItem(MobHandler.MobPositionedStack mobStack, MobDrop raw, int eecOrder) {
        Object obj = RecipeUtil.getRecipeItems(mobStack);
        if (obj instanceof RecipeItem item) {
            return transferToMobItem(item, mobStack, raw, eecOrder);
        } else if (obj instanceof RecipeItem[]recipeItems) {
            return Arrays.stream(recipeItems)
                .map(recipeItem -> transferToMobItem(recipeItem, mobStack, raw, eecOrder))
                .toArray(MobItem[]::new);
        } else {
            return null;
        }
    }

    // MobsInfo keys its recipes by registration name, or by its own key for a variant it registers separately (the
    // wither skeleton is "witherSkeleton", an entity of class Skeleton). The NEI page names both "Skeleton", so the
    // recipes are told apart by the entity instance they were built from, as the entity export does.
    private static Map<Object, String> variantIds;
    private static Map<Object, String> mobKeys;
    private static Map<Object, com.kuba6000.mobsinfo.api.MobRecipe> recipesByEntity;

    private static synchronized void indexMobs() {
        if (mobKeys != null) return;
        Map<Object, String> ids = new java.util.IdentityHashMap<>();
        Map<Object, String> keys = new java.util.IdentityHashMap<>();
        Map<Object, com.kuba6000.mobsinfo.api.MobRecipe> recipes = new java.util.IdentityHashMap<>();
        for (Map.Entry<String, com.kuba6000.mobsinfo.api.MobRecipe> entry : com.kuba6000.mobsinfo.api.MobRecipe.MobNameToRecipeMap
            .entrySet()) {
            if (entry.getValue() == null || entry.getValue().entity == null) continue;
            keys.put(entry.getValue().entity, entry.getKey());
            recipes.put(entry.getValue().entity, entry.getValue());
        }
        try {
            for (com.iouter.gtnhdumper.common.utils.EntityExport.Variant variant : com.iouter.gtnhdumper.common.utils.EntityExport
                .variants()) {
                ids.put(variant.entity, variant.id);
            }
        } catch (Exception | LinkageError e) {
            GTNHDumper.LOG.warn("Entity variants unavailable for the Mob Info export: {}", e.toString());
        }
        variantIds = ids;
        recipesByEntity = recipes;
        mobKeys = keys;
    }

    /**
     * The id the entity export uses: the registration name, or for a variant {@code <name>#<tag>=<value>} (such as
     * {@code Skeleton#SkeletonType=1}).
     */
    private static String entityId(Object cachedRecipe) {
        indexMobs();
        String variant = variantIds.get(getMob(cachedRecipe));
        return variant != null ? variant : getMobname(cachedRecipe);
    }

    /** MobsInfo's recipe key for the entity, which is also what the Extreme Entity Crusher recipe map uses. */
    private static String mobKey(Object cachedRecipe) {
        indexMobs();
        String key = mobKeys.get(getMob(cachedRecipe));
        return key != null ? key : getMobname(cachedRecipe);
    }

    /** MobsInfo's own drop list for an entity, a copy that the matching below consumes. */
    private static List<MobDrop> rawDrops(Object cachedRecipe) {
        indexMobs();
        com.kuba6000.mobsinfo.api.MobRecipe recipe = recipesByEntity.get(getMob(cachedRecipe));
        if (recipe == null) {
            recipe = com.kuba6000.mobsinfo.api.MobRecipe.MobNameToRecipeMap.get(getMobname(cachedRecipe));
        }
        return recipe == null || recipe.mOutputs == null ? new ArrayList<>() : new ArrayList<>(recipe.mOutputs);
    }

    /** Takes the first unused raw drop with the stack's type, chance and item; null when none fits. */
    private static MobDrop takeDrop(List<MobDrop> rawDrops, MobHandler.MobPositionedStack mobStack) {
        ItemStack shown = mobStack.item;
        for (int i = 0; i < rawDrops.size(); i++) {
            MobDrop drop = rawDrops.get(i);
            if (drop.type != mobStack.type || drop.chance != mobStack.chance
                || drop.stack == null
                || shown == null
                || drop.stack.getItem() != shown.getItem()) continue;
            if (!mobStack.randomdamage && drop.stack.getItemDamage() != shown.getItemDamage()
                && drop.stack.getItemDamage() != net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE) continue;
            return rawDrops.remove(i);
        }
        return null;
    }

    /** A chance modifier as its class name and the values it was built with (the fields are package-private). */
    private static Map<String, Object> describe(IChanceModifier modifier) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(
            "type",
            modifier.getClass()
                .getSimpleName());
        for (Class<?> type = modifier.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                try {
                    field.setAccessible(true);
                    Object value = describeValue(field.get(modifier));
                    if (value != null) out.put(field.getName(), value);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    out.put(field.getName() + "Error", e.toString());
                }
            }
        }
        return out;
    }

    private static Object describeValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String)
            return value;
        if (value instanceof ItemStack stack) return Utils.getItemStackShortKey(stack);
        if (value instanceof net.minecraft.enchantment.EnchantmentData data) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("enchantment", describeValue(data.enchantmentobj));
            out.put("level", data.enchantmentLevel);
            return out;
        }
        if (value instanceof net.minecraft.enchantment.Enchantment enchantment) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", enchantment.effectId);
            out.put("name", enchantment.getName());
            return out;
        }
        if (value instanceof net.minecraft.world.biome.BiomeGenBase biome) return biome.biomeID;
        return String.valueOf(value);
    }

    /** The crusher's recipe for one entity: power, duration and the drop list in the order the machine uses. */
    private static final class EecInfo {

        final int eut;
        final int duration;
        final List<MobDrop> outputs;

        EecInfo(int eut, int duration, List<MobDrop> outputs) {
            this.eut = eut;
            this.duration = duration;
            this.outputs = outputs;
        }
    }

    /**
     * Position of a MobsInfo drop in the crusher's list: the same object when the list shares it, otherwise the first
     * unused entry with the same type, chance and item. -1 when the crusher does not have this drop.
     */
    private static int eecIndex(EecInfo eec, boolean[] used, MobDrop drop) {
        if (drop == null) return -1;
        for (int i = 0; i < eec.outputs.size(); i++) {
            if (!used[i] && eec.outputs.get(i) == drop) {
                used[i] = true;
                return i;
            }
        }
        for (int i = 0; i < eec.outputs.size(); i++) {
            MobDrop other = eec.outputs.get(i);
            if (used[i] || other == null
                || other.stack == null
                || drop.stack == null
                || other.type != drop.type
                || other.chance != drop.chance
                || other.stack.getItem() != drop.stack.getItem()
                || other.stack.getItemDamage() != drop.stack.getItemDamage()) continue;
            used[i] = true;
            return i;
        }
        return -1;
    }

    private static Map<String, EecInfo> eecCache;

    /**
     * Extreme Entity Crusher power and duration per entity (KubaTech's recipe map), read by reflection. Only entities
     * the machine can process appear, so a recipe with these values is one the crusher accepts.
     */
    private static synchronized Map<String, EecInfo> eecRecipes() {
        if (eecCache != null) return eecCache;
        Map<String, EecInfo> result = new HashMap<>();
        try {
            Class<?> loader = Class.forName("kubatech.loaders.MobHandlerLoader");
            Field mapField = loader.getDeclaredField("recipeMap");
            mapField.setAccessible(true);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) mapField.get(null)).entrySet()) {
                Object recipe = entry.getValue();
                Field eut = recipe.getClass()
                    .getDeclaredField("mEUt");
                Field duration = recipe.getClass()
                    .getDeclaredField("mDuration");
                eut.setAccessible(true);
                duration.setAccessible(true);
                Field outputs = recipe.getClass()
                    .getDeclaredField("mOutputs");
                outputs.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<MobDrop> drops = (List<MobDrop>) outputs.get(recipe);
                result.put(
                    String.valueOf(entry.getKey()),
                    new EecInfo(
                        ((Number) eut.get(recipe)).intValue(),
                        ((Number) duration.get(recipe)).intValue(),
                        drops == null ? new ArrayList<>() : new ArrayList<>(drops)));
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            GTNHDumper.LOG.warn("Extreme Entity Crusher recipes unavailable for the Mob Info export: {}", e.toString());
        }
        eecCache = result;
        return result;
    }

    public static List<Object> compressToRanges(List<Integer> arr) {
        if (arr == null || arr.isEmpty()) return null;

        Integer[] sorted = arr.stream()
            .distinct()
            .sorted()
            .toArray(Integer[]::new);
        List<Object> result = new ArrayList<>();
        int start = sorted[0];
        int end = sorted[0];

        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] == end + 1) {
                end = sorted[i];
            } else {
                addRangeOrNumber(result, start, end);
                start = sorted[i];
                end = sorted[i];
            }
        }
        addRangeOrNumber(result, start, end);
        return result;
    }

    private static void addRangeOrNumber(List<Object> list, int start, int end) {
        if (start == end) {
            list.add(start);
        } else {
            list.add(new int[] { start, end });
        }
    }

    @Setter
    @Accessors(chain = true)
    private static class MobRecipe {

        private String entityName;
        private String localizedName;
        private String mod;
        private float maxHealth;
        private int infernalType;
        private String[] spawnList;
        /** Number of places MobsInfo knows of; the summary reads "everywhere", "in N places" or "everywhere but M". */
        private Integer spawnTotal;
        private boolean isBoss;
        private boolean isPeacefulAllowed;
        private boolean isUsableInVial;
        private List<String> additionalInformation;
        /** Extreme Entity Crusher power (EU/t) and duration (ticks); absent for entities the machine cannot process. */
        private Integer eecEut;
        private Integer eecDuration;
        private List<ItemStack> inputItems;
        private List<Object> normalDrops;
        private List<Object> rareDrops;
        private List<Object> additionalDrops;
        private List<Object> infernalDrops;
    }

    @Setter
    public static class MobItem extends RecipeItem {

        private boolean enchantable;
        private boolean randomdamage;
        private List<Object> damages;
        private int enchantmentLevel;
        // From MobsInfo's own drop; only set when true or non-empty.
        /** Position in the Extreme Entity Crusher's drop list; absent when the crusher does not have this drop. */
        private Integer eecOrder;
        private Boolean lootable;
        private Boolean playerOnly;
        private Boolean variableChance;
        private List<Map<String, Object>> chanceModifiers;
        private List<String> additionalInfo;

        public MobItem(String key, int amount) {
            super(key, amount);
        }

        public MobItem(ItemStack stack) {
            super(stack);
        }

        public MobItem(RecipeItem recipeItem) {
            this(recipeItem.key, 1);
            this.amount = recipeItem.amount;
            this.chance = recipeItem.chance;
            this.nbt = recipeItem.nbt;
            this.tooltip = recipeItem.tooltip;
        }
    }
}
