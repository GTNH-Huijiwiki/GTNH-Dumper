package com.iouter.gtnhdumper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

/** Fixes exported data that depends on the system clock, unseeded randomness or what happened to run first. */
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
        if (loadedMods.contains("gregtech")) {
            mixins.add("determinism.GTInventoryCacheMixin");
            mixins.add("determinism.GTInfinityPulseMixin");
        }
        if (loadedMods.contains("Avaritia")) mixins.add("determinism.AvaritiaHaloPulseMixin");
        if (loadedMods.contains("eternalsingularity")) mixins.add("determinism.EternalSingularityPulseMixin");
        return mixins;
    }
}
