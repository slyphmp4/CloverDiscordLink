package dev.fread.discordbridge.update;

import dev.fread.discordbridge.DiscordChatBridge;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {

    private static final String API_URL =
            "https://api.github.com/repos/slyphmp4/DiscordChatBridge/releases/latest";
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([0-9.]+)\"");

    private final DiscordChatBridge plugin;

    public UpdateChecker(DiscordChatBridge plugin) { this.plugin = plugin; }

    public void check() {
        CompletableFuture.runAsync(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder json = new StringBuilder();
                    for (String ln; (ln = br.readLine()) != null; ) json.append(ln);

                    Matcher m = TAG.matcher(json.toString());
                    if (!m.find()) return;

                    String latest  = m.group(1);
                    String current = plugin.getDescription().getVersion();
                    if (!isNewer(latest, current)) return;

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().info("");
                        plugin.getLogger().info("=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=");
                        plugin.getLogger().info("");
                        plugin.getLogger().info("A newer version of CloverDiscordLink is available!");
                        plugin.getLogger().info("Latest : " + latest);
                        plugin.getLogger().info("Current: " + current);
                        plugin.getLogger().info("Download: https://github.com/slyphmp4/DiscordChatBridge/releases/latest");
                        plugin.getLogger().info("");
                        plugin.getLogger().info("=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=-=+=");
                        plugin.getLogger().info("");
                    });
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Update check failed: " + ex.getMessage());
            }
        });
    }

    private boolean isNewer(String latest, String current) {
        String[] l = latest.split("\\.");
        String[] c = current.split("\\.");
        int len = Math.max(l.length, c.length);
        for (int i = 0; i < len; i++) {
            int lv = i < l.length ? Integer.parseInt(l[i]) : 0;
            int cv = i < c.length ? Integer.parseInt(c[i]) : 0;
            if (lv > cv) return true;
            if (lv < cv) return false;
        }
        return false;
    }
}
