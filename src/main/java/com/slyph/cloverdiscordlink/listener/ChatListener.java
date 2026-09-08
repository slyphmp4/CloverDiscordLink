package com.slyph.cloverdiscordlink.listener;

import com.slyph.cloverdiscordlink.discord.DiscordBot;
import com.slyph.cloverdiscordlink.util.TextFormatter;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatListener implements Listener {

    private final DiscordBot discordBot;
    private final TextFormatter textFormatter;

    public ChatListener(DiscordBot discordBot, TextFormatter textFormatter) {
        this.discordBot = discordBot;
        this.textFormatter = textFormatter;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        discordBot.sendMinecraftMessage(
                event.getPlayer().getName(),
                event.getPlayer().getUniqueId(),
                textFormatter.plain(event.message())
        );
    }
}
