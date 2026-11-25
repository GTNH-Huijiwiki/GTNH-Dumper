package com.iouter.gtnhdumper.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import net.minecraft.stats.Achievement;
import net.minecraft.stats.AchievementList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.AchievementPage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iouter.gtnhdumper.GTNHDumper;

import cpw.mods.fml.common.Loader;

/**
 * Registered achievements (vanilla page and every Forge achievement page) and Amazing Trophies' trophies, in the
 * current language and in English. Written to {@code achievements.json}:
 * {@code {"pages": [...], "achievements": [...], "trophies": [...]}}.
 * <p>
 * Amazing Trophies defines its achievements and trophies in {@code config/amazingtrophies}; the condition and model
 * objects of those files are copied as they are (the mod keeps no parsed copy), so consumers can tell which item an
 * achievement asks for or a trophy shows.
 */
public final class AchievementExport {

    private static final String VANILLA_PAGE = "Minecraft";

    private AchievementExport() {}

    public static void write(File file) throws Exception {
        Map<String, JsonObject> atAchievements = new TreeMap<>();
        Map<String, JsonObject> atTrophies = new TreeMap<>();
        if (Loader.isModLoaded("amazingtrophies")) {
            File config = new File(
                Loader.instance()
                    .getConfigDir(),
                "amazingtrophies");
            readDefinitions(new File(config, "achievements"), atAchievements);
            readDefinitions(new File(config, "trophies"), atTrophies);
        }
        List<Map<String, Object>> english = new ArrayList<>();
        List<Map<String, Object>> englishTrophies = new ArrayList<>();
        Utils.getEnglishTranslation(() -> {
            english.addAll(achievements(atAchievements));
            englishTrophies.addAll(trophies(atTrophies));
        });
        List<Map<String, Object>> achievements = achievements(atAchievements);
        for (int i = 0; i < achievements.size(); i++) {
            achievements.get(i)
                .put(
                    "nameEnglish",
                    english.get(i)
                        .get("name"));
            achievements.get(i)
                .put(
                    "descriptionEnglish",
                    english.get(i)
                        .get("description"));
        }
        List<Map<String, Object>> trophies = trophies(atTrophies);
        for (int i = 0; i < trophies.size(); i++) trophies.get(i)
            .put(
                "nameEnglish",
                englishTrophies.get(i)
                    .get("name"));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("pages", pages());
        root.put("achievements", achievements);
        root.put("trophies", trophies);
        Files.createDirectories(
            file.toPath()
                .getParent());
        Files.write(
            file.toPath(),
            GTNHDumper.GSON.toJson(root)
                .getBytes(StandardCharsets.UTF_8));
    }

    /** The vanilla page first, then Forge pages by name: their registration order changes between launches. */
    private static List<String> pages() {
        List<String> pages = new ArrayList<>();
        pages.add(VANILLA_PAGE);
        for (AchievementPage page : AchievementPage.getAchievementPages()) pages.add(page.getName());
        java.util.Collections.sort(pages.subList(1, pages.size()));
        return pages;
    }

    private static List<Map<String, Object>> achievements(Map<String, JsonObject> definitions) {
        Map<Achievement, String> pageOf = new IdentityHashMap<>();
        for (AchievementPage page : AchievementPage.getAchievementPages()) {
            for (Achievement achievement : page.getAchievements()) pageOf.put(achievement, page.getName());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object value : AchievementList.achievementList) {
            Achievement achievement = (Achievement) value;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", achievement.statId);
            row.put(
                "name",
                plain(
                    achievement.func_150951_e()
                        .getUnformattedText()));
            row.put("description", plain(achievement.getDescription()));
            row.put("page", pageOf.getOrDefault(achievement, VANILLA_PAGE));
            row.put("x", achievement.displayColumn);
            row.put("y", achievement.displayRow);
            if (achievement.parentAchievement != null) row.put("parent", achievement.parentAchievement.statId);
            row.put("special", achievement.getSpecial());
            if (achievement.theItemStack != null && achievement.theItemStack.getItem() != null)
                row.put("icon", Utils.getItemStackShortKey(achievement.theItemStack));
            String shortId = achievement.statId.startsWith("achievement.") ? achievement.statId.substring(12)
                : achievement.statId;
            JsonObject definition = definitions.get(shortId);
            if (definition != null && definition.has("condition")) {
                row.put("source", "amazingtrophies");
                row.put("condition", definition.get("condition"));
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<Map<String, Object>> trophies(Map<String, JsonObject> definitions) {
        Collection<String> ids = registeredTrophies(definitions);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            String key = "tile.amazingtrophies.trophy." + id + ".name";
            row.put("name", StatCollector.canTranslate(key) ? plain(StatCollector.translateToLocal(key)) : null);
            String item = trophyItemKey(id);
            if (item != null) row.put("item", item);
            JsonObject definition = definitions.get(id);
            if (definition != null) {
                if (definition.has("condition")) row.put("condition", definition.get("condition"));
                if (definition.has("model")) row.put("model", definition.get("model"));
            }
            rows.add(row);
        }
        return rows;
    }

    /** Item key (as in the item export) of the trophy block carrying this trophy, or null without the mod. */
    private static String trophyItemKey(String id) {
        try {
            Class<?> api = Class.forName("glowredman.amazingtrophies.api.AmazingTrophiesAPI");
            net.minecraft.block.Block block = (net.minecraft.block.Block) api.getMethod("getTrophyBlock")
                .invoke(null);
            String tag = (String) api.getField("TAGNAME_ID")
                .get(null);
            if (block == null) return null;
            net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(block);
            stack.stackTagCompound = new net.minecraft.nbt.NBTTagCompound();
            stack.stackTagCompound.setString(tag, id);
            return Utils.getItemStackShortKey(stack);
        } catch (ReflectiveOperationException | LinkageError missing) {
            return null;
        }
    }

    /** The mod's registry when reachable (it also holds trophies other mods register), else the config files. */
    @SuppressWarnings("unchecked")
    private static Collection<String> registeredTrophies(Map<String, JsonObject> definitions) {
        try {
            Class<?> api = Class.forName("glowredman.amazingtrophies.api.AmazingTrophiesAPI");
            return new java.util.TreeSet<>(
                (Collection<String>) api.getMethod("getTrophyIDs")
                    .invoke(null));
        } catch (ReflectiveOperationException | LinkageError missing) {
            return definitions.keySet();
        }
    }

    private static void readDefinitions(File folder, Map<String, JsonObject> into) throws IOException {
        if (!folder.isDirectory()) return;
        try (Stream<Path> paths = Files.walk(folder.toPath())) {
            for (Path path : (Iterable<Path>) paths.sorted()::iterator) {
                if (!path.toString()
                    .endsWith(".json")) continue;
                JsonElement element = new JsonParser()
                    .parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                if (object.has("id")) into.put(
                    object.get("id")
                        .getAsString(),
                    object);
            }
        }
    }

    private static String plain(String text) {
        return text == null ? null : EnumChatFormatting.getTextWithoutFormattingCodes(text);
    }
}
