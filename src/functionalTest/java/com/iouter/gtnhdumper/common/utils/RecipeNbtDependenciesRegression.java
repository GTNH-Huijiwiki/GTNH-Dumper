package com.iouter.gtnhdumper.common.utils;

import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonParser;

public final class RecipeNbtDependenciesRegression {

    public static void main(String[] args) throws Exception {
        RecipeNbtDependencies dependencies = new RecipeNbtDependencies();
        dependencies.add(new JsonParser().parse("[{\"otherItems\":[[{\"key\":\"gregtech:gt.metaitem.01:32708\","
            + "\"nbt\":\"{research:123L,display:{Name:\\\"Starcore\\\"}}\"}]],"
            + "\"inputItems\":[\"minecraft:stone\"]}]"));
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", "Starcore");
        tag.setTag("display", display);
        tag.setLong("research", 123);
        check(dependencies.contains("gregtech:gt.metaitem.01:32708", tag), "Nested cross-mod research stick retained");
        check(!dependencies.contains("gregtech:gt.metaitem.01:32707", tag), "Different metadata is not a match");
        tag.setLong("research", 124);
        check(!dependencies.contains("gregtech:gt.metaitem.01:32708", tag), "Different research NBT is not a match");
        check(!dependencies.contains("minecraft:stone", null), "Ordinary cross-mod inputs remain in the base library");
        check(!new RecipeNbtDependencies().contains("gregtech:gt.metaitem.01:32708", tag), "Scopes are independent");
        dependencies.add(new JsonParser().parse("{\"key\":\"test:wand:32767\",\"nbt\":\"{research:124L,display:{Name:\\\"Starcore\\\"}}\"}"));
        check(dependencies.contains("test:wand:7", tag), "NBT wildcard includes concrete metadata");
        check(dependencies.contains("test:wand", tag), "NBT wildcard includes zero metadata shorthand");
        tag.setLong("research", 125);
        check(!dependencies.contains("test:wand:7", tag), "NBT wildcard still requires exact tag identity");
        boolean invalid = false;
        try {
            dependencies.add(new JsonParser().parse("{\"key\":\"test:item\",\"nbt\":\"not nbt\"}"));
        } catch (IllegalArgumentException expected) {
            invalid = true;
        }
        check(invalid, "Malformed dependency must fail the scan instead of silently dropping it");
        RecipeNbtDependencies jsonOnly = new RecipeNbtDependencies();
        jsonOnly.add(new JsonParser().parse("{\"key\":\"test:toolbox\",\"nbt\":\"{message:1b}\"}"));
        final int[] visits = { 0 };
        jsonOnly.forEach((key, snapshot) -> {
            check(key.equals("test:toolbox") && snapshot.getBoolean("message"), "JSON-only dependency is enumerable");
            visits[0]++;
            snapshot.setBoolean("message", false);
        });
        NBTTagCompound expected = new NBTTagCompound();
        expected.setBoolean("message", true);
        check(visits[0] == 1 && jsonOnly.contains("test:toolbox", expected), "Enumeration does not mutate identity");
        NBTTagCompound namespaced = new NBTTagCompound();
        namespaced.setBoolean("gt5u.toolbox:DisplayCraftingMessage", true);
        namespaced.setString("literal", "text,{not:a:key:1b}");
        net.minecraft.nbt.NBTTagList children = new net.minecraft.nbt.NBTTagList();
        NBTTagCompound child = new NBTTagCompound();
        child.setInteger("mod:nested:key", 3);
        children.appendTag(child);
        namespaced.setTag("children", children);
        check(namespaced.equals(LegacyNbtParser.parse(namespaced.toString())),
            "Namespaced keys round-trip without changing strings or nested list order");
        System.out.println("Recipe NBT dependency regression passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
