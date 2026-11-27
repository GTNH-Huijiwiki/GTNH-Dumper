package com.iouter.gtnhdumper.common.dumper;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.iouter.gtnhdumper.CommonProxy;

import gregtech.api.enums.CondensateType;
import gregtech.api.enums.Materials;
import gregtech.api.util.GTUtility;
import gregtech.common.items.ItemFluidDisplay;
import gregtech.common.render.items.UniversiumRenderer;

/**
 * NEI lists GregTech's fluid display items without the NBT that GregTech adds when it shows a fluid. GregTech's
 * renderer needs it for two things: molten Universium's star field (without it a plain dark texture) and the
 * animated overlay of condensate fluids. Only those get the tag; other fluids look the same either way.
 */
final class GtFluidDisplayTag {

    private GtFluidDisplayTag() {}

    /** The stack to draw: {@code stack} itself, or a copy carrying GregTech's display NBT. */
    static ItemStack forRender(ItemStack stack) {
        return CommonProxy.isGTLoaded ? tagged(stack) : stack;
    }

    private static ItemStack tagged(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemFluidDisplay) || stack.hasTagCompound()) return stack;
        Fluid fluid = FluidRegistry.getFluid(stack.getItemDamage());
        if (fluid == null) return stack;
        ItemStack display = GTUtility.getFluidDisplayStack(new FluidStack(fluid, 1), false, true);
        if (display == null || !display.hasTagCompound()) return stack;
        Materials material = Materials.get(
            display.getTagCompound()
                .getString("mFluidMaterialName"));
        boolean starField = material != null && material.renderer instanceof UniversiumRenderer;
        if (!starField && CondensateType.getCondensateType(fluid) == null) return stack;
        ItemStack copy = stack.copy();
        copy.setTagCompound(
            (NBTTagCompound) display.getTagCompound()
                .copy());
        return copy;
    }
}
