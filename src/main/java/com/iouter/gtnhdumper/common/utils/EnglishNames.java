package com.iouter.gtnhdumper.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iouter.gtnhdumper.GTNHDumper;
import com.iouter.gtnhdumper.common.dumper.FluidsDumper;

import codechicken.nei.guihook.GuiContainerManager;

/**
 * English names taken from a client that was started in en_US.
 * <p>
 * Some mods bake a name into the item at load time in whatever language the client started with (CropsNH passes
 * {@code StatCollector.translateToLocal(..)} to the machine constructor), so switching the language afterwards, as
 * {@link Utils#getEnglishTranslation} does, cannot reach them. The {@code englishnames} task writes the names of an
 * en_US launch; a normal export reads that file through the request's {@code englishNames} path and uses it for
 * {@code originalName}.
 * <p>
 * Items are keyed by short key, fluids by {@code fluid.<registry key>}. Items whose NBT embeds localized text get a
 * different short key in the two launches and keep the name found in the normal export.
 */
public final class EnglishNames {

    private final Map<String, String> items = new LinkedHashMap<>();
    private final Map<String, String> fluids = new LinkedHashMap<>();
    private final AtomicInteger appliedItems = new AtomicInteger();
    private final AtomicInteger appliedFluids = new AtomicInteger();

    private EnglishNames() {}

    /** Loads a file written by {@link #write}; rejects files that did not come from an en_US client. */
    public static EnglishNames read(File file) throws IOException {
        JsonObject root = GTNHDumper.GSON
            .fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), JsonObject.class);
        if (root == null || !root.has("language")
            || !"en_US".equals(
                root.get("language")
                    .getAsString()))
            throw new IllegalArgumentException("englishNames file was not exported by an en_US client: " + file);
        EnglishNames names = new EnglishNames();
        fill(root, "items", names.items);
        fill(root, "fluids", names.fluids);
        return names;
    }

    private static void fill(JsonObject root, String section, Map<String, String> target) {
        if (!root.has(section)) return;
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(section)
            .entrySet()) {
            if (!entry.getValue()
                .isJsonNull())
                target.put(
                    entry.getKey(),
                    entry.getValue()
                        .getAsString());
        }
    }

    /** English name for an item short key, or null when this launch did not see the item. */
    public String item(String shortKey) {
        String name = items.get(shortKey);
        if (name != null) appliedItems.incrementAndGet();
        return name;
    }

    /** English name for {@code fluid.<registry key>}, or null. */
    public String fluid(String key) {
        String name = fluids.get(key);
        if (name != null) appliedFluids.incrementAndGet();
        return name;
    }

    public int itemCount() {
        return items.size();
    }

    public int fluidCount() {
        return fluids.size();
    }

    public int appliedItems() {
        return appliedItems.get();
    }

    public int appliedFluids() {
        return appliedFluids.get();
    }

    /** Writes the names of every NEI item and registered fluid in the current language, which must be en_US. */
    public static void write(File file) throws IOException {
        String language = Minecraft.getMinecraft().gameSettings.language;
        if (!"en_US".equals(language)) throw new IllegalStateException(
            "englishnames must run in a client started with language en_US (this one: " + language + ")");
        Map<String, String> itemNames = new LinkedHashMap<>();
        List<ItemStack> stacks = AllItemStacks.getFilteredItemStacks();
        for (ItemStack identity : stacks) {
            try {
                // Name lookups of some mods write default NBT into the stack; query a copy.
                ItemStack stack = identity.copy();
                if (Utils.isStackInvalid(stack)) continue;
                itemNames.put(
                    Utils.getItemStackShortKey(identity),
                    EnumChatFormatting.getTextWithoutFormattingCodes(GuiContainerManager.itemDisplayNameShort(stack)));
            } catch (RuntimeException error) {
                GTNHDumper.LOG.warn("englishnames: skipped an item: {}", error.toString());
            }
        }
        Map<String, String> fluidNames = new LinkedHashMap<>();
        for (Map.Entry<String, Fluid> entry : FluidRegistry.getRegisteredFluids()
            .entrySet()) {
            if (entry.getValue() == null) continue;
            fluidNames.put("fluid." + entry.getKey(), FluidsDumper.getFluidName(entry.getValue()));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("language", language);
        root.put("items", itemNames);
        root.put("fluids", fluidNames);
        Files.write(
            file.toPath(),
            GTNHDumper.GSON.toJson(root)
                .getBytes(StandardCharsets.UTF_8));
    }
}
