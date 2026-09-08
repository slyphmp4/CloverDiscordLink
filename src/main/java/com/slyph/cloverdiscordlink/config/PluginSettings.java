package com.slyph.cloverdiscordlink.config;

import java.time.Duration;
import java.util.List;

public record PluginSettings(
        boolean updateCheck,
        Discord discord,
        MySql mysql,
        Chat chat,
        Messages messages,
        Link link
) {
    public record Discord(
            String token,
            String channelId,
            boolean roleGate,
            String requiredRoleId,
            Duration roleLookupTimeout,
            String joinMessage,
            String quitMessage,
            String footer,
            String dmLinkSuccess,
            String dmLinkInvalid,
            String dmDiscordInUse,
            String dmStorageError,
            String dmRateLimited
    ) {
    }

    public record MySql(
            boolean enabled,
            String host,
            int port,
            String database,
            String user,
            String password,
            int poolSize,
            boolean ssl
    ) {
    }

    public record Chat(String toMinecraftPrefix) {
    }

    public record Messages(
            List<String> noPermission,
            List<String> reloadSuccess,
            List<String> reloadFailed,
            List<String> usage,
            List<String> discordToMinecraft,
            List<String> discordHover
    ) {
    }

    public record Link(
            int codeLength,
            Duration codeTtl,
            int maxDmAttempts,
            Duration attemptWindow,
            List<String> kickMessage,
            List<String> roleKick,
            List<String> serviceUnavailable
    ) {
    }
}
