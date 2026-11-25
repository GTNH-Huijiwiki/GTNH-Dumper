package com.iouter.gtnhdumper.common.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;

/** Text representation shared by the ordinary and modifier-key tooltip exports. */
final class TooltipText {

    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9A-FK-OR]");

    // Vanilla translations can put the signed amount after the name (e.g. Chinese),
    // while Chromatic Tooltips puts it first. Normalize just this attribute notation.
    private static final Pattern LEADING_AMOUNT = Pattern.compile("^([+-][0-9][0-9.,]*%?)\\s+(.+)$");

    private TooltipText() {}

    static String format(List<String> lines, List<JsonObject> stats) {
        List<String> result = new ArrayList<>(lines);
        Set<String> existing = new HashSet<>();
        for (String line : lines) {
            existing.add(plainText(line));
        }
        for (JsonObject stat : stats) {
            if (!stat.has("textLine") || stat.get("textLine")
                .isJsonNull()) continue;
            String text = stat.get("textLine")
                .getAsString();
            String plain = plainText(text);
            if (!plain.isEmpty() && existing.add(plain)) {
                result.add(text);
            }
        }
        return String.join("<br>", result);
    }

    private static final Pattern UPGRADE_TARGET = Pattern.compile(".* \\(\\d+\\)(§r)?$");

    /**
     * Gives a fixed order to tooltip lines a mod lists by iterating a hash map: the machines an AE2 upgrade card fits,
     * and the sections of a Forestry electron tube.
     */
    static String stableLines(String registryName, String tooltip) {
        if (tooltip == null) return null;
        if ("appliedenergistics2:item.ItemMultiMaterial".equals(registryName)) {
            List<String> lines = new ArrayList<>(java.util.Arrays.asList(tooltip.split("<br>", -1)));
            List<Integer> targets = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) if (UPGRADE_TARGET.matcher(lines.get(i))
                .matches()) targets.add(i);
            List<String> sorted = new ArrayList<>();
            for (int index : targets) sorted.add(lines.get(index));
            java.util.Collections.sort(sorted);
            for (int i = 0; i < targets.size(); i++) lines.set(targets.get(i), sorted.get(i));
            return String.join("<br>", lines);
        }
        if ("Forestry:thermionicTubes".equals(registryName)) {
            // Each section starts with an underlined heading; the heading before it is the tube's name.
            String[] parts = tooltip.split("<br>(?=§f§n)", -1);
            if (parts.length > 2) java.util.Arrays.sort(parts, 1, parts.length);
            return String.join("<br>", parts);
        }
        return tooltip;
    }

    private static String plainText(String line) {
        String plain = FORMATTING.matcher(String.valueOf(line))
            .replaceAll("")
            .trim();
        return LEADING_AMOUNT.matcher(plain)
            .replaceFirst("$2 $1");
    }
}
