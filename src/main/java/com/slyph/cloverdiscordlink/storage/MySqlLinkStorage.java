package com.slyph.cloverdiscordlink.storage;

import com.slyph.cloverdiscordlink.config.PluginSettings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MySqlLinkStorage implements LinkStorage {

    private static final String TABLE = "dcb_links";

    private final PluginSettings.MySql settings;
    private HikariDataSource dataSource;

    public MySqlLinkStorage(PluginSettings.MySql settings) {
        this.settings = settings;
    }

    @Override
    public Map<UUID, String> loadAll() throws Exception {
        initializePool();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                            "uuid CHAR(36) PRIMARY KEY, " +
                            "discord_id VARCHAR(32) NOT NULL)"
            );
        }

        Map<UUID, String> links = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT uuid, discord_id FROM " + TABLE
             );
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                links.put(
                        UUID.fromString(resultSet.getString("uuid")),
                        resultSet.getString("discord_id")
                );
            }
        }
        return links;
    }

    @Override
    public void save(UUID uuid, String discordId) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE " + TABLE + " SET discord_id=? WHERE uuid=?"
                )) {
                    update.setString(1, discordId);
                    update.setString(2, uuid.toString());
                    updated = update.executeUpdate();
                }

                if (updated == 0) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO " + TABLE + "(uuid, discord_id) VALUES(?, ?)"
                    )) {
                        insert.setString(1, uuid.toString());
                        insert.setString(2, discordId);
                        insert.executeUpdate();
                    }
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public void delete(UUID uuid) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + TABLE + " WHERE uuid=?"
             )) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public String name() {
        return "MySQL";
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private void initializePool() throws Exception {
        if (dataSource != null) {
            return;
        }

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(buildJdbcUrl());
        hikari.setUsername(settings.user());
        hikari.setPassword(settings.password());
        hikari.setPoolName("CloverDiscordLink-MySQL");
        hikari.setMaximumPoolSize(settings.poolSize());
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(5_000);
        hikari.setValidationTimeout(3_000);
        hikari.setInitializationFailTimeout(5_000);
        hikari.setAutoCommit(true);

        dataSource = new HikariDataSource(hikari);
    }

    private String buildJdbcUrl() {
        String host = settings.host();
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        String sslMode = settings.ssl() ? "REQUIRED" : "DISABLED";
        return "jdbc:mysql://" + host + ":" + settings.port() + "/" + settings.database()
                + "?sslMode=" + sslMode + "&characterEncoding=UTF-8";
    }
}
