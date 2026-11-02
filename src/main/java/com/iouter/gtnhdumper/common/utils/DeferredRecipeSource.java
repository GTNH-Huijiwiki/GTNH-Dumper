package com.iouter.gtnhdumper.common.utils;

import java.util.List;

/** Corrects the known GTNL tick-time registration where FML attributes the GT builder to GregTech. */
public final class DeferredRecipeSource {

    private static final String BUILDER = "com.science.gtnl.utils.recipes.RecipeBuilder.addTo";
    private static final String SCHEDULER = "com.science.gtnl.loader.LoadCompleteRecipeScheduler.loadRecipesAfterCompleteInit";

    private DeferredRecipeSource() {}

    public static String resolve(String recordedOwner, List<List<String>> stackTraces) {
        if (recordedOwner != null && !"gregtech".equals(recordedOwner)) return recordedOwner;
        if (stackTraces == null || stackTraces.isEmpty()) return recordedOwner;
        // Never inspect later traces: they may describe a modifier rather than the original registration.
        List<String> originalTrace = stackTraces.get(0);
        if (originalTrace == null || originalTrace.isEmpty()) return recordedOwner;
        // Direct copies do not enter this builder. Do not infer ownership merely from a GTNL caller below them.
        if (!isCall(originalTrace.get(0), BUILDER)) return recordedOwner;
        for (String frame : originalTrace) {
            if (isCall(frame, SCHEDULER)) return "sciencenotleisure";
        }
        return recordedOwner;
    }

    private static boolean isCall(String frame, String method) {
        if (frame == null) return false;
        int arguments = frame.indexOf('(');
        if (arguments < 0) return false;
        String qualifiedMethod = frame.substring(0, arguments);
        // Java 9+ stack traces can include a class loader and/or module prefix (e.g. Launch//).
        return qualifiedMethod.substring(qualifiedMethod.lastIndexOf('/') + 1)
            .equals(method);
    }
}
