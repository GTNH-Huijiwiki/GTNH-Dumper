package com.iouter.gtnhdumper.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Copies the quest book sources into the export, so the tool chain reads quests from the same snapshot as the other
 * data: {@code config/betterquesting/DefaultQuests/} and the quest translations in
 * {@code config/txloader/forceload/betterquesting/lang/}.
 */
public final class BetterQuestingFiles {

    private BetterQuestingFiles() {}

    /** Replaces {@code <output>/betterquesting/}; returns the number of copied files. */
    public static int copy(File gameDirectory, File output) throws IOException {
        Path config = gameDirectory.toPath()
            .resolve("config");
        Path quests = config.resolve("betterquesting/DefaultQuests");
        if (!Files.isDirectory(quests)) throw new IOException("Missing quest book: " + quests);
        Path target = output.toPath()
            .resolve("betterquesting");
        delete(target);
        int copied = copyTree(quests, target.resolve("DefaultQuests"));
        Path lang = config.resolve("txloader/forceload/betterquesting/lang");
        if (Files.isDirectory(lang)) copied += copyTree(lang, target.resolve("lang"));
        return copied;
    }

    private static int copyTree(Path source, Path target) throws IOException {
        int[] count = { 0 };
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                count[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }

    private static void delete(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
