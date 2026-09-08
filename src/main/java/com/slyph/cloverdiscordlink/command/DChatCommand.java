package com.slyph.cloverdiscordlink.command;

import com.slyph.cloverdiscordlink.CloverDiscordLink;
import com.slyph.cloverdiscordlink.config.ConfigManager;
import com.slyph.cloverdiscordlink.config.PluginSettings;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DChatCommand implements CommandExecutor, TabCompleter {

    private static final String RELOAD_PERMISSION = "dchat.reload";

    private final CloverDiscordLink plugin;
    private final ConfigManager configManager;

    public DChatCommand(CloverDiscordLink plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        PluginSettings settings = configManager.settings();

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(RELOAD_PERMISSION)) {
                sender.sendMessage(configManager.commandMessage(
                        settings.messages().noPermission(),
                        Map.of()
                ));
                return true;
            }

            boolean reloaded = configManager.reload();
            PluginSettings active = configManager.settings();
            sender.sendMessage(configManager.commandMessage(
                    reloaded ? active.messages().reloadSuccess() : active.messages().reloadFailed(),
                    Map.of()
            ));

            if (reloaded) {
                plugin.getLogger().info(sender.getName() + " reloaded the configuration.");
            }
            return true;
        }

        sender.sendMessage(configManager.commandMessage(
                settings.messages().usage(),
                Map.of("label", label)
        ));
        return true;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission(RELOAD_PERMISSION) || args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        return "reload".startsWith(input) ? List.of("reload") : List.of();
    }
}
