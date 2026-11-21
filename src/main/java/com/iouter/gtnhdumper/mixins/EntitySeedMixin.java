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

import com.iouter.gtnhdumper.common.utils.EntitySeeds;

/**
 * Item renderers that draw a mob (mob souls, spawners, trophies) create the entity while rendering. During icon export
 * the entity's random generator and UUID are seeded from its class, so its pose and random looks repeat across runs.
 * MobsInfo's mob list generation uses the same seeding (random boss names, slime sizes and thus health).
 */
@Mixin(Entity.class)
public abstract class EntitySeedMixin {

    @Shadow
    protected Random rand;

    @Shadow
    protected UUID entityUniqueID;

    /**
     * Also before {@code entityInit()}, which the constructor calls once {@code rand} exists: mods pick random looks
     * there (Special Mobs' random render scale goes into the data watcher), before the RETURN seeding below.
     */
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;entityInit()V"))
    private void gtnhdumper$seedBeforeInit(World world, CallbackInfo callback) {
        gtnhdumper$seed(world, callback);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gtnhdumper$seed(World world, CallbackInfo callback) {
        if (!EntitySeeds.active()) return;
        long seed = getClass().getName()
            .hashCode();
        rand.setSeed(seed);
        entityUniqueID = new UUID(seed, 0L);
    }
}
