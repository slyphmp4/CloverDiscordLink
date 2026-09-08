package com.slyph.cloverdiscordlink.account;

import com.slyph.cloverdiscordlink.CloverDiscordLink;
import com.slyph.cloverdiscordlink.config.PluginSettings;
import com.slyph.cloverdiscordlink.storage.LinkStorage;
import com.slyph.cloverdiscordlink.storage.LocalYamlLinkStorage;
import com.slyph.cloverdiscordlink.storage.MySqlLinkStorage;
import com.slyph.cloverdiscordlink.util.LinkCodeGenerator;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LinkManager {

    public enum LinkResult {
        SUCCESS,
        MINECRAFT_ALREADY_LINKED,
        DISCORD_ALREADY_LINKED,
        STORAGE_UNAVAILABLE,
        STORAGE_ERROR
    }

    private record PendingLink(UUID uuid, String code, Instant expiresAt) {
    }

    private final CloverDiscordLink plugin;
    private final PluginSettings initialSettings;
    private final Map<UUID, String> uuidToDiscord = new ConcurrentHashMap<>();
    private final Map<String, UUID> discordToUuid = new ConcurrentHashMap<>();
    private final Map<UUID, PendingLink> pendingByUuid = new ConcurrentHashMap<>();
    private final Map<String, PendingLink> pendingByCode = new ConcurrentHashMap<>();
    private final Object pendingLock = new Object();
    private final AtomicBoolean ready = new AtomicBoolean();
    private final ExecutorService storageExecutor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("clover-discord-link-storage").factory()
    );

    private volatile LinkStorage storage;

    public LinkManager(CloverDiscordLink plugin, PluginSettings initialSettings) {
        this.plugin = plugin;
        this.initialSettings = initialSettings;
    }

    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(this::initializeStorage, storageExecutor);
    }

    public boolean isReady() {
        return ready.get();
    }

    public boolean isLinked(UUID uuid) {
        return uuidToDiscord.containsKey(uuid);
    }

    public String getDiscordId(UUID uuid) {
        return uuidToDiscord.get(uuid);
    }

    public String createCode(UUID uuid, int length, java.time.Duration ttl) {
        synchronized (pendingLock) {
            removePendingForUuid(uuid);
            cleanupExpired();

            for (int attempts = 0; attempts < 128; attempts++) {
                String code = LinkCodeGenerator.generate(length);
                if (pendingByCode.containsKey(code)) {
                    continue;
                }

                PendingLink pending = new PendingLink(uuid, code, Instant.now().plus(ttl));
                pendingByUuid.put(uuid, pending);
                pendingByCode.put(code, pending);
                return code;
            }
        }
        throw new IllegalStateException("Could not allocate a unique link code");
    }

    public Optional<UUID> consumeCode(String code) {
        if (code == null) {
            return Optional.empty();
        }

        synchronized (pendingLock) {
            cleanupExpired();
            PendingLink pending = pendingByCode.remove(code.trim());
            if (pending == null) {
                return Optional.empty();
            }
            pendingByUuid.remove(pending.uuid(), pending);
            return Optional.of(pending.uuid());
        }
    }

    public CompletableFuture<LinkResult> linkAsync(UUID uuid, String discordId) {
        if (!ready.get()) {
            return CompletableFuture.completedFuture(LinkResult.STORAGE_UNAVAILABLE);
        }

        return CompletableFuture.supplyAsync(() -> {
            if (!ready.get() || storage == null) {
                return LinkResult.STORAGE_UNAVAILABLE;
            }
            if (uuidToDiscord.containsKey(uuid)) {
                return LinkResult.MINECRAFT_ALREADY_LINKED;
            }
            if (discordToUuid.containsKey(discordId)) {
                return LinkResult.DISCORD_ALREADY_LINKED;
            }

            try {
                storage.save(uuid, discordId);
                uuidToDiscord.put(uuid, discordId);
                discordToUuid.put(discordId, uuid);
                return LinkResult.SUCCESS;
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to save account link: " + exception.getMessage());
                return LinkResult.STORAGE_ERROR;
            }
        }, storageExecutor);
    }

    public CompletableFuture<Boolean> unlinkAsync(UUID uuid) {
        if (!ready.get()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String discordId = uuidToDiscord.get(uuid);
            if (discordId == null || storage == null) {
                return false;
            }

            try {
                storage.delete(uuid);
                uuidToDiscord.remove(uuid, discordId);
                discordToUuid.remove(discordId, uuid);
                return true;
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to delete account link: " + exception.getMessage());
                return false;
            }
        }, storageExecutor);
    }

    public void shutdown() {
        ready.set(false);
        storageExecutor.submit(this::closeStorage);
        storageExecutor.shutdown();
        try {
            if (!storageExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                storageExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            storageExecutor.shutdownNow();
        }
    }

    private void initializeStorage() {
        LinkStorage selected = null;
        Map<UUID, String> loaded = Map.of();

        if (initialSettings.mysql().enabled()) {
            MySqlLinkStorage mysql = new MySqlLinkStorage(initialSettings.mysql());
            try {
                loaded = mysql.loadAll();
                selected = mysql;
            } catch (Exception exception) {
                plugin.getLogger().warning("MySQL is unavailable; falling back to links.yml: " + exception.getMessage());
                closeQuietly(mysql);
            }
        }

        if (selected == null) {
            LocalYamlLinkStorage local = new LocalYamlLinkStorage(
                    plugin.getDataFolder().toPath().resolve("links.yml"),
                    plugin.getLogger()
            );
            try {
                loaded = local.loadAll();
                selected = local;
            } catch (Exception exception) {
                plugin.getLogger().severe("Local link storage could not be initialized: " + exception.getMessage());
                closeQuietly(local);
                return;
            }
        }

        uuidToDiscord.clear();
        discordToUuid.clear();
        int duplicates = 0;

        for (Map.Entry<UUID, String> entry : loaded.entrySet()) {
            UUID previous = discordToUuid.putIfAbsent(entry.getValue(), entry.getKey());
            if (previous != null && !previous.equals(entry.getKey())) {
                duplicates++;
                continue;
            }
            uuidToDiscord.put(entry.getKey(), entry.getValue());
        }

        storage = selected;
        ready.set(true);
        plugin.getLogger().info(
                "Loaded " + uuidToDiscord.size() + " linked accounts from " + selected.name() + "."
        );
        if (duplicates > 0) {
            plugin.getLogger().warning(
                    "Ignored " + duplicates + " duplicate Discord account links. Each Discord account may link to only one Minecraft account."
            );
        }
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        pendingByCode.entrySet().removeIf(entry -> {
            PendingLink pending = entry.getValue();
            if (pending.expiresAt().isAfter(now)) {
                return false;
            }
            pendingByUuid.remove(pending.uuid(), pending);
            return true;
        });
    }

    private void removePendingForUuid(UUID uuid) {
        PendingLink existing = pendingByUuid.remove(uuid);
        if (existing != null) {
            pendingByCode.remove(existing.code(), existing);
        }
    }

    private void closeStorage() {
        LinkStorage current = storage;
        storage = null;
        if (current != null) {
            closeQuietly(current);
        }
    }

    private void closeQuietly(LinkStorage linkStorage) {
        try {
            linkStorage.close();
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to close " + linkStorage.name() + " storage: " + exception.getMessage());
        }
    }
}
