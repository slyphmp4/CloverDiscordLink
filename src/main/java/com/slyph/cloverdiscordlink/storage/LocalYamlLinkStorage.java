package com.slyph.cloverdiscordlink.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class LocalYamlLinkStorage implements LinkStorage {

    private final Path file;
    private final Logger logger;
    private YamlConfiguration yaml;

    public LocalYamlLinkStorage(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    @Override
    public Map<UUID, String> loadAll() throws Exception {
        Files.createDirectories(file.getParent());
        if (Files.notExists(file)) {
            Files.createFile(file);
        }

        yaml = YamlConfiguration.loadConfiguration(file.toFile());
        Map<UUID, String> links = new HashMap<>();

        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String discordId = yaml.getString(key + ".discord");
                if (discordId != null && !discordId.isBlank()) {
                    links.put(uuid, discordId);
                }
            } catch (IllegalArgumentException exception) {
                logger.warning("Ignoring malformed UUID in links.yml: " + key);
            }
        }
        return links;
    }

    @Override
    public void save(UUID uuid, String discordId) throws Exception {
        ensureLoaded();
        yaml.set(uuid + ".discord", discordId);
        yaml.save(file.toFile());
    }

    @Override
    public void delete(UUID uuid) throws Exception {
        ensureLoaded();
        yaml.set(uuid.toString(), null);
        yaml.save(file.toFile());
    }

    @Override
    public String name() {
        return "Local YAML";
    }

    @Override
    public void close() {
    }

    private void ensureLoaded() {
        if (yaml == null) {
            throw new IllegalStateException("Local storage has not been initialized");
        }
    }
}
