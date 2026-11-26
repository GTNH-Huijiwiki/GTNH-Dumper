package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

/**
 * Essentia costs that a mod rolls anew while the game runs (two Gadomancy infusion recipes). Such a list is empty until
 * something asks for its aspects, and while exporting every roll gives the middle of its range. Since Gadomancy 1.5.20
 * the list also tells the smallest and largest amount of each aspect, which Aspect Recipe Index shows as a tooltip.
 */
public final class RolledAspects {

    private RolledAspects() {}

    /** Makes the list fill itself; any other list stays as it is. */
    public static void roll(AspectList list) {
        list.getAspects();
    }

    /**
     * @param key the name each aspect is exported under.
     * @return {@code {smallest, largest}} by aspect for the list's aspects, or null for a list with fixed amounts. A
     *         smallest amount of 0 means the aspect is sometimes not required at all.
     */
    public static Map<String, int[]> ranges(AspectList list, Function<Aspect, String> key) {
        if (list == null) return null;
        Method smallest;
        Method largest;
        try {
            smallest = list.getClass()
                .getMethod("getMinAmount", Aspect.class);
            largest = list.getClass()
                .getMethod("getMaxAmount", Aspect.class);
        } catch (NoSuchMethodException fixedAmounts) {
            return null;
        }
        roll(list);
        Map<String, int[]> ranges = new LinkedHashMap<>();
        java.util.List<Aspect> aspects = new java.util.ArrayList<>(list.aspects.keySet());
        aspects.removeIf(java.util.Objects::isNull);
        // The list is refilled from a hash map: that order differs in every run.
        aspects.sort(java.util.Comparator.comparing(Aspect::getTag));
        try {
            for (Aspect aspect : aspects) {
                ranges.put(
                    key.apply(aspect),
                    new int[] { (Integer) smallest.invoke(list, aspect), (Integer) largest.invoke(list, aspect) });
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read the essentia cost range", error);
        }
        return ranges.isEmpty() ? null : ranges;
    }
}
