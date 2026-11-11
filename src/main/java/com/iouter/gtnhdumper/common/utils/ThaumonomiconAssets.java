package com.iouter.gtnhdumper.common.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;

import com.iouter.gtnhdumper.GTNHDumper;

/**
 * Copies the textures a Thaumonomicon export refers to, so the front end can be rebuilt without the game.
 * Files are read through the resource manager (resource packs apply) and written unchanged to
 * {@code <dumps>/tc4thaumonomicon-assets/<domain>/<path>}. A resource is identified as {@code domain:path}.
 */
public final class ThaumonomiconAssets {

    public static final String DIRECTORY = "tc4thaumonomicon-assets";

    /** Thaumcraft's {@code <IMG>domain:path:u:v:width:height:scale</IMG>} text markup. */
    private static final Pattern IMG = Pattern.compile("<IMG>(.*?)</IMG>", Pattern.DOTALL);

    private final Path root;
    private final Set<String> done = new LinkedHashSet<>();
    private final List<String> missing = new ArrayList<>();

    public ThaumonomiconAssets(File dumpFile) {
        File parent = dumpFile.getAbsoluteFile()
            .getParentFile();
        this.root = new File(parent, DIRECTORY).toPath()
            .toAbsolutePath()
            .normalize();
    }

    public static String id(ResourceLocation location) {
        return location == null ? null : location.getResourceDomain() + ":" + location.getResourcePath();
    }

    /** Copies one texture. Returns its {@code domain:path} id, or null for a null location. */
    public String add(ResourceLocation location) {
        if (location == null) return null;
        String id = id(location);
        if (!done.add(id)) return id;
        try {
            Path target = root.resolve(location.getResourceDomain())
                .resolve(location.getResourcePath())
                .normalize();
            if (!target.startsWith(root)) throw new IOException("Resource path leaves the asset directory");
            IResource resource = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(location);
            Files.createDirectories(target.getParent());
            try (InputStream in = resource.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            missing.add(id);
            GTNHDumper.LOG.warn("Thaumonomicon asset {} could not be copied: {}", id, e.toString());
        }
        return id;
    }

    /** Copies every image referenced by {@code <IMG>} markup in a page text. */
    public void addTextImages(String text) {
        if (text == null || text.indexOf("<IMG>") < 0) return;
        Matcher matcher = IMG.matcher(text);
        while (matcher.find()) {
            String[] parts = matcher.group(1)
                .split(":");
            // Same split as Thaumcraft's renderer: parts[0] is the domain, parts[1] the path.
            if (parts.length >= 2) add(new ResourceLocation(parts[0], parts[1]));
        }
    }

    public List<String> missing() {
        return missing;
    }

    public int count() {
        return done.size();
    }
}
