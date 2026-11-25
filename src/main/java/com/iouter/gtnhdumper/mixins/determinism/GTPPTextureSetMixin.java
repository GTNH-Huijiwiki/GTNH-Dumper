package com.iouter.gtnhdumper.mixins.determinism;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gregtech.api.enums.Materials;
import gregtech.api.enums.TextureSet;
import gtPlusPlus.core.material.Material;

/**
 * GT++ gives an alloy the texture set most of its components use, counted in a hash map keyed by {@code TextureSet},
 * which has identity hash codes: on a tie the winner changed from one launch to the next, and with it the look of every
 * item of that material. Ties now go to the earlier component. This runs while materials are registered, so it applies
 * to the whole session, not only during an export.
 */
@Pseudo
@Mixin(targets = "gtPlusPlus.core.util.minecraft.MaterialUtils", remap = false)
public abstract class GTPPTextureSetMixin {

    @Inject(method = "getMostCommonTextureSet", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gtnhdumper$stableTie(List<Material> materials, CallbackInfoReturnable<TextureSet> callback) {
        Map<TextureSet, Integer> counts = new LinkedHashMap<>();
        for (Material material : materials) {
            TextureSet set = material.getTextureSet();
            if (set == null) set = Materials.Gold.mIconSet;
            if (set != null) counts.merge(set, 1, Integer::sum);
        }
        TextureSet best = Materials.Gold.mIconSet;
        int most = 0;
        for (Map.Entry<TextureSet, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > most) {
                most = entry.getValue();
                best = entry.getKey();
            }
        }
        callback.setReturnValue(best);
    }
}
