package com.iouter.gtnhdumper.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** EnderIO 2.10.51 still renders the old model, but moved its texture into models/old. */
@Pseudo
@Mixin(targets = "crazypants.enderio.machine.hypercube.HyperCubeModel", remap = false)
public abstract class HyperCubeTextureMixin {

    @ModifyConstant(
        method = "render()V",
        constant = @Constant(stringValue = "enderio:models/hyperCube.png"),
        require = 1)
    private String gtnhdumper$relocatedTexture(String original) {
        return com.iouter.gtnhdumper.common.utils.TextureResourceFallback.hyperCube(original);
    }
}
