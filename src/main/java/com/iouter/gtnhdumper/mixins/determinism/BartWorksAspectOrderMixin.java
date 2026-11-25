package com.iouter.gtnhdumper.mixins.determinism;

import java.util.Arrays;
import java.util.Comparator;

import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import bartworks.system.material.Werkstoff;
import thaumcraft.api.aspects.Aspect;

/**
 * BartWorks collects a material's aspects in a hash set, so they are registered in another order in every run.
 * Thaumcraft keeps six aspects per item and drops one of the smallest, the first in that order: which aspect a
 * material part has, and which aspects items crafted from it derive, changed with it. Registers them by aspect name.
 */
@Mixin(value = Werkstoff.class, remap = false)
public abstract class BartWorksAspectOrderMixin {

    @Inject(method = "getTCAspects(I)[Lorg/apache/commons/lang3/tuple/Pair;", at = @At("RETURN"))
    private void gtnhdumper$sortByName(int multiplier, CallbackInfoReturnable<Pair<Object, Integer>[]> result) {
        Pair<Object, Integer>[] aspects = result.getReturnValue();
        if (aspects == null) return;
        // The method returns its own copy of the cached array.
        Arrays.sort(
            aspects,
            Comparator
                .<Pair<Object, Integer>, String>comparing(
                    pair -> pair.getKey() instanceof Aspect aspect ? aspect.getTag() : String.valueOf(pair.getKey()))
                .thenComparing(Pair::getValue));
    }
}
