package com.slyph.cloverdiscordlink.update;

import com.slyph.cloverdiscordlink.CloverDiscordLink;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {

    private static final URI RELEASES_URI = URI.create(
            "https://api.github.com/repos/slyphmp4/CloverDiscordLink/releases/latest"
    );
    private static final Pattern TAG_PATTERN = Pattern.compile(
            "\"tag_name\"\\s*:\\s*\"v?([0-9]+(?:\\.[0-9]+)*)\""
    );

    private final CloverDiscordLink plugin;
    private final HttpClient client;

    public UpdateChecker(CloverDiscordLink plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void check() {
        String current = plugin.getPluginMeta().getVersion();
        HttpRequest request = HttpRequest.newBuilder(RELEASES_URI)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "CloverDiscordLink/" + current)
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> handleResponse(current, response))
                .exceptionally(exception -> {
                    plugin.getLogger().warning("Update check failed: " + rootMessage(exception));
                    return null;
                });
    }

    private void handleResponse(String current, HttpResponse<String> response) {
        if (response.statusCode() == 404) {
            return;
        }
        if (response.statusCode() != 200) {
            plugin.getLogger().warning("Update check returned HTTP " + response.statusCode() + ".");
            return;
        }

        Matcher matcher = TAG_PATTERN.matcher(response.body());
        if (!matcher.find()) {
            return;
        }

        String latest = matcher.group(1);
        if (!isNewer(latest, current)) {
            return;
        }

        plugin.getLogger().info("A newer CloverDiscordLink version is available: " + latest
                + " (current: " + current + ").");
        plugin.getLogger().info("https://github.com/slyphmp4/CloverDiscordLink/releases/latest");
    }

    private boolean isNewer(String latest, String current) {
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");
        int length = Math.max(latestParts.length, currentParts.length);

        for (int i = 0; i < length; i++) {
            int latestValue = numericPart(latestParts, i);
            int currentValue = numericPart(currentParts, i);
            if (latestValue > currentValue) {
                return true;
            }
            if (latestValue < currentValue) {
                return false;
            }
        }
        return false;
    }

    private int numericPart(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }

        Matcher matcher = Pattern.compile("^(\\d+)").matcher(parts[index]);
        if (!matcher.find()) {
            return 0;
        }

        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
