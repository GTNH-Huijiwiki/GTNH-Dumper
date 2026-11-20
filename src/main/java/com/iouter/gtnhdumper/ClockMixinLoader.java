package com.iouter.gtnhdumper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

/** Item renderers that read the system clock directly; redirected to the frozen icon-export clock. */
@LateMixin
public final class ClockMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.gtnhdumper.clock.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();
        if (loadedMods.contains("Forestry")) {
            mixins.add("clock.ForestryButterflyClockMixin");
            mixins.add("clock.ForestryBeeClockMixin");
        }
        if (loadedMods.contains("DraconicEvolution")) mixins.add("clock.DraconicEarthClockMixin");
        if (loadedMods.contains("gadomancy")) {
            mixins.add("clock.GadomancyNodeClockMixin");
            mixins.add("clock.GadomancyFamiliarClockMixin");
        }
        if (loadedMods.contains("WitchingGadgets")) mixins.add("clock.WitchingGauntletClockMixin");
        if (loadedMods.contains("gregtech")) {
            mixins.add("clock.GTGlitchClockMixin");
            mixins.add("clock.GTGlitchMetaClockMixin");
        }
        return mixins;
    }
}
