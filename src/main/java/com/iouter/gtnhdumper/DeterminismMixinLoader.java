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
        if (loadedMods.contains("NotEnoughItems")) {
            mixins.add("determinism.TemplateRecipeHandlerCycleMixin");
            mixins.add("determinism.NEICatalystOrderMixin");
        }
        if (loadedMods.contains("Forestry")) mixins.add("determinism.ForestryCentrifugeOrderMixin");
        if (loadedMods.contains("gtnhlib")) mixins.add("determinism.GTNHLibLoreSeedMixin");
        if (loadedMods.contains("gregtech") && loadedMods.contains("Thaumcraft")) {
            // BartWorks is part of the GregTech jar.
            mixins.add("determinism.BartWorksAspectOrderMixin");
        }
        if (loadedMods.contains("mobsinfo")) mixins.add("determinism.MobRecipeLoaderSeedMixin");
        if (loadedMods.contains("gregtech")) {
            mixins.add("determinism.GTInventoryCacheMixin");
            mixins.add("determinism.GTInfinityPulseMixin");
            mixins.add("determinism.GTInfinityHaloMixin");
            mixins.add("determinism.GTNeutroniumHaloMixin");
            mixins.add("determinism.GTPPTextureSetMixin");
        }
        if (loadedMods.contains("ThaumicExploration")) mixins.add("determinism.UrnRenderBoundsMixin");
        if (loadedMods.contains("Avaritia")) {
            mixins.add("determinism.AvaritiaHaloPulseMixin");
            // Also targets the Eternal Singularity's renderer, which cannot exist without Avaritia.
            mixins.add("determinism.AvaritiaHaloMixin");
        }
        if (loadedMods.contains("eternalsingularity")) mixins.add("determinism.EternalSingularityPulseMixin");
        return mixins;
    }
}
