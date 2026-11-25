package com.iouter.gtnhdumper.mixins.determinism;

import java.util.Random;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.iouter.gtnhdumper.common.utils.IconRenderClock;

/** The same random pulse as GregTech's infinity items, in Avaritia's halo renderer which GregTech copied it from. */
@Pseudo
@Mixin(targets = "fox.spiteful.avaritia.render.FancyHaloRenderer", remap = false)
public abstract class AvaritiaHaloPulseMixin {

    @Redirect(
        method = "renderItem",
        at = @At(value = "INVOKE", target = "Ljava/util/Random;nextGaussian()D"),
        require = 0)
    private double gtnhdumper$framePulse(Random random) {
        return IconRenderClock.pulseGaussian(random);
    }
}
