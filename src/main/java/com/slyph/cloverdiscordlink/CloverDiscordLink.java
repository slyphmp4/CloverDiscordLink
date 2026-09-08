package com.slyph.cloverdiscordlink;

import com.slyph.cloverdiscordlink.account.LinkManager;
import com.slyph.cloverdiscordlink.command.DChatCommand;
import com.slyph.cloverdiscordlink.config.ConfigManager;
import com.slyph.cloverdiscordlink.discord.DiscordBot;
import com.slyph.cloverdiscordlink.listener.ChatListener;
import com.slyph.cloverdiscordlink.listener.ConnectionListener;
import com.slyph.cloverdiscordlink.update.UpdateChecker;
import com.slyph.cloverdiscordlink.util.LegacyDataMigrator;
import com.slyph.cloverdiscordlink.util.TextFormatter;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CloverDiscordLink extends JavaPlugin {

    private ConfigManager configManager;
    private LinkManager linkManager;
    private DiscordBot discordBot;

    @Override
    public void onEnable() {
        LegacyDataMigrator.migrate(this);
        saveDefaultConfig();

        TextFormatter textFormatter = new TextFormatter();
        configManager = new ConfigManager(this, textFormatter);
        if (!configManager.loadInitial()) {
            getLogger().severe("CloverDiscordLink was disabled because config.yml is invalid.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        linkManager = new LinkManager(this, configManager.settings());
        linkManager.initialize().exceptionally(exception -> {
            getLogger().severe("Link storage initialization failed: " + exception.getMessage());
            return null;
        });

        try {
            discordBot = new DiscordBot(this, configManager, linkManager);
        } catch (RuntimeException exception) {
            getLogger().severe("Discord bot initialization failed: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(
                new ChatListener(discordBot, textFormatter),
                this
        );
        getServer().getPluginManager().registerEvents(
                new ConnectionListener(configManager, linkManager, discordBot),
                this
        );

        PluginCommand command = getCommand("dchat");
        if (command == null) {
            getLogger().severe("Command dchat is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        DChatCommand commandHandler = new DChatCommand(this, configManager);
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getLogger().info("CloverDiscordLink v" + getPluginMeta().getVersion() + " enabled for Paper 26.2.");

        if (configManager.settings().updateCheck()) {
            new UpdateChecker(this).check();
        }
    }

    @Override
    public void onDisable() {
        if (discordBot != null) {
            discordBot.shutdown();
        }
        if (linkManager != null) {
            linkManager.shutdown();
        }
    }
}
