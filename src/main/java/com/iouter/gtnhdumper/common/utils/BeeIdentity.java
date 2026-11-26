package com.iouter.gtnhdumper.common.utils;

import net.minecraft.item.ItemStack;

import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.EnumBeeType;
import forestry.api.apiculture.IBee;
import forestry.api.apiculture.IBeeRoot;

/**
 * Which species and kind of bee an item stack is. Every queen is the same item, as is every princess, drone and larva:
 * the species is only in the stack's genome, so exported bee items carry it as {@code beeSpecies} and {@code beeType}.
 */
public final class BeeIdentity {

    /** The allele uid of the active species. */
    public final String species;
    /** {@code QUEEN}, {@code PRINCESS}, {@code DRONE} or {@code LARVAE}. */
    public final String type;

    private BeeIdentity(String species, String type) {
        this.species = species;
        this.type = type;
    }

    /** The identity of a bee with a genome, or null for anything else and without Forestry. */
    public static BeeIdentity of(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackTagCompound == null) return null;
        try {
            return Bees.identify(stack);
        } catch (NoClassDefFoundError withoutForestry) {
            return null;
        }
    }

    /** Forestry's classes are only touched from here, so that it stays optional. */
    private static final class Bees {

        static BeeIdentity identify(ItemStack stack) {
            IBeeRoot root = BeeManager.beeRoot;
            if (root == null || !root.isMember(stack)) return null;
            EnumBeeType type = root.getType(stack);
            IBee bee = root.getMember(stack);
            if (type == null || bee == null) return null;
            return new BeeIdentity(
                bee.getGenome()
                    .getPrimary()
                    .getUID(),
                type.name());
        }
    }
}
