package com.iouter.gtnhdumper;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

@LateMixin
public final class EnderIOMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.gtnhdumper.enderio.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return loadedMods.contains("EnderIO") ? Collections.singletonList("HyperCubeTextureMixin")
            : Collections.emptyList();
    }
}
