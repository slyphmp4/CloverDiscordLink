package com.slyph.cloverdiscordlink.config;

import com.slyph.cloverdiscordlink.CloverDiscordLink;
import com.slyph.cloverdiscordlink.util.TextFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class ConfigManager {

    private static final Pattern SNOWFLAKE = Pattern.compile("\\d{17,20}");
    private static final Pattern MYSQL_HOST = Pattern.compile("[A-Za-z0-9._:\\-\\[\\]]+");
    private static final Pattern MYSQL_DATABASE = Pattern.compile("[A-Za-z0-9_$-]+");

    private final CloverDiscordLink plugin;
    private final TextFormatter textFormatter;
    private final AtomicReference<PluginSettings> settings = new AtomicReference<>();

    public ConfigManager(CloverDiscordLink plugin, TextFormatter textFormatter) {
        this.plugin = plugin;
        this.textFormatter = textFormatter;
    }

    public boolean loadInitial() {
        return reload();
    }

    public boolean reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        List<String> errors = new ArrayList<>();

        String token = environmentOrConfig("CLOVER_DISCORD_TOKEN", config.getString("discord.token", ""));
        String channelId = aliasedString(config, "discord.channel-id", "discord.channelId", "");
        boolean roleGate = config.getBoolean("discord.role-gate", true);
        String requiredRoleId = aliasedString(config, "discord.required-role-id", "discord.requiredRoleId", "");
        int roleTimeoutSeconds = config.getInt("discord.role-lookup-timeout-seconds", 5);

        boolean mysqlEnabled = config.getBoolean("mysql.enabled", false);
        String mysqlPassword = environmentOrConfig(
                "CLOVER_MYSQL_PASSWORD",
                config.getString("mysql.password", "")
        );
        int mysqlPort = config.getInt("mysql.port", 3306);
        int poolSize = config.getInt("mysql.pool-size", 4);

        int codeLength = config.getInt("link.code-length", 8);
        int codeTtlSeconds = config.getInt("link.code-ttl-seconds", 300);
        int maxDmAttempts = config.getInt("link.max-dm-attempts", 5);
        int attemptWindowSeconds = config.getInt("link.attempt-window-seconds", 60);

        if (token.isBlank() || token.equals("BOT_TOKEN")) {
            errors.add("discord.token must contain a valid bot token or CLOVER_DISCORD_TOKEN must be set");
        }
        if (!SNOWFLAKE.matcher(channelId).matches()) {
            errors.add("discord.channel-id must be a valid Discord snowflake");
        }
        if (roleGate && !SNOWFLAKE.matcher(requiredRoleId).matches()) {
            errors.add("discord.required-role-id must be a valid Discord snowflake when role-gate is enabled");
        }
        if (roleTimeoutSeconds < 1 || roleTimeoutSeconds > 15) {
            errors.add("discord.role-lookup-timeout-seconds must be between 1 and 15");
        }
        if (mysqlPort < 1 || mysqlPort > 65535) {
            errors.add("mysql.port must be between 1 and 65535");
        }
        if (poolSize < 1 || poolSize > 16) {
            errors.add("mysql.pool-size must be between 1 and 16");
        }
        if (codeLength < 4 || codeLength > 10) {
            errors.add("link.code-length must be between 4 and 10");
        }
        if (codeTtlSeconds < 60 || codeTtlSeconds > 3600) {
            errors.add("link.code-ttl-seconds must be between 60 and 3600");
        }
        if (maxDmAttempts < 1 || maxDmAttempts > 20) {
            errors.add("link.max-dm-attempts must be between 1 and 20");
        }
        if (attemptWindowSeconds < 10 || attemptWindowSeconds > 600) {
            errors.add("link.attempt-window-seconds must be between 10 and 600");
        }

        validateRequired(config, "mysql.host", mysqlEnabled, errors);
        validateRequired(config, "mysql.database", mysqlEnabled, errors);
        validateRequired(config, "mysql.user", mysqlEnabled, errors);
        if (mysqlEnabled && !MYSQL_HOST.matcher(Objects.requireNonNullElse(config.getString("mysql.host"), "")).matches()) {
            errors.add("mysql.host contains unsupported characters");
        }
        if (mysqlEnabled && !MYSQL_DATABASE.matcher(Objects.requireNonNullElse(config.getString("mysql.database"), "")).matches()) {
            errors.add("mysql.database contains unsupported characters");
        }

        List<String> noPermission = list(config, "messages.no-permission", errors);
        List<String> reloadSuccess = list(config, "messages.reload-success", errors);
        List<String> reloadFailed = list(config, "messages.reload-failed", errors);
        List<String> usage = list(config, "messages.usage", errors);
        List<String> discordToMinecraft = list(config, "messages.discord-to-minecraft", errors);
        List<String> discordHover = list(config, "messages.discord-hover", errors);
        List<String> kickMessage = list(config, "link.kick-message", errors);
        List<String> roleKick = list(config, "link.role-kick", errors);
        List<String> serviceUnavailable = list(config, "link.service-unavailable", errors);

        validateTemplate("messages.no-permission", noPermission, Map.of(), errors);
        validateTemplate("messages.reload-success", reloadSuccess, Map.of(), errors);
        validateTemplate("messages.reload-failed", reloadFailed, Map.of(), errors);
        validateTemplate("messages.usage", usage, Map.of("label", "dchat"), errors);
        validateTemplate(
                "messages.discord-to-minecraft",
                discordToMinecraft,
                Map.of("author", "user", "channel", "channel", "message", "message"),
                errors
        );
        validateTemplate(
                "messages.discord-hover",
                discordHover,
                Map.of("author", "user", "channel", "channel", "message", "message"),
                errors
        );
        validateTemplate("link.kick-message", kickMessage, Map.of("code", "00000000", "bot", "bot"), errors);
        validateTemplate("link.role-kick", roleKick, Map.of(), errors);
        validateTemplate("link.service-unavailable", serviceUnavailable, Map.of(), errors);

        if (!errors.isEmpty()) {
            errors.forEach(error -> plugin.getLogger().severe("Config: " + error));
            return false;
        }

        PluginSettings next = new PluginSettings(
                config.getBoolean("update-check", true),
                new PluginSettings.Discord(
                        token,
                        channelId,
                        roleGate,
                        requiredRoleId,
                        Duration.ofSeconds(roleTimeoutSeconds),
                        config.getString("discord.join-message", "**{player}** joined the server"),
                        config.getString("discord.quit-message", "**{player}** left the server"),
                        config.getString("discord.footer", "Clover • {time}"),
                        config.getString("discord.dm-link-success", "✅ Your Discord account is now linked."),
                        config.getString("discord.dm-link-invalid", "❌ This code is invalid or has expired."),
                        config.getString("discord.dm-discord-in-use", "❌ This Discord account is already linked."),
                        config.getString("discord.dm-storage-error", "❌ The link could not be saved."),
                        config.getString("discord.dm-rate-limited", "❌ Too many invalid attempts.")
                ),
                new PluginSettings.MySql(
                        mysqlEnabled,
                        Objects.requireNonNullElse(config.getString("mysql.host"), "localhost"),
                        mysqlPort,
                        Objects.requireNonNullElse(config.getString("mysql.database"), "database"),
                        Objects.requireNonNullElse(config.getString("mysql.user"), "root"),
                        mysqlPassword,
                        poolSize,
                        config.getBoolean("mysql.ssl", false)
                ),
                new PluginSettings.Chat(
                        config.getString("chat.to-minecraft-prefix", "")
                ),
                new PluginSettings.Messages(
                        List.copyOf(noPermission),
                        List.copyOf(reloadSuccess),
                        List.copyOf(reloadFailed),
                        List.copyOf(usage),
                        List.copyOf(discordToMinecraft),
                        List.copyOf(discordHover)
                ),
                new PluginSettings.Link(
                        codeLength,
                        Duration.ofSeconds(codeTtlSeconds),
                        maxDmAttempts,
                        Duration.ofSeconds(attemptWindowSeconds),
                        List.copyOf(kickMessage),
                        List.copyOf(roleKick),
                        List.copyOf(serviceUnavailable)
                )
        );

        PluginSettings previous = settings.getAndSet(next);
        if (previous != null && (!previous.discord().token().equals(next.discord().token())
                || !previous.mysql().equals(next.mysql()))) {
            plugin.getLogger().warning("Discord token and MySQL changes require a server restart to take effect.");
        }
        return true;
    }

    public PluginSettings settings() {
        PluginSettings current = settings.get();
        if (current == null) {
            throw new IllegalStateException("Configuration has not been loaded");
        }
        return current;
    }

    public Component commandMessage(List<String> lines, Map<String, String> placeholders) {
        return textFormatter.deserializeLines(lines, placeholders);
    }

    public Component discordToMinecraft(String author, String channel, String message) {
        PluginSettings current = settings();
        String body = String.join("\n", current.messages().discordToMinecraft());
        return textFormatter.deserialize(
                current.chat().toMinecraftPrefix() + body,
                Map.of("author", author, "channel", channel, "message", message)
        );
    }

    public Component discordHover(String author, String channel, String message) {
        return textFormatter.deserializeLines(
                settings().messages().discordHover(),
                Map.of("author", author, "channel", channel, "message", message)
        );
    }

    private String aliasedString(FileConfiguration config, String modernPath, String legacyPath, String fallback) {
        if (config.contains(modernPath, true)) {
            return Objects.requireNonNullElse(config.getString(modernPath), fallback);
        }
        if (config.contains(legacyPath, true)) {
            return Objects.requireNonNullElse(config.getString(legacyPath), fallback);
        }
        return Objects.requireNonNullElse(config.getString(modernPath), fallback);
    }

    private String environmentOrConfig(String environmentName, String configured) {
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null || environmentValue.isBlank() ? Objects.requireNonNullElse(configured, "") : environmentValue;
    }

    private List<String> list(FileConfiguration config, String path, List<String> errors) {
        List<String> value = config.getStringList(path);
        if (value.isEmpty()) {
            errors.add(path + " must contain at least one line");
        }
        return value;
    }

    private void validateTemplate(
            String path,
            List<String> lines,
            Map<String, String> placeholders,
            List<String> errors
    ) {
        if (lines.isEmpty()) {
            return;
        }
        try {
            textFormatter.deserializeLines(lines, placeholders);
        } catch (RuntimeException exception) {
            errors.add(path + " contains invalid formatting: " + exception.getMessage());
        }
    }

    private void validateRequired(FileConfiguration config, String path, boolean enabled, List<String> errors) {
        if (enabled && Objects.requireNonNullElse(config.getString(path), "").isBlank()) {
            errors.add(path + " must not be blank when MySQL is enabled");
        }
    }
}
