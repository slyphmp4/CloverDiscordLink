package com.slyph.cloverdiscordlink.listener;

import com.slyph.cloverdiscordlink.account.LinkManager;
import com.slyph.cloverdiscordlink.config.ConfigManager;
import com.slyph.cloverdiscordlink.config.PluginSettings;
import com.slyph.cloverdiscordlink.discord.DiscordBot;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

public final class ConnectionListener implements Listener {

    private final ConfigManager configManager;
    private final LinkManager linkManager;
    private final DiscordBot discordBot;

    public ConnectionListener(
            ConfigManager configManager,
            LinkManager linkManager,
            DiscordBot discordBot
    ) {
        this.configManager = configManager;
        this.linkManager = linkManager;
        this.discordBot = discordBot;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        UUID uuid = event.getUniqueId();
        PluginSettings settings = configManager.settings();

        if (!linkManager.isReady()) {
            disallow(event, settings.link().serviceUnavailable(), Map.of());
            return;
        }

        if (!linkManager.isLinked(uuid)) {
            if (!discordBot.isReady()) {
                disallow(event, settings.link().serviceUnavailable(), Map.of());
                return;
            }

            String code = linkManager.createCode(
                    uuid,
                    settings.link().codeLength(),
                    settings.link().codeTtl()
            );
            disallow(
                    event,
                    settings.link().kickMessage(),
                    Map.of("code", code, "bot", discordBot.botName())
            );
            return;
        }

        switch (discordBot.checkRequiredRole(uuid)) {
            case GRANTED -> {
            }
            case DENIED -> disallow(event, settings.link().roleKick(), Map.of());
            case UNAVAILABLE -> disallow(event, settings.link().serviceUnavailable(), Map.of());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        if (linkManager.isLinked(event.getPlayer().getUniqueId())) {
            discordBot.sendJoinLeave(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        if (linkManager.isLinked(event.getPlayer().getUniqueId())) {
            discordBot.sendJoinLeave(event.getPlayer(), false);
        }
    }

    private void disallow(
            AsyncPlayerPreLoginEvent event,
            java.util.List<String> lines,
            Map<String, String> placeholders
    ) {
        Component message = configManager.commandMessage(lines, placeholders);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, message);
    }
}
