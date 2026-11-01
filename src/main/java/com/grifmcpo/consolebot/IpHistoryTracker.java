package com.grifmcpo.consolebot;

import com.grifmcpo.consolebot.storage.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class IpHistoryTracker {

    private static final int DEFAULT_DYNAMIC_THRESHOLD = 3;
    private static final int DEFAULT_LOOKBACK_DAYS = 7;

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;

    public IpHistoryTracker(JavaPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void recordIpHistory(String playerName, String ip) {
        if (playerName == null || playerName.trim().isEmpty() || ip == null || ip.trim().isEmpty()) {
            return;
        }

        String normalizedIp = normalizeIp(ip);
        if (normalizedIp == null || isLoopback(normalizedIp)) {
            return;
        }

        Player player = Bukkit.getPlayerExact(playerName);
        UUID uuid = player != null ? player.getUniqueId() : null;
        if (uuid == null) {
            return;
        }

        databaseManager.recordIp(uuid, playerName, normalizedIp);
    }

    public void clearHistory(String playerName) {
        // SQLite storage keeps historical records, no in-memory cache to clear.
    }

    public List<String> getRecentHistory(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Player player = Bukkit.getPlayerExact(playerName);
        UUID uuid = player != null ? player.getUniqueId() : null;
        if (uuid == null) {
            return Collections.emptyList();
        }

        int lookbackDays = plugin.getConfig().getInt("dynamic-ip.lookback-days", DEFAULT_LOOKBACK_DAYS);
        return databaseManager.getRecentIps(uuid, lookbackDays).join();
    }

    public boolean isLikelyDynamicIp(String playerName, String ip) {
        if (playerName == null || playerName.trim().isEmpty() || ip == null) {
            return false;
        }

        String normalizedIp = normalizeIp(ip);
        if (normalizedIp == null || isLoopback(normalizedIp)) {
            return false;
        }

        Player player = Bukkit.getPlayerExact(playerName);
        UUID uuid = player != null ? player.getUniqueId() : null;
        if (uuid == null) {
            return false;
        }

        int threshold = plugin.getConfig().getInt("dynamic-ip.threshold", DEFAULT_DYNAMIC_THRESHOLD);
        int lookbackDays = plugin.getConfig().getInt("dynamic-ip.lookback-days", DEFAULT_LOOKBACK_DAYS);

        int distinctIps = databaseManager.countDistinctIps(uuid, lookbackDays).join();
        if (distinctIps < threshold) {
            return false;
        }

        List<String> recentIps = databaseManager.getRecentIps(uuid, lookbackDays).join();
        if (recentIps.contains(normalizedIp)) {
            return false;
        }

        int distinctUuidsForIp = databaseManager.countDistinctUuidsForIp(normalizedIp, lookbackDays).join();
        return distinctUuidsForIp <= 1;
    }

    public boolean isPotentialIpEscape(String playerName, String ip) {
        if (ip == null) {
            return false;
        }

        String normalizedIp = normalizeIp(ip);
        if (normalizedIp == null || isLoopback(normalizedIp)) {
            return false;
        }

        int lookbackDays = plugin.getConfig().getInt("alt-account.lookback-days", 30);
        int distinctUuids = databaseManager.countDistinctUuidsForIp(normalizedIp, lookbackDays).join();
        return distinctUuids > 1;
    }

    public boolean isLoopback(String ip) {
        if (ip == null) {
            return true;
        }
        String normalized = ip.trim();
        return normalized.equals("0.0.0.0")
                || normalized.equals("127.0.0.1")
                || normalized.equals("::1")
                || normalized.equalsIgnoreCase("localhost")
                || normalized.startsWith("127.")
                || normalized.startsWith("::1");
    }

    public int countDistinctIpsInWindow(String playerName, long millis) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return 0;
        }

        Player player = Bukkit.getPlayerExact(playerName);
        UUID uuid = player != null ? player.getUniqueId() : null;
        if (uuid == null) {
            return 0;
        }

        int days = Math.max(1, (int) TimeUnit.MILLISECONDS.toDays(millis));
        return databaseManager.countDistinctIps(uuid, days).join();
    }

    private String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        String result = ip.trim();
        if (result.isEmpty() || result.equals("—") || result.equals("0.0.0.0") || result.equals("127.0.0.1") || result.equals("::1") || result.equalsIgnoreCase("localhost")) {
            return null;
        }
        return result;
    }
}

