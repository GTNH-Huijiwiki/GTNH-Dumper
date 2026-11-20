package com.iouter.gtnhdumper.common.dumper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import net.minecraft.util.ChatComponentTranslation;

import com.iouter.gtnhdumper.common.base.WikiDumper;
import com.iouter.gtnhdumper.common.utils.DimensionExportData;
import com.iouter.gtnhdumper.common.utils.Utils;

import gtneioreplugin.util.DimensionHelper;

public class GTNHDimensionDumper extends WikiDumper {

    public GTNHDimensionDumper() {
        super("tools.dump.gtnhdumper.gtnhdimension");
    }

    @Override
    public int getKeyIndex() {
        return 0;
    }

    @Override
    public String getKeyStr() {
        return "dimensions";
    }

    @Override
    public Iterable<Object[]> dumpObject(int mode) {
        LinkedList<Object[]> list = new LinkedList<>();

        Map<DimensionHelper.Dimension, String> originalNameMap = new HashMap<>();
        DimensionExportData dimensions = DimensionExportData.discover();

        Utils.getEnglishTranslation(() -> {
            dimensions.captureEnglishNames();
            for (DimensionHelper.Dimension dimension : DimensionHelper.ALL_DIMENSIONS) {
                originalNameMap
                    .put(dimension, DimensionHelper.getDimLocalizedName(DimensionHelper.getFullName(dimension.abbr())));
            }
        });

        Set<Integer> represented = new HashSet<>();
        Set<String> usedKeys = new HashSet<>();
        String[] fields = header();
        for (DimensionHelper.Dimension dimension : DimensionHelper.ALL_DIMENSIONS) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("abbreviatedName", dimension.abbr());
            info.put("internalName", dimension.internalName());
            info.put("fullName", dimension.fullName());
            info.put("originalName", originalNameMap.get(dimension));
            info.put(
                "localizedName",
                DimensionHelper.getDimLocalizedName(DimensionHelper.getFullName(dimension.abbr())));
            String tier = dimension.tierKey()
                .replace("gtnop.tier.", "");
            try {
                info.put("tier", Integer.valueOf(tier));
            } catch (NumberFormatException ignored) {
                info.put("tier", tier);
            }
            info.put(
                "stoneTypes",
                dimension.stoneTypes()
                    .stream()
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.toList()));
            Map<String, Object> data = dimensions.resolveGtnei(info);
            if (data.get("dimensionId") instanceof Integer) represented.add((Integer) data.get("dimensionId"));
            usedKeys.add(dimension.abbr());
            list.add(toRow(fields, data));
        }
        for (Map<String, Object> dimension : dimensions.additionalDimensions(represented, usedKeys)) {
            list.add(toRow(fields, dimension));
        }
        return list;
    }

    private static Object[] toRow(String[] fields, Map<String, Object> dimension) {
        Object[] row = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) row[i] = dimension.get(fields[i]);
        return row;
    }

    @Override
    public String[] header() {
        ArrayList<String> fields = new ArrayList<>(Arrays.asList("key", "gtneiInfo"));
        fields.addAll(Arrays.asList(DimensionExportData.HEADERS));
        return fields.toArray(new String[0]);
    }

    @Override
    public ChatComponentTranslation dumpMessage(File file) {
        return new ChatComponentTranslation(
            "nei.options.tools.dump.gtnhdumper.gtnhdimension.dumped",
            "dumps/" + file.getName());
    }
}
