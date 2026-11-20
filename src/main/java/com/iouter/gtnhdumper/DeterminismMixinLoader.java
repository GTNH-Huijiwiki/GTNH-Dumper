package com.iouter.gtnhdumper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

/** Fixes run-to-run differences in exported data that come from the system clock or unseeded randomness. */
@LateMixin
public final class DeterminismMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.gtnhdumper.determinism.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();
        if (loadedMods.contains("NotEnoughItems")) mixins.add("determinism.TemplateRecipeHandlerCycleMixin");
        if (loadedMods.contains("mobsinfo")) mixins.add("determinism.MobRecipeLoaderSeedMixin");
        return mixins;
    }
}
