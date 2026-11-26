package com.iouter.gtnhdumper.common.utils;

import java.util.Map;
import java.util.TreeMap;

import forestry.api.genetics.AlleleManager;
import forestry.api.genetics.IAllele;

/** Names of every Forestry allele in the current language; kept apart so that Forestry stays optional. */
final class ForestryAlleleNames {

    private ForestryAlleleNames() {}

    static Map<String, String> all() {
        Map<String, String> names = new TreeMap<>();
        for (IAllele allele : AlleleManager.alleleRegistry.getRegisteredAlleles()
            .values()) {
            try {
                names.put(allele.getUID(), allele.getName());
            } catch (RuntimeException error) {
                com.iouter.gtnhdumper.GTNHDumper.LOG.warn("englishnames: skipped an allele: {}", error.toString());
            }
        }
        return names;
    }
}
