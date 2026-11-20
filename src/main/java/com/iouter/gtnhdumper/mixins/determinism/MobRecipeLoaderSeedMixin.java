package com.iouter.gtnhdumper.mixins.determinism;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.iouter.gtnhdumper.common.utils.EntitySeeds;

/**
 * MobsInfo builds its mob list from freshly constructed entities. Constructors roll random names (Hardcore Ender
 * Expansion bosses) and sizes (slimes, hence their health); seed those entities like the icon export does.
 */
@Pseudo
@Mixin(targets = "com.kuba6000.mobsinfo.loader.MobRecipeLoader", remap = false)
public abstract class MobRecipeLoaderSeedMixin {

    @Inject(method = "generateMobRecipeMap", at = @At("HEAD"), require = 0)
    private static void gtnhdumper$seedEntities(CallbackInfo callback) {
        EntitySeeds.begin();
    }

    @Inject(method = "generateMobRecipeMap", at = @At("RETURN"), require = 0)
    private static void gtnhdumper$restoreEntities(CallbackInfo callback) {
        EntitySeeds.end();
    }
}
