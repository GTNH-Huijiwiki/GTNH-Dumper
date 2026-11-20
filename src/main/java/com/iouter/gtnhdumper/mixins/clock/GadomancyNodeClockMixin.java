package com.iouter.gtnhdumper.mixins.clock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.iouter.gtnhdumper.common.utils.IconRenderClock;

/** Item icon export reads the frozen clock instead of the system time; normal play is unchanged. */
@Pseudo
@Mixin(targets = "makeo.gadomancy.client.renderers.item.ItemNodeRenderer", remap = false)
public abstract class GadomancyNodeClockMixin {

    @Redirect(
        method = "renderItemNode",
        at = @At(value = "INVOKE", target = "Ljava/lang/System;nanoTime()J"),
        require = 0)
    private static long gtnhdumper$frozenClock() {
        return IconRenderClock.nanoTime();
    }
}
