package com.iouter.gtnhdumper.mixins.determinism;

import java.util.Comparator;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.iouter.gtnhdumper.common.utils.StableOrder;

import codechicken.nei.recipe.CatalystInfo;

/**
 * NEI orders a recipe handler's machines by priority and leaves equal priorities in registration order, which varies
 * between launches. Equal priorities are ordered by item.
 */
@Pseudo
@Mixin(targets = "codechicken.nei.recipe.CatalystInfoList", remap = false)
public abstract class NEICatalystOrderMixin {

    @SuppressWarnings("unchecked")
    @Inject(method = "sort()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtnhdumper$stableTies(CallbackInfo callback) {
        // NEI's own comparator puts the lower priority value first.
        ((List<CatalystInfo>) (Object) this).sort(
            Comparator.comparingInt(CatalystInfo::getPriority)
                .thenComparing(CatalystInfo::getStack, StableOrder.ITEMS));
        callback.cancel();
    }
}
