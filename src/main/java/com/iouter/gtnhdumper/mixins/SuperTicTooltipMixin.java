package com.iouter.gtnhdumper.mixins;

import java.util.HashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Unconfigured TiC materials have no SuperTiC potion effect, just like an explicit zero entry. */
@Pseudo
@Mixin(targets = "com.Zoko061602.SuperTic.TooltipEventHandler", remap = false)
public abstract class SuperTicTooltipMixin {

    @Redirect(
        method = { "addPartTooltips", "addPotionTooltips" },
        at = @At(value = "INVOKE", target = "Ljava/util/HashMap;get(Ljava/lang/Object;)Ljava/lang/Object;"),
        require = 1)
    private Object gtnhdumper$optionalMaterialEffect(HashMap<?, ?> values, Object material) {
        Object value = values.get(material);
        return value == null ? Integer.valueOf(0) : value;
    }
}
