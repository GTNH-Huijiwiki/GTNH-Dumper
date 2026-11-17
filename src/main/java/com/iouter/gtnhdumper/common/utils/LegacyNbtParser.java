package com.iouter.gtnhdumper.common.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Reads 1.7 compound.toString(), whose compound keys may contain unquoted colons. */
public final class LegacyNbtParser {

    private static final Pattern KEY = Pattern.compile("([^{}\\[\\],\"\\s]+):([^{}\\[\\],\"\\s:]+):");

    private LegacyNbtParser() {}

    public static NBTBase parse(String text) throws NBTException {
        String prefix = "gtnhdumperKey";
        while (text.contains(prefix)) prefix += "X";
        Map<String, String> keys = new LinkedHashMap<>();
        StringBuilder safe = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            safe.append(c);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == ',') {
                Matcher match = KEY.matcher(text);
                match.region(i + 1, text.length());
                if (match.lookingAt()) {
                    String placeholder = prefix + keys.size();
                    keys.put(placeholder, match.group(1) + ":" + match.group(2));
                    safe.append(placeholder).append(':');
                    i = match.end() - 1;
                }
            }
        }
        NBTBase result = JsonToNBT.func_150315_a(safe.toString());
        restore(result, keys);
        return result;
    }

    private static void restore(NBTBase tag, Map<String, String> keys) {
        if (tag instanceof NBTTagCompound compound) {
            for (Object value : new ArrayList<>(compound.func_150296_c())) {
                String key = (String) value;
                NBTBase child = compound.getTag(key);
                restore(child, keys);
                if (keys.containsKey(key)) {
                    compound.removeTag(key);
                    compound.setTag(keys.get(key), child);
                }
            }
        } else if (tag instanceof NBTTagList list) {
            // 1.7 has no generic indexed getter; rotating all entries preserves their order.
            int count = list.tagCount();
            for (int i = 0; i < count; i++) {
                NBTBase child = list.removeTag(0);
                restore(child, keys);
                list.appendTag(child);
            }
        }
    }
}
