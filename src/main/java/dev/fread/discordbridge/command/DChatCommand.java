package dev.fread.discordbridge.command;

import dev.fread.discordbridge.DiscordChatBridge;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class DChatCommand implements CommandExecutor {

    private final DiscordChatBridge plugin;
    public DChatCommand(DiscordChatBridge plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {

            if (!sender.hasPermission("dchat.reload")) {
                sender.sendMessage(ChatColor.RED + "У вас нет прав.");
                return true;
            }

            plugin.getConfigManager().reload();
            sender.sendMessage(ChatColor.GREEN + "CloverDiscordLink: конфигурация перезагружена.");
            plugin.getLogger().info(sender.getName() + " перезагрузил конфиг.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Использование: /" + label + " reload");
        return true;
    }
}
