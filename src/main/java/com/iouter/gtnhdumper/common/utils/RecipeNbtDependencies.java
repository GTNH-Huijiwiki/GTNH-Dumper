package com.iouter.gtnhdumper.common.utils;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class RecipeNbtDependencies {

    private final Set<Identity> identities = new HashSet<>();

    public void add(JsonElement value) {
        if (value == null) return;
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) add(child);
        } else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("key") && object.has("nbt") && object.get("key").isJsonPrimitive()
                && object.get("nbt").isJsonPrimitive()) {
                try {
                    NBTBase tag = JsonToNBT.func_150315_a(object.get("nbt").getAsString());
                    if (tag instanceof NBTTagCompound compound && !compound.hasNoTags()) {
                        identities.add(new Identity(object.get("key").getAsString(), compound));
                    }
                } catch (net.minecraft.nbt.NBTException error) {
                    throw new IllegalArgumentException("Invalid NBT in recipe dependency", error);
                }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) add(entry.getValue());
        }
    }

    public boolean contains(String rawKey, NBTTagCompound tag) {
        if (tag == null) return false;
        if (identities.contains(new Identity(rawKey, tag))) return true;
        int separator = rawKey.lastIndexOf(':');
        String registry = separator > rawKey.indexOf(':') ? rawKey.substring(0, separator) : rawKey;
        return identities.contains(new Identity(registry + ":32767", tag));
    }

    public void forEach(BiConsumer<String, NBTTagCompound> consumer) {
        for (Identity identity : identities) {
            consumer.accept(identity.rawKey, (NBTTagCompound) identity.tag.copy());
        }
    }

    private static final class Identity {

        private final String rawKey;
        private final NBTTagCompound tag;

        private Identity(String rawKey, NBTTagCompound tag) {
            this.rawKey = rawKey;
            this.tag = (NBTTagCompound) tag.copy();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Identity identity && Objects.equals(rawKey, identity.rawKey)
                && Objects.equals(tag, identity.tag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rawKey, tag);
        }
    }
}
