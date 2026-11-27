package com.iouter.gtnhdumper.common.dumper;

import net.minecraft.item.ItemStack;

import com.iouter.gtnhdumper.CommonProxy;
import com.iouter.gtnhdumper.common.utils.Utils;

import gregtech.client.handler.CondensateAnimationTickHandler;
import gregtech.common.items.ItemFluidDisplay;

/**
 * GregTech draws an animated overlay over condensate fluids. Its frame is a static counter advanced every client tick,
 * so
 * the export sets it before each render: stills show frame 0 on every run, and the frames of an animation are stepped.
 * Both GregTech's and NEI's fluid display items are drawn by the same GregTech renderer.
 */
final class GtCondensateOverlay {

    static final int FRAMES = 64;

    private GtCondensateOverlay() {}

    static void show(int frame) {
        if (CommonProxy.isGTLoaded) Gt.show(frame);
    }

    static boolean isFluidDisplay(ItemStack stack) {
        if (!CommonProxy.isGTLoaded || stack == null || stack.getItem() == null) return false;
        return Gt.isGtDisplay(stack) || Utils.getItemStackShortKey(stack)
            .startsWith("NotEnoughItems:neiFluidDisplay");
    }

    /** GregTech classes are only loaded when GregTech is present. */
    private static final class Gt {

        static void show(int frame) {
            CondensateAnimationTickHandler.currentFrame = Math.floorMod(frame, FRAMES);
        }

        static boolean isGtDisplay(ItemStack stack) {
            return stack.getItem() instanceof ItemFluidDisplay;
        }
    }
}
