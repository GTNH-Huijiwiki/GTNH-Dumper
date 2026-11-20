package com.iouter.gtnhdumper.common.serializer;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

public class AspectListSerializer implements JsonSerializer<AspectList> {

    @Override
    public JsonElement serialize(AspectList src, Type typeOfSrc, JsonSerializationContext context) {
        if (src == null) {
            return null;
        }
        JsonObject obj = new JsonObject();
        LinkedHashMap<Aspect, Integer> aspects = src.aspects;
        if (aspects == null || aspects.isEmpty()
            || aspects.keySet()
                .stream()
                .allMatch(Objects::isNull)) {
            return null;
        }
        // 部分模组用 HashMap 拼出要素表，插入顺序每次启动不同；按要素注册顺序输出（基础要素在前）。
        java.util.List<Aspect> registered = new java.util.ArrayList<>(Aspect.aspects.values());
        java.util.List<Aspect> keys = new java.util.ArrayList<>();
        for (Aspect aspect : aspects.keySet()) if (aspect != null) keys.add(aspect);
        keys.sort(java.util.Comparator.comparingInt((Aspect aspect) -> {
            int index = registered.indexOf(aspect);
            return index < 0 ? Integer.MAX_VALUE : index;
        })
            .thenComparing(Aspect::getTag));
        for (Aspect aspect : keys) {
            obj.addProperty(aspect.getName(), aspects.get(aspect));
        }
        return obj;
    }
}
