package com.iouter.gtnhdumper.mixins.determinism;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.iouter.gtnhdumper.common.utils.IconHalo;

import fox.spiteful.avaritia.render.IHaloRenderItem;

/**
 * Halos of Avaritia's items and of everything else implementing its halo interface, drawn by its renderer and by the
 * Eternal Singularity's copy of it; see {@link IconHalo}.
 */
@Pseudo
@Mixin(
    targets = { "fox.spiteful.avaritia.render.FancyHaloRenderer",
        "singulariteam.eternalsingularity.render.EternalItemRenderer" },
    remap = false)
public abstract class AvaritiaHaloMixin {

    @Redirect(
        method = "renderItem",
        at = @At(
            value = "INVOKE",
            target = "Lfox/spiteful/avaritia/render/IHaloRenderItem;drawHalo(Lnet/minecraft/item/ItemStack;)Z"),
        require = 0)
    private boolean gtnhdumper$recordHalo(IHaloRenderItem item, ItemStack stack) {
        if (!item.drawHalo(stack)) return false;
        return !IconHalo.isRemoving() || !IconHalo.remove(
            item.getHaloTexture(stack)
                .getIconName(),
            item.getHaloColour(stack),
            item.getHaloSize(stack));
    }
}
