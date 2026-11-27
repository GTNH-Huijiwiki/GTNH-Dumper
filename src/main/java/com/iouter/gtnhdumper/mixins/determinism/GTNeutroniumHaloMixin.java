package com.iouter.gtnhdumper.mixins.determinism;

import net.minecraftforge.client.IItemRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.iouter.gtnhdumper.common.utils.IconHalo;

/** The animated noise halo behind GregTech's cosmic neutronium items; see {@link IconHalo}. */
@Pseudo
@Mixin(targets = "gregtech.common.render.items.CosmicNeutroniumRenderer", remap = false)
public abstract class GTNeutroniumHaloMixin {

    @Inject(method = "renderHalo", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gtnhdumper$recordHalo(IItemRenderer.ItemRenderType type, CallbackInfo info) {
        if (type != IItemRenderer.ItemRenderType.INVENTORY
            || !IconHalo.remove("gregtech:iconsets/HALO_FUZZY", 0x4dffffff, 10)) return;
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 0x4d / 255.0f);
        info.cancel();
    }
}
