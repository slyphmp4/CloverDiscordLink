package com.slyph.cloverdiscordlink.storage;

import java.util.Map;
import java.util.UUID;

public interface LinkStorage extends AutoCloseable {

    Map<UUID, String> loadAll() throws Exception;

    void save(UUID uuid, String discordId) throws Exception;

    void delete(UUID uuid) throws Exception;

    String name();

    @Override
    void close() throws Exception;
}
