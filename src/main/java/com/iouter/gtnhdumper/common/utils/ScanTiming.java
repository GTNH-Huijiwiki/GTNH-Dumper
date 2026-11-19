package com.iouter.gtnhdumper.common.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Experimental timing of the recipe item scan: totals per phase and per NEI handler. */
public final class ScanTiming {

    private static final Map<String, long[]> PHASES = new LinkedHashMap<>();
    private static final Map<String, Map<String, Long>> HANDLERS = new LinkedHashMap<>();
    private static final Map<String, Long> HANDLER_RECIPES = new LinkedHashMap<>();

    private ScanTiming() {}

    public static synchronized void reset() {
        PHASES.clear();
        HANDLERS.clear();
        HANDLER_RECIPES.clear();
    }

    /** Adds elapsed nanoseconds to a phase total and, when a handler is given, to that handler. */
    public static synchronized void add(String phase, String handler, long nanos) {
        long[] total = PHASES.computeIfAbsent(phase, k -> new long[2]);
        total[0] += nanos;
        total[1]++;
        if (handler != null) HANDLERS.computeIfAbsent(handler, k -> new LinkedHashMap<>())
            .merge(phase, nanos, Long::sum);
    }

    public static synchronized void recipes(String handler, long count) {
        HANDLER_RECIPES.merge(handler, count, Long::sum);
    }

    public static synchronized Map<String, Object> report() {
        Map<String, Object> phases = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : PHASES.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("millis", e.getValue()[0] / 1_000_000L);
            row.put("calls", e.getValue()[1]);
            phases.put(e.getKey(), row);
        }
        List<Map<String, Object>> handlers = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> e : HANDLERS.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            long total = 0;
            row.put("handler", e.getKey());
            for (Map.Entry<String, Long> p : e.getValue()
                .entrySet()) {
                row.put(p.getKey() + "Millis", p.getValue() / 1_000_000L);
                total += p.getValue();
            }
            row.put("totalMillis", total / 1_000_000L);
            row.put("recipes", HANDLER_RECIPES.getOrDefault(e.getKey(), 0L));
            handlers.add(row);
        }
        handlers.sort((a, b) -> Long.compare((Long) b.get("totalMillis"), (Long) a.get("totalMillis")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("phases", phases);
        out.put("handlerCount", handlers.size());
        out.put("handlers", handlers);
        return out;
    }
}
