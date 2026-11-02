package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.util.Vec3Impl;

/** Reads both StructureLib's legacy coordinate sets and its compact occupied-space representation. */
public final class StructureOccupancy {

    private StructureOccupancy() {}

    public static Set<Vec3Impl> read(StructureDefinition<?> definition, String name) {
        try {
            Map<?, ?> spaces = (Map<?, ?>) field(StructureDefinition.class, "occupiedSpaces").get(definition);
            return decode(spaces.get(name));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read StructureLib occupied positions for " + name, error);
        }
    }

    static Set<Vec3Impl> decode(Object space) throws ReflectiveOperationException {
        if (space == null) return Collections.emptySet();
        if (space instanceof Set<?>positions) return coordinates(positions);
        Class<?> type = space.getClass();
        Object sparse = field(type, "sparseOccupied").get(space);
        if (sparse instanceof Set<?>positions) return coordinates(positions);
        BitSet occupied = (BitSet) field(type, "occupied").get(space);
        int sizeA = field(type, "sizeA").getInt(space);
        int sizeB = field(type, "sizeB").getInt(space);
        Set<Vec3Impl> positions = new HashSet<>();
        // Only visit set bits; do not scan or intern the entire bounding box.
        for (int bit = occupied.nextSetBit(0); bit >= 0; bit = occupied.nextSetBit(bit + 1)) {
            positions.add(new Vec3Impl(bit % sizeA, bit / sizeA % sizeB, bit / sizeA / sizeB));
            if (bit == Integer.MAX_VALUE) break;
        }
        return positions;
    }

    private static Set<Vec3Impl> coordinates(Set<?> positions) {
        Set<Vec3Impl> result = new HashSet<>();
        for (Object position : positions) result.add((Vec3Impl) position);
        return result;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
