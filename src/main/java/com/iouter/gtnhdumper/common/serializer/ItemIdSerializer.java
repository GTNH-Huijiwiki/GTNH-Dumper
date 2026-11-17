package com.iouter.gtnhdumper.common.serializer;

import java.lang.reflect.Type;

import net.minecraft.item.ItemStack;

import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.gtnewhorizon.gtnhlib.util.data.ItemId;

public final class ItemIdSerializer implements JsonSerializer<ItemId> {

    @Override
    public JsonElement serialize(ItemId source, Type type, JsonSerializationContext context) {
        // ItemId defaults to zero quantity, which means non-consumable in recipe exports.
        return context.serialize(source.toStack(1), ItemStack.class);
    }
}
