package com.iouter.gtnhdumper.common.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Uses the actual GTNL deferred-registration trace shape, without launching Minecraft. */
public final class DeferredRecipeSourceRegression {

    private DeferredRecipeSourceRegression() {}

    public static void main(String[] args) {
        List<String> original = Arrays.asList(
            "Launch//com.science.gtnl.utils.recipes.RecipeBuilder.addTo(:784)",
            "Launch//com.science.gtnl.utils.recipes.RecipeBuilder.addTo(:873)",
            "Launch//com.science.gtnl.common.recipe.gtnl.SteamCarpenterRecipe.loadRecipes(:58)",
            "Launch//com.science.gtnl.loader.LoadCompleteRecipeScheduler.loadRecipesAfterCompleteInit(:42)");
        List<List<String>> traces = Collections.singletonList(original);
        expect("sciencenotleisure", "gregtech", traces);
        expect("sciencenotleisure", null, traces);
        expect("sciencenotleisure", "sciencenotleisure", traces);
        expect("othermod", "othermod", traces);
        expect("gregtech", "gregtech", null);
        expect(null, null, Collections.emptyList());
        expect("gregtech", "gregtech", Collections.singletonList(null));
        expect("gregtech", "gregtech", Collections.singletonList(Collections.emptyList()));
        expect("gregtech", "gregtech", Collections.singletonList(original.subList(0, 3)));
        List<String> copied = Arrays
            .asList("Launch//com.science.gtnl.common.recipe.gtnl.SomeRecipe.loadRecipes(:42)", original.get(3));
        expect("gregtech", "gregtech", Collections.singletonList(copied));
        expect("gregtech", "gregtech", Arrays.asList(copied, original));
        expect("sciencenotleisure", "gregtech", Arrays.asList(original, copied));
        expect(
            "sciencenotleisure",
            "gregtech",
            Collections.singletonList(
                Arrays.asList(
                    original.get(0)
                        .replace("Launch//", ""),
                    original.get(3))));
        expect(
            "gregtech",
            "gregtech",
            Collections.singletonList(
                Arrays.asList(
                    original.get(0)
                        .replace(".addTo", ".addToOther"),
                    original.get(3))));
        System.out.println("Deferred recipe source regression checks passed (14 cases)");
    }

    private static void expect(String expected, String recorded, List<List<String>> traces) {
        String actual = DeferredRecipeSource.resolve(recorded, traces);
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
