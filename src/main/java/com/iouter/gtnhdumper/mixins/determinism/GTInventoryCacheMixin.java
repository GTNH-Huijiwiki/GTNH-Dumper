package com.iouter.gtnhdumper.mixins.determinism;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.iouter.gtnhdumper.common.utils.IconRenderClock;

/**
 * GregTech caches each machine's inventory model in a display list keyed by meta id alone, built by whoever draws it
 * first. Amazing Trophies draws multiblock trophies with a {@code RenderBlocks} that skips faces hidden inside the
 * structure, so a controller seen first inside a trophy kept only its exposed faces and its own icon showed one or two
 * sides. While item icons are exported the cache is skipped: GregTech already draws immediately while another renderer
 * is capturing vertices, and this makes it take the same path. Normal play is unchanged; versions without the cache
 * have no such call.
 */
@Pseudo
@Mixin(targets = "gregtech.common.render.GTRendererBlock", remap = false)
public abstract class GTInventoryCacheMixin {

    @Redirect(
        method = "renderInventoryBlock(Lnet/minecraft/block/Block;IILnet/minecraft/client/renderer/RenderBlocks;)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizon/gtnhlib/client/renderer/TessellatorManager;isCurrentlyCapturing()Z"),
        require = 0)
    private boolean gtnhdumper$drawWithoutCache() {
        return IconRenderClock.bypassInventoryModelCache() || TessellatorManager.isCurrentlyCapturing();
    }
}
