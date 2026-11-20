package com.iouter.gtnhdumper.mixins;

import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.iouter.gtnhdumper.common.utils.IconRenderClock;

/**
 * Item renderers that draw a mob (mob souls, spawners, trophies) create the entity while rendering. During icon export
 * the entity's random generator and UUID are seeded from its class, so its pose and random looks repeat across runs.
 */
@Mixin(Entity.class)
public abstract class EntitySeedMixin {

    @Shadow
    protected Random rand;

    @Shadow
    protected UUID entityUniqueID;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gtnhdumper$seed(World world, CallbackInfo callback) {
        if (!IconRenderClock.isFrozen()) return;
        long seed = getClass().getName()
            .hashCode();
        rand.setSeed(seed);
        entityUniqueID = new UUID(seed, 0L);
    }
}
