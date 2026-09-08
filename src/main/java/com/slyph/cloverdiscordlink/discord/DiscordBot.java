package com.slyph.cloverdiscordlink.discord;

import com.slyph.cloverdiscordlink.CloverDiscordLink;
import com.slyph.cloverdiscordlink.account.LinkManager;
import com.slyph.cloverdiscordlink.config.ConfigManager;
import com.slyph.cloverdiscordlink.config.PluginSettings;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class DiscordBot {

    public enum RoleCheck {
        GRANTED,
        DENIED,
        UNAVAILABLE
    }

    private static final Color CHAT_COLOR = new Color(0xADFBFF);
    private static final Color JOIN_COLOR = new Color(0x57F287);
    private static final Color QUIT_COLOR = new Color(0xED4245);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final CloverDiscordLink plugin;
    private final ConfigManager configManager;
    private final LinkManager linkManager;
    private final JDA jda;
    private final Map<String, FailedAttemptWindow> failedAttempts = new ConcurrentHashMap<>();

    public DiscordBot(
            CloverDiscordLink plugin,
            ConfigManager configManager,
            LinkManager linkManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.linkManager = linkManager;

        EnumSet<GatewayIntent> intents = EnumSet.of(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.DIRECT_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT
        );

        jda = JDABuilder.createLight(configManager.settings().discord().token(), intents)
                .addEventListeners(new DiscordListener())
                .build();
    }

    public boolean isReady() {
        return jda.getStatus() == JDA.Status.CONNECTED && resolveChannel() != null;
    }

    public String botName() {
        return jda.getSelfUser().getName();
    }

    public RoleCheck checkRequiredRole(UUID uuid) {
        PluginSettings.Discord discord = configManager.settings().discord();
        if (!discord.roleGate()) {
            return RoleCheck.GRANTED;
        }

        String discordId = linkManager.getDiscordId(uuid);
        TextChannel channel = resolveChannel();
        if (discordId == null || channel == null || jda.getStatus() != JDA.Status.CONNECTED) {
            return RoleCheck.UNAVAILABLE;
        }

        try {
            Member member = channel.getGuild()
                    .retrieveMemberById(discordId)
                    .timeout(discord.roleLookupTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .submit()
                    .get(discord.roleLookupTimeout().plusSeconds(1).toMillis(), TimeUnit.MILLISECONDS);

            return member.getRoles().stream().anyMatch(role -> role.getId().equals(discord.requiredRoleId()))
                    ? RoleCheck.GRANTED
                    : RoleCheck.DENIED;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ErrorResponseException error
                    && (error.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER
                    || error.getErrorResponse() == ErrorResponse.UNKNOWN_USER)) {
                return RoleCheck.DENIED;
            }
            plugin.getLogger().warning("Discord role lookup failed: " + safeMessage(cause));
            return RoleCheck.UNAVAILABLE;
        } catch (TimeoutException exception) {
            plugin.getLogger().warning("Discord role lookup timed out.");
            return RoleCheck.UNAVAILABLE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return RoleCheck.UNAVAILABLE;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Discord role lookup failed: " + safeMessage(exception));
            return RoleCheck.UNAVAILABLE;
        }
    }

    public void sendMinecraftMessage(String playerName, UUID uuid, String plainMessage) {
        TextChannel channel = resolveChannel();
        if (channel == null) {
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(playerName, null, "https://mc-heads.net/avatar/" + uuid + "/64")
                .setDescription(plainMessage)
                .setColor(CHAT_COLOR)
                .setFooter(formatFooter());

        channel.sendMessageEmbeds(embed.build()).queue(
                ignored -> {
                },
                failure -> plugin.getLogger().warning("Failed to relay Minecraft chat to Discord: " + safeMessage(failure))
        );
    }

    public void sendJoinLeave(Player player, boolean joined) {
        TextChannel channel = resolveChannel();
        if (channel == null) {
            return;
        }

        PluginSettings.Discord discord = configManager.settings().discord();
        String template = joined ? discord.joinMessage() : discord.quitMessage();
        String description = template.replace("{player}", player.getName());

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(player.getName(), null, "https://mc-heads.net/avatar/" + player.getUniqueId() + "/64")
                .setDescription(description)
                .setColor(joined ? JOIN_COLOR : QUIT_COLOR)
                .setFooter(formatFooter());

        channel.sendMessageEmbeds(embed.build()).queue(
                ignored -> {
                },
                failure -> plugin.getLogger().warning("Failed to relay player status to Discord: " + safeMessage(failure))
        );
    }

    public void shutdown() {
        jda.shutdownNow();
    }

    private void relayToMinecraft(MessageReceivedEvent event) {
        TextChannel channel = resolveChannel();
        if (channel == null || !event.getChannel().getId().equals(channel.getId())) {
            return;
        }

        String author = event.getAuthor().getName();
        String channelName = channel.getName();
        String content = event.getMessage().getContentDisplay();

        Component message = configManager.discordToMinecraft(author, channelName, content)
                .hoverEvent(HoverEvent.showText(configManager.discordHover(author, channelName, content)))
                .clickEvent(ClickEvent.openUrl(event.getMessage().getJumpUrl()));

        if (!plugin.isEnabled()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
            }
        });
    }

    private void handleDirectMessage(MessageReceivedEvent event) {
        String discordId = event.getAuthor().getId();
        PluginSettings settings = configManager.settings();

        if (failedAttempts.size() > 1_024) {
            failedAttempts.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }

        FailedAttemptWindow window = failedAttempts.computeIfAbsent(
                discordId,
                ignored -> new FailedAttemptWindow()
        );

        if (!window.canAttempt(settings.link().maxDmAttempts(), settings.link().attemptWindow())) {
            sendDm(event.getChannel(), settings.discord().dmRateLimited(), QUIT_COLOR);
            return;
        }

        String code = event.getMessage().getContentDisplay().trim();
        linkManager.consumeCode(code).ifPresentOrElse(
                uuid -> linkManager.linkAsync(uuid, discordId).thenAccept(result -> {
                    switch (result) {
                        case SUCCESS -> {
                            failedAttempts.remove(discordId);
                            sendDm(event.getChannel(), configManager.settings().discord().dmLinkSuccess(), JOIN_COLOR);
                        }
                        case DISCORD_ALREADY_LINKED, MINECRAFT_ALREADY_LINKED ->
                                sendDm(event.getChannel(), configManager.settings().discord().dmDiscordInUse(), QUIT_COLOR);
                        case STORAGE_ERROR, STORAGE_UNAVAILABLE ->
                                sendDm(event.getChannel(), configManager.settings().discord().dmStorageError(), QUIT_COLOR);
                    }
                }),
                () -> {
                    window.recordFailure(settings.link().attemptWindow());
                    sendDm(event.getChannel(), settings.discord().dmLinkInvalid(), QUIT_COLOR);
                }
        );
    }

    private void sendDm(MessageChannel channel, String text, Color color) {
        channel.sendMessageEmbeds(new EmbedBuilder()
                .setColor(color)
                .setDescription(text)
                .build()).queue();
    }

    private TextChannel resolveChannel() {
        try {
            return jda.getTextChannelById(configManager.settings().discord().channelId());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String formatFooter() {
        return configManager.settings().discord().footer()
                .replace("{time}", LocalTime.now().format(TIME));
    }

    private String safeMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null
                ? throwable == null ? "unknown error" : throwable.getClass().getSimpleName()
                : throwable.getMessage();
    }

    private final class DiscordListener extends ListenerAdapter {

        @Override
        public void onMessageReceived(@NotNull MessageReceivedEvent event) {
            if (event.getAuthor().isBot()) {
                return;
            }

            if (event.isFromGuild()) {
                relayToMinecraft(event);
            } else {
                handleDirectMessage(event);
            }
        }
    }

    private static final class FailedAttemptWindow {

        private int failures;
        private long resetAtMillis;

        synchronized boolean canAttempt(int maxAttempts, java.time.Duration window) {
            long now = System.currentTimeMillis();
            if (now >= resetAtMillis) {
                failures = 0;
                resetAtMillis = now + window.toMillis();
            }
            return failures < maxAttempts;
        }

        synchronized void recordFailure(java.time.Duration window) {
            long now = System.currentTimeMillis();
            if (now >= resetAtMillis) {
                failures = 0;
                resetAtMillis = now + window.toMillis();
            }
            failures++;
        }

        synchronized boolean isExpired() {
            return resetAtMillis > 0 && System.currentTimeMillis() >= resetAtMillis;
        }
    }
}
