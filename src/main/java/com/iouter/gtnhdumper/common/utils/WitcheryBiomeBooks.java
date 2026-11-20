package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.biome.BiomeGenBase;

import com.emoniph.witchery.Witchery;
import com.emoniph.witchery.client.gui.GuiScreenBiomeBook;
import com.emoniph.witchery.client.gui.GuiScreenWitchcraftBook;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Witchery's biome books as their screens build them (the screens are constructed, never shown), in the current
 * language and in English:
 * <ul>
 * <li>{@code bookOfBiomes} ({@code witchery:ingredient:106}): the Rite of Shifting Seasons guide, one page per biome
 * type with its focus item and member biomes.</li>
 * <li>{@code bookOfBiomesExtended} ({@code witchery:bookbiomes2}): one page per biome with the lines the book draws
 * (rainfall, temperature, snow, lightning), grouped into biome-type sections.</li>
 * </ul>
 */
final class WitcheryBiomeBooks {

    private WitcheryBiomeBooks() {}

    /** Fills {@code pagesByBiome} with the extended book's page numbers (1-based) of each biome id. */
    static Map<String, Object> export(Map<Integer, List<Integer>> pagesByBiome) throws Exception {
        Map<String, Object> books = new LinkedHashMap<>();
        Object[] english = new Object[2];
        Utils.getEnglishTranslation(() -> {
            try {
                english[0] = basicPages();
                english[1] = extended(null, null);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        });
        Map<String, Object> basic = new LinkedHashMap<>();
        basic.put("item", "witchery:ingredient:106");
        basic.put("pages", basicPages());
        basic.put("pagesEnglish", english[0]);
        books.put("bookOfBiomes", basic);
        Map<String, Object> extended = extended(pagesByBiome, english[1]);
        books.put("bookOfBiomesExtended", extended);
        return books;
    }

    /** Page texts of the Book of Biomes, formatting codes removed. */
    private static List<String> basicPages() throws ReflectiveOperationException {
        ItemStack stack = new ItemStack(GameRegistry.findItem("witchery", "ingredient"), 1, 106);
        GuiScreenWitchcraftBook screen = new GuiScreenWitchcraftBook(Minecraft.getMinecraft().thePlayer, stack);
        NBTTagList pages = (NBTTagList) field(GuiScreenWitchcraftBook.class, "bookPages").get(screen);
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < pages.tagCount(); i++) {
            NBTTagCompound page = pages.getCompoundTagAt(i);
            texts.add(EnumChatFormatting.getTextWithoutFormattingCodes(page.getString("Summary")));
        }
        return texts;
    }

    /**
     * The extended book's pages; {@code english} is the English export to merge, or null when building it. Lines use
     * the book's own format strings, like {@code GuiScreenBiomeBook.drawScreen}.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extended(Map<Integer, List<Integer>> pagesByBiome, Object english)
        throws ReflectiveOperationException {
        Item book = GameRegistry.findItem("witchery", "bookbiomes2");
        GuiScreenBiomeBook screen = new GuiScreenBiomeBook(Minecraft.getMinecraft().thePlayer, new ItemStack(book));
        List<BiomeGenBase> biomes = (List<BiomeGenBase>) field(GuiScreenBiomeBook.class, "biomes").get(screen);
        List<Integer> sectionStarts = (List<Integer>) field(GuiScreenBiomeBook.class, "sections").get(screen);
        List<String> sectionNames = (List<String>) field(GuiScreenBiomeBook.class, "sectionNames").get(screen);
        Map<String, Object> englishBook = (Map<String, Object>) english;
        List<Map<String, Object>> englishPages = englishBook == null ? null
            : (List<Map<String, Object>>) englishBook.get("pages");
        List<Map<String, Object>> sections = new ArrayList<>();
        for (int i = 0; i < sectionStarts.size(); i++) {
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("name", EnumChatFormatting.getTextWithoutFormattingCodes(sectionNames.get(i)));
            if (englishBook != null) section.put(
                "nameEnglish",
                ((List<Map<String, Object>>) englishBook.get("sections")).get(i)
                    .get("name"));
            section.put("firstPage", sectionStarts.get(i) + 1);
            sections.add(section);
        }
        List<Map<String, Object>> pages = new ArrayList<>();
        for (int i = 0; i < biomes.size(); i++) {
            BiomeGenBase biome = biomes.get(i);
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("page", i + 1);
            page.put("biomeId", biome.biomeID);
            page.put("biomeName", biome.biomeName);
            page.put("lines", lines(biome));
            if (englishPages != null) page.put(
                "linesEnglish",
                englishPages.get(i)
                    .get("lines"));
            pages.add(page);
            if (pagesByBiome != null) pagesByBiome.computeIfAbsent(biome.biomeID, id -> new ArrayList<>())
                .add(i + 1);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("item", "witchery:bookbiomes2");
        result.put("sections", sections);
        result.put("pages", pages);
        return result;
    }

    private static List<String> lines(BiomeGenBase biome) {
        List<String> lines = new ArrayList<>();
        lines.add(format("witchery.biomebook.rainfall", biome.rainfall));
        lines.add(
            format(
                biome.isHighHumidity() ? "witchery.biomebook.temperaturehot" : "witchery.biomebook.temperature",
                biome.temperature));
        lines.add(format("witchery.biomebook.snows", yesNo(biome.getEnableSnow())));
        lines.add(format("witchery.biomebook.lightning", yesNo(biome.canSpawnLightningBolt())));
        return lines;
    }

    private static String format(String key, Object value) {
        return EnumChatFormatting.getTextWithoutFormattingCodes(String.format(Witchery.resource(key), value));
    }

    private static String yesNo(boolean value) {
        return Witchery.resource(value ? "witchery.yes" : "witchery.no");
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
