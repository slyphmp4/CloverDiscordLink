package com.slyph.cloverdiscordlink.listener;

import com.slyph.cloverdiscordlink.discord.DiscordBot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

@SuppressWarnings("deprecation")
public final class ChatListener implements Listener {

    private final DiscordBot discordBot;

    public ChatListener(DiscordBot discordBot) {
        this.discordBot = discordBot;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        discordBot.sendMinecraftMessage(
                event.getPlayer().getName(),
                event.getPlayer().getUniqueId(),
                event.getMessage()
        );
    }
}
