package com.iouter.gtnhdumper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/** Loads mixins into vanilla classes, which are already loaded before late mixins apply. */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("GTNHDumperEarlyMixins")
@IFMLLoadingPlugin.SortingIndex(1001)
public final class EarlyMixinPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.gtnhdumper.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return java.util.Arrays.asList("MinecraftSystemTimeMixin", "EntitySeedMixin");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "com.iouter.gtnhdumper.DeterminismTransformer" };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
