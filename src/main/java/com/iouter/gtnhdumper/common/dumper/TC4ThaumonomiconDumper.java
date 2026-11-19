package com.iouter.gtnhdumper.common.dumper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;

import com.iouter.gtnhdumper.CommonProxy;
import com.iouter.gtnhdumper.GTNHDumper;
import com.iouter.gtnhdumper.common.base.WikiDumper;
import com.iouter.gtnhdumper.common.recipe.base.RecipeItem;
import com.iouter.gtnhdumper.common.utils.ModFilter;
import com.iouter.gtnhdumper.common.utils.ResearchSources;
import com.iouter.gtnhdumper.common.utils.ThaumonomiconAssets;
import com.iouter.gtnhdumper.common.utils.ThaumonomiconPages;

import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategoryList;
import thaumcraft.api.research.ResearchItem;
import tuhljin.automagy.config.ModResearchItems;

/**
 * The whole Thaumonomicon in one file, {@code tc4thaumonomicon.json}:
 * <ul>
 * <li>{@code categories}: the tabs, with icon, background and the grid bounds the browser scrolls within;</li>
 * <li>{@code aspects}: every aspect with colour, components and icon;</li>
 * <li>{@code researches}: every entry with grid position, aspect cost, icon, prerequisites and all pages.</li>
 * </ul>
 * All three are maps in registration order, keyed by category key, aspect tag and research key. Textures they refer
 * to (plus the book's interface textures) are copied to {@code tc4thaumonomicon-assets/}, see
 * {@link ThaumonomiconAssets}. Every field of {@code tc4research.json} is included with the same name and value, so
 * this file replaces it.
 *
 * <p>
 * With a mod filter only researches from that mod are written, together with the categories they sit in; aspects
 * have no per-mod ownership and are left out.
 */
public class TC4ThaumonomiconDumper extends WikiDumper {

    private static final ResourceLocation[] INTERFACE = {
        new ResourceLocation("thaumcraft", "textures/gui/gui_research.png"),
        new ResourceLocation("thaumcraft", "textures/gui/gui_researchbook.png"),
        new ResourceLocation("thaumcraft", "textures/gui/gui_researchbook_overlay.png"),
        new ResourceLocation("thaumcraft", "textures/aspects/_back.png"),
        new ResourceLocation("thaumcraft", "textures/aspects/_unknown.png") };

    public TC4ThaumonomiconDumper() {
        super("tools.dump.gtnhdumper.tc4thaumonomicon");
    }

    @Override
    public boolean supportsModFilter() {
        return true;
    }

    @Override
    public int modeCount() {
        return 1;
    }

    @Override
    public String modeButtonText() {
        return "Map";
    }

    // The WikiDumper row API is not used: the file is a composite, written by dumpTo.
    @Override
    public int getKeyIndex() {
        return 0;
    }

    @Override
    public String getKeyStr() {
        return "tc4Thaumonomicon";
    }

    @Override
    public String[] header() {
        return new String[0];
    }

    @Override
    public Iterable<Object[]> dumpObject(int mode) {
        return Collections.emptyList();
    }

    @Override
    public void dumpTo(File file, int mode) throws IOException {
        ThaumonomiconAssets assets = new ThaumonomiconAssets(file);
        ModFilter filter = ModFilter.current();
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> researches = new LinkedHashMap<>();
        Set<String> usedCategories = new LinkedHashSet<>();
        for (ResearchCategoryList category : ResearchCategories.researchCategories.values()) {
            // The category map is a HashMap; sort so the file is stable between runs.
            List<ResearchItem> sorted = new ArrayList<>(category.research.values());
            sorted.sort(
                Comparator.<ResearchItem>comparingInt(r -> r.displayColumn)
                    .thenComparingInt(r -> r.displayRow)
                    .thenComparing(r -> r.key));
            for (ResearchItem research : sorted) {
                String source = ResearchSources.sourceOf(research);
                if (!filter.matchesMod(source)) continue;
                researches.put(research.key, research(research, source, assets));
                usedCategories.add(research.category);
            }
        }
        root.put("categories", categories(filter.isActive() ? usedCategories : null, assets));
        if (!filter.isActive()) root.put("aspects", aspects(assets));
        root.put("researches", researches);
        try (FileWriter writer = new FileWriter(file)) {
            GTNHDumper.GSON.toJson(root, writer);
        }
        GTNHDumper.LOG.info(
            "Thaumonomicon: {} researches, {} textures copied, {} missing {}",
            researches.size(),
            assets.count(),
            assets.missing()
                .size(),
            assets.missing());
    }

