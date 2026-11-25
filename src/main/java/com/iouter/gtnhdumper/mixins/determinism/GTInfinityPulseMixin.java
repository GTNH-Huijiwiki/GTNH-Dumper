package com.iouter.gtnhdumper.mixins.determinism;

import java.util.Random;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.iouter.gtnhdumper.common.utils.IconRenderClock;

/**
 * GregTech's infinity items pulse with a random size on every render. Item icon export takes the size from the
 * animation frame instead, so frames still shimmer but repeated exports match; normal play is unchanged.
 */
@Pseudo
@Mixin(targets = "gregtech.common.render.items.InfinityRenderer", remap = false)
public abstract class GTInfinityPulseMixin {

    @Redirect(
        method = "renderPulse",
        at = @At(value = "INVOKE", target = "Ljava/util/Random;nextGaussian()D"),
        require = 0)
    private static double gtnhdumper$framePulse(Random random) {
        return IconRenderClock.pulseGaussian(random);
    }
}
