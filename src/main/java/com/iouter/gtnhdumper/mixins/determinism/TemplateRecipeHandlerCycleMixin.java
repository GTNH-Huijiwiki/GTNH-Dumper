package com.iouter.gtnhdumper.mixins.determinism;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * NEI starts each handler's display cycle at the current system time. Several handlers pick the shown example while
 * loading (Extra Utilities microblock material, crop and fuel examples), so exports differed between runs. New handler
 * instances start the cycle at 0; in the recipe GUI this only fixes the phase of the first permutation shown.
 */
@Mixin(value = TemplateRecipeHandler.class, remap = false)
public abstract class TemplateRecipeHandlerCycleMixin {

    @Shadow
    public int cycleticks;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gtnhdumper$fixedCycle(CallbackInfo callback) {
        cycleticks = 0;
    }
}
