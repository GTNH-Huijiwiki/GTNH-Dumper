package com.iouter.gtnhdumper.common.utils;

import java.io.IOException;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;

/** Prefer resource-pack overrides; use the relocated asset only if the original is absent. */
public final class TextureResourceFallback {

    private TextureResourceFallback() {}

    public static String hyperCube(String original) {
        return hyperCube(original, TextureResourceFallback::exists);
    }

    static String hyperCube(String original, Predicate<String> available) {
        if (available.test(original)) return original;
        String relocated = "enderio:models/old/hyperCube.png";
        return available.test(relocated) ? relocated : original;
    }

    private static boolean exists(String path) {
        try {
            IResource resource = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(new ResourceLocation(path));
            resource.getInputStream()
                .close();
            return true;
        } catch (IOException missing) {
            return false;
        }
    }
}
