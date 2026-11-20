package com.iouter.gtnhdumper.mixins.clock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.iouter.gtnhdumper.common.utils.IconRenderClock;

/** Item icon export reads the frozen clock instead of the system time; normal play is unchanged. */
@Pseudo
@Mixin(targets = "forestry.apiculture.render.RenderBeeItem", remap = false)
public abstract class ForestryBeeClockMixin {

    @Redirect(
        method = "getWingYaw",
        at = @At(value = "INVOKE", target = "Ljava/lang/System;currentTimeMillis()J"),
        require = 0)
    private static long gtnhdumper$frozenClock() {
        return IconRenderClock.currentTimeMillis();
    }
}
