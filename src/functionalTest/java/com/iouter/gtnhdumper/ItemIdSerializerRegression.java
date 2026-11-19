package com.iouter.gtnhdumper;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import com.gtnewhorizon.gtnhlib.util.data.ItemId;
import com.iouter.gtnhdumper.common.serializer.ItemIdSerializer;

public final class ItemIdSerializerRegression {

    public static void main(String[] args) {
        Item item = new Item();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("research", "nanites");
        ItemId identity = ItemId.create(item, 7, tag);
        Gson gson = new GsonBuilder().registerTypeAdapter(ItemId.class, new ItemIdSerializer())
            .registerTypeAdapter(ItemStack.class, (JsonSerializer<ItemStack>) (stack, type, context) -> {
                if (stack.getItem() != item) throw new AssertionError("Item identity lost");
                JsonObject result = new JsonObject();
                result.addProperty("metadata", stack.getItemDamage());
                result.addProperty("amount", stack.stackSize);
                result.addProperty(
                    "nbt",
                    stack.getTagCompound()
                        .getString("research"));
                // toStack must not let serializers mutate the immutable source's NBT.
                stack.getTagCompound()
                    .setString("research", "changed");
                return result;
            })
            .create();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stack", identity);
        metadata.put("speedBoost", 2.5);
        JsonObject result = gson.toJsonTree(metadata)
            .getAsJsonObject();
        JsonObject stack = result.getAsJsonObject("stack");
        if (stack.get("metadata")
            .getAsInt() != 7
            || stack.get("amount")
                .getAsInt() != 1
            || !stack.get("nbt")
                .getAsString()
                .equals("nanites")
            || !identity.getTag()
                .getString("research")
                .equals("nanites")
            || result.get("speedBoost")
                .getAsDouble() != 2.5)
            throw new AssertionError(result);
        if (!gson.toJsonTree(null, ItemId.class)
            .isJsonNull()) throw new AssertionError("Null identity");
        System.out.println("ItemId metadata serialization regression passed");
    }
}
