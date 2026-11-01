package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizon.structurelib.util.Vec3Impl;

/** Large sparse bounds must not populate StructureLib's permanent coordinate pool. */
public final class StructureShapeRegression {

    private StructureShapeRegression() {}

    public static void main(String[] args) {
        try {
            occupiedSpaces();
            Field poolField = Vec3Impl.class.getDeclaredField("pool");
            poolField.setAccessible(true);
            Map<?, ?> pool = (Map<?, ?>) poolField.get(null);
            int before = pool.size();
            Map<Vec3Impl, Character> blocks = new HashMap<>();
            blocks.put(new Vec3Impl(256, 256, 256), 'A');
            blocks.put(new Vec3Impl(2, 1, 0), 'B');
            Set<Vec3Impl> occupied = new HashSet<>(blocks.keySet());
            occupied.add(new Vec3Impl(1, 1, 0));
            String[][] decoded = StructureShapeGrid.decode(blocks, occupied);
            check(decoded.length == 257 && decoded[0].length == 257, "Slice/row dimensions");
            check(decoded[256][256].length() == 257 && decoded[256][256].charAt(256) == 'A', "Far corner block");
            check(
                decoded[0][1].substring(0, 4)
                    .equals(" ~B "),
                "Controller and ordinary block precedence");
            check(decoded[0][0] == decoded[1][0], "Empty rows share immutable storage");
            check(pool.size() == before, "No bounding-box positions interned globally");
            blocks.clear();
            occupied.clear();
            occupied.add(new Vec3Impl(3, 2, 1));
            check(StructureShapeGrid.decode(blocks, occupied)[1][2].equals("   ~"), "Controller-only shape");
            System.out.println("Structure shape regression passed");
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static void occupiedSpaces() throws Exception {
        Class<?> type = Class.forName("com.gtnewhorizon.structurelib.structure.StructureDefinition$OccupiedSpace");
        Constructor<?> constructor = type.getDeclaredConstructor(int.class, int.class, int.class, long.class);
        constructor.setAccessible(true);
        Method add = type.getDeclaredMethod("add", int.class, int.class, int.class);
        add.setAccessible(true);
        Field bits = type.getDeclaredField("occupied");
        bits.setAccessible(true);
        Field poolField = Vec3Impl.class.getDeclaredField("pool");
        poolField.setAccessible(true);
        int poolSize = ((Map<?, ?>) poolField.get(null)).size();
        Object dense = constructor.newInstance(5, 4, 3, 2L);
        check(bits.get(dense) != null, "Fixture uses the real bitset representation");
        add.invoke(dense, 4, 3, 2);
        add.invoke(dense, 1, 0, 0);
        Set<Vec3Impl> expected = new HashSet<>(Arrays.asList(new Vec3Impl(4, 3, 2), new Vec3Impl(1, 0, 0)));
        check(
            StructureOccupancy.decode(dense)
                .equals(expected),
            "Non-cubic bitset coordinate order");
        Object sparse = constructor.newInstance(10000, 10000, 10000, 2L);
        check(bits.get(sparse) == null, "Fixture uses the real sparse representation");
        add.invoke(sparse, 9999, 2, 1);
        check(
            StructureOccupancy.decode(sparse)
                .equals(Collections.singleton(new Vec3Impl(9999, 2, 1))),
            "Sparse coordinates avoid bounding-box scans");
        check(
            StructureOccupancy.decode(expected)
                .equals(expected),
            "Legacy sets remain supported");
        check(
            StructureOccupancy.decode(null)
                .isEmpty(),
            "Missing shape is empty");
        Map<Vec3Impl, Character> blocks = new HashMap<>();
        blocks.put(new Vec3Impl(4, 3, 2), 'A');
        String[][] shape = StructureShapeGrid.decode(blocks, StructureOccupancy.decode(dense));
        check(shape[0][0].equals(" ~   ") && shape[2][3].equals("    A"), "Controller and block recovery");
        check(((Map<?, ?>) poolField.get(null)).size() == poolSize, "No global coordinate pool pollution");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
