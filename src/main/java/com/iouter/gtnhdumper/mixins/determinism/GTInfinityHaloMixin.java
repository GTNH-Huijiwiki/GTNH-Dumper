package com.iouter.gtnhdumper.mixins.determinism;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.iouter.gtnhdumper.common.utils.IconHalo;

/** The dark halo behind GregTech's infinity items; see {@link IconHalo}. */
@Pseudo
@Mixin(targets = "gregtech.common.render.items.InfinityRenderer", remap = false)
public abstract class GTInfinityHaloMixin {

    @Inject(method = "renderHalo", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gtnhdumper$recordHalo(CallbackInfo info) {
        if (!IconHalo.remove("gregtech:iconsets/HALO", 0xff141414, 10)) return;
        // The draws that follow rely on the state the cancelled method leaves behind.
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLStateManager.glDisable(GL11.GL_ALPHA_TEST);
        GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
        GLStateManager.glColor4f(20 / 255.0f, 20 / 255.0f, 20 / 255.0f, 1.0f);
        info.cancel();
    }
}
