package com.slyph.cloverdiscordlink.util;

import com.slyph.cloverdiscordlink.CloverDiscordLink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class LegacyDataMigrator {

    private static final String LEGACY_FOLDER = "DiscordChatBridge";
    private static final List<String> MIGRATED_FILES = List.of("config.yml", "links.yml");

    private LegacyDataMigrator() {
    }

    public static void migrate(CloverDiscordLink plugin) {
        Path currentFolder = plugin.getDataFolder().toPath();
        Path pluginsFolder = currentFolder.getParent();
        if (pluginsFolder == null) {
            return;
        }

        Path legacyFolder = pluginsFolder.resolve(LEGACY_FOLDER);
        if (!Files.isDirectory(legacyFolder)) {
            return;
        }

        try {
            Files.createDirectories(currentFolder);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not prepare plugin data directory: " + exception.getMessage());
            return;
        }

        for (String fileName : MIGRATED_FILES) {
            Path source = legacyFolder.resolve(fileName);
            Path target = currentFolder.resolve(fileName);

            if (!Files.isRegularFile(source) || Files.exists(target)) {
                continue;
            }

            try {
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                plugin.getLogger().info("Migrated " + fileName + " from " + LEGACY_FOLDER + ".");
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not migrate " + fileName + ": " + exception.getMessage());
            }
        }
    }
}