    private static Map<String, Object> categories(Set<String> only, ThaumonomiconAssets assets) {
        for (ResourceLocation location : INTERFACE) assets.add(location);
        Map<String, Object> result = new LinkedHashMap<>();
        int order = 0;
        for (Map.Entry<String, ResearchCategoryList> entry : ResearchCategories.researchCategories.entrySet()) {
            String key = entry.getKey();
            ResearchCategoryList category = entry.getValue();
            int index = order++;
            if (only != null && !only.contains(key)) continue;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("Key", key);
            map.put("Name", ResearchCategories.getCategoryName(key));
            map.put("Order", index);
            map.put("Icon", assets.add(category.icon));
            map.put("Background", assets.add(category.background));
            map.put("MinColumn", category.minDisplayColumn);
            map.put("MinRow", category.minDisplayRow);
            map.put("MaxColumn", category.maxDisplayColumn);
            map.put("MaxRow", category.maxDisplayRow);
            map.put("ResearchCount", category.research.size());
            result.put(key, map);
        }
        return result;
    }

    private static Map<String, Object> aspects(ThaumonomiconAssets assets) {
        Map<String, Object> result = new LinkedHashMap<>();
        int order = 0;
        for (Aspect aspect : Aspect.aspects.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("Tag", aspect.getTag());
            map.put("Name", aspect.getName());
            map.put("Description", aspect.getLocalizedDescription());
            map.put("Order", order++);
            map.put("Color", aspect.getColor());
            map.put("IsPrimal", aspect.isPrimal());
            Aspect[] components = aspect.getComponents();
            if (!aspect.isPrimal() && components != null) {
                List<String> tags = new ArrayList<>();
                for (Aspect component : components) tags.add(component.getTag());
                map.put("Components", tags);
            }
            map.put("Image", assets.add(aspect.getImage()));
            map.put("Blend", aspect.getBlend());
            result.put(aspect.getTag(), map);
        }
        return result;
    }

    private static Map<String, Object> research(ResearchItem m, String source, ThaumonomiconAssets assets) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("Key", m.key);
        // tc4research.json trims names and writes the localized category name; keep both.
        map.put(
            "Name",
            m.getName()
                .trim());
        map.put("Category", ResearchCategories.getCategoryName(m.category));
        map.put("CategoryKey", m.category);
        map.put("Column", m.displayColumn);
        map.put("Row", m.displayRow);
        map.put("Description", m.getText());
        map.put("Aspects", ThaumonomiconPages.aspects(m.tags));
        map.put("Complexity", m.getComplexity());
        map.put("Warp", ThaumcraftApi.getWarp(m.key));
        map.put("Parents", m.parents);
        map.put("ParentsHidden", m.parentsHidden);
        map.put("Siblings", m.siblings);
        // Same fields and values as tc4research.json: triggers as localized names.
        map.put("ItemTriggers", TC4ResearchDumper.getItemStacksName(m.getItemTriggers()));
        map.put("EntityTriggers", TC4ResearchDumper.getEnTitiesName(m.getEntityTriggers()));
        map.put("AspectTriggers", TC4ResearchDumper.getAspectsName(m.getAspectTriggers()));
        // Lossless forms of the same triggers: items, entity ids and aspect tags.
        map.put("ItemTriggerItems", itemTriggers(m.getItemTriggers()));
        map.put("EntityTriggerIds", m.getEntityTriggers());
        map.put("AspectTriggerTags", aspectTriggers(m.getAspectTriggers()));
        map.put("KillerTriggers", killerTriggers(m));
        map.put("Icon", ThaumonomiconPages.stack(m.icon_item));
        map.put("IconResource", assets.add(m.icon_resource));
        map.put("IsSpecial", m.isSpecial());
        map.put("IsSecondary", m.isSecondary());
        map.put("IsRound", m.isRound());
        map.put("IsStub", m.isStub());
        map.put("IsVirtual", m.isVirtual());
        map.put("IsConcealed", m.isConcealed());
        map.put("IsHidden", m.isHidden());
        map.put("IsLost", m.isLost());
        map.put("IsAutoUnlock", m.isAutoUnlock());
        map.put("Pages", ThaumonomiconPages.pages(m.getPages(), assets));
        map.put("sourceMod", source);
        return map;
    }

    private static List<RecipeItem> itemTriggers(ItemStack[] stacks) {
        if (stacks == null) return null;
        List<RecipeItem> list = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack != null) list.add(new RecipeItem(stack));
        }
        return list;
    }

    private static List<String> aspectTriggers(Aspect[] aspects) {
        if (aspects == null) return null;
        List<String> list = new ArrayList<>();
        for (Aspect aspect : aspects) {
            if (aspect != null) list.add(aspect.getTag());
        }
        return list;
    }

    /** Automagy unlocks some of its research by killing entities; the map is entity id to research key. */
    private static String[] killerTriggers(ResearchItem research) {
        if (!CommonProxy.isAutomagyLoaded || !"AUTOMAGY".equals(research.category)) return null;
        Set<String> keys = TC4ResearchDumper.getKeysByValue(ModResearchItems.cluesOnKill, research.key);
        return keys.toArray(new String[0]);
    }

    @Override
    public ChatComponentTranslation dumpMessage(File file) {
        return new ChatComponentTranslation(
            "nei.options.tools.dump.gtnhdumper.tc4thaumonomicon.dumped",
            "dumps/" + file.getName());
    }
}
