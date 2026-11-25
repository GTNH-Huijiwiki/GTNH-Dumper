package com.iouter.gtnhdumper.mixins.determinism;

import java.util.Random;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GTNHLib picks the flavour lines of several machine tooltips at random whenever resources load, before any export.
 * Seeds each pick with its translation key, so the same line wins in every run whatever order the lines are picked in.
 */
@Mixin(targets = "com.gtnewhorizon.gtnhlib.client.tooltip.LoreHandler", remap = false)
public abstract class GTNHLibLoreSeedMixin {

    @Shadow
    @Final
    private static Random RANDOM;

    @Inject(method = "getRandomLine", at = @At("HEAD"))
    private static void gtnhdumper$samePickEveryRun(String key, CallbackInfoReturnable<String> result) {
        RANDOM.setSeed(key.hashCode());
    }
}
