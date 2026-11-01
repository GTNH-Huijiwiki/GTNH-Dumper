package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;

import com.iouter.gtnhdumper.SuperTicMixinLoader;
import com.iouter.gtnhdumper.mixins.SuperTicTooltipMixin;

public final class SuperTicTooltipRegression {

    private SuperTicTooltipRegression() {}

    public static void main(String[] args) throws Exception {
        tooltips();
        SuperTicMixinLoader loader = new SuperTicMixinLoader();
        check(
            loader.getMixins(Collections.emptySet())
                .isEmpty(),
            "SuperTiC remains optional");
        check(
            loader.getMixins(Collections.singleton("SuperTic"))
                .equals(Collections.singletonList("SuperTicTooltipMixin")),
            "Select SuperTiC hook");
        check(
            loader.getClass()
                .getClassLoader()
                .getResource(loader.getMixinConfig()) != null,
            "SuperTiC config packaged");
        System.out.println("SuperTiC tooltip regression passed");
    }

    private static void tooltips() throws Exception {
        Method lookup = SuperTicTooltipMixin.class
            .getDeclaredMethod("gtnhdumper$optionalMaterialEffect", HashMap.class, Object.class);
        lookup.setAccessible(true);
        SuperTicTooltipMixin hook = new SuperTicTooltipMixin() {};
        HashMap<Integer, Integer> values = new HashMap<>();
        values.put(1, -5);
        values.put(2, 3);
        values.put(3, null);
        check(
            lookup.invoke(hook, values, 999)
                .equals(0),
            "Absent effect does not unbox null");
        check(
            lookup.invoke(hook, values, 3)
                .equals(0),
            "Null effect does not unbox null");
        check(
            lookup.invoke(hook, values, 1)
                .equals(-5),
            "Negative effect retained");
        check(
            lookup.invoke(hook, values, 2)
                .equals(3),
            "Configured amplitude retained");
        check(values.size() == 3, "Tooltip lookup does not modify SuperTiC configuration");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
