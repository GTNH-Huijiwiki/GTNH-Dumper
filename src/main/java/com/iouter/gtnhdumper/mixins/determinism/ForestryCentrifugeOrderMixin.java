package com.iouter.gtnhdumper.mixins.determinism;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.iouter.gtnhdumper.common.utils.StableOrder;

/**
 * Forestry keeps centrifuge products in a hash map. GregTech copies them into its own centrifuge recipes in that order,
 * so slot positions changed between launches, and NEI shows only the first nine, so for combs with more products even
 * the visible ones changed. Products are listed most likely first.
 */
@Pseudo
@Mixin(targets = "forestry.factory.recipes.CentrifugeRecipe", remap = false)
public abstract class ForestryCentrifugeOrderMixin {

    @Inject(method = "getAllProducts", at = @At("RETURN"), cancellable = true, require = 0)
    private void gtnhdumper$stableOrder(CallbackInfoReturnable<Map<ItemStack, Float>> callback) {
        List<Map.Entry<ItemStack, Float>> entries = new ArrayList<>(
            callback.getReturnValue()
                .entrySet());
        entries.sort(
            (a, b) -> a.getValue()
                .equals(b.getValue()) ? StableOrder.ITEMS.compare(a.getKey(), b.getKey())
                    : Float.compare(b.getValue(), a.getValue()));
        Map<ItemStack, Float> products = new LinkedHashMap<>();
        for (Map.Entry<ItemStack, Float> entry : entries) products.put(entry.getKey(), entry.getValue());
        callback.setReturnValue(products);
    }
}
