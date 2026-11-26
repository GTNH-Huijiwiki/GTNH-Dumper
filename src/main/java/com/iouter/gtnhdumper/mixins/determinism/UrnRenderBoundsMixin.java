package com.iouter.gtnhdumper.mixins.determinism;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.iouter.gtnhdumper.common.utils.IconRenderClock;

/**
 * Thaumic Exploration's urns narrow the block's bounds and expect the faces to follow, but {@code RenderBlocks} draws
 * with bounds of its own, which stay a full cube. The side faces then sit on the outer cube while their textures leave
 * two transparent pixels at each edge, so the icon has a slit along every corner. The three layers of faces and their
 * textures only meet with the bounds the renderer asked for, which this sets while icons are exported.
 */
@Pseudo
@Mixin(targets = "flaxbeard.thaumicexploration.client.render.BlockEverfullUrnRenderer", remap = false)
public abstract class UrnRenderBoundsMixin {

    @Inject(method = "renderInventoryBlock", at = @At("HEAD"), require = 0)
    private void gtnhdumper$insetFaces(Block block, int meta, int modelId, RenderBlocks renderer, CallbackInfo ci) {
        if (IconRenderClock.isTimeFrozen()) renderer.setRenderBounds(0.125, 0, 0.125, 0.875, 1, 0.875);
    }

    @Inject(method = "renderInventoryBlock", at = @At("RETURN"), require = 0)
    private void gtnhdumper$restoreBounds(Block block, int meta, int modelId, RenderBlocks renderer, CallbackInfo ci) {
        if (IconRenderClock.isTimeFrozen()) renderer.setRenderBounds(0, 0, 0, 1, 1, 1);
    }
}
