package com.iouter.gtnhdumper.mixins;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.iouter.gtnhdumper.common.utils.IconRenderClock;

/** Item glints and many item renderers animate from this clock; icon export pins it to one instant. */
@Mixin(Minecraft.class)
public abstract class MinecraftSystemTimeMixin {

    @Inject(method = "getSystemTime", at = @At("HEAD"), cancellable = true)
    private static void gtnhdumper$frozenClock(CallbackInfoReturnable<Long> callback) {
        if (IconRenderClock.isTimeFrozen()) callback.setReturnValue(IconRenderClock.frozenMillis());
    }
}
