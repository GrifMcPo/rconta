package com.grifmcpo.consolebot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IpHistoryTracker {

    private static final long HISTORY_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final int DYNAMIC_IP_THRESHOLD = 3;

    private final Map<String, List<IpHistoryEntry>> history = new ConcurrentHashMap<>();

    public void recordIpHistory(String playerName, String ip) {
        if (playerName == null || playerName.trim().isEmpty() || ip == null || ip.isEmpty()) {
            return;
        }

        List<IpHistoryEntry> entries = history.computeIfAbsent(playerName, key -> new ArrayList<>());
        long now = System.currentTimeMillis();

        entries.removeIf(entry -> now - entry.timestamp > HISTORY_WINDOW_MS);

        for (IpHistoryEntry entry : entries) {
            if (entry.ip.equals(ip)) {
                entry.timestamp = now;
                return;
            }
        }

        entries.add(new IpHistoryEntry(ip, now));
    }

    public void clearHistory(String playerName) {
        if (playerName == null) return;
        history.remove(playerName);
    }

    public List<String> getRecentHistory(String playerName) {
        List<String> result = new ArrayList<>();
        if (playerName == null) return result;

        List<IpHistoryEntry> entries = history.getOrDefault(playerName, Collections.emptyList());
        for (IpHistoryEntry entry : entries) {
            result.add(entry.ip);
        }
        return result;
    }

    public boolean isLikelyDynamicIp(String playerName, String ip) {
        if (playerName == null || ip == null) return false;

        List<IpHistoryEntry> entries = history.getOrDefault(playerName, Collections.emptyList());
        if (entries.isEmpty()) {
            return false;
        }

        long now = System.currentTimeMillis();
        int unique = 0;
        for (IpHistoryEntry entry : entries) {
            if (now - entry.timestamp <= HISTORY_WINDOW_MS) {
                unique++;
            }
        }

        if (unique <= DYNAMIC_IP_THRESHOLD) {
            return false;
        }

        return entries.stream().filter(e -> e.ip.equals(ip)).count() == 0;
    }

    public boolean isPotentialIpEscape(String playerName, String ip) {
        if (playerName == null || ip == null) return false;

        for (Map.Entry<String, List<IpHistoryEntry>> entry : history.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(playerName)) {
                continue;
            }

            for (IpHistoryEntry historyEntry : entry.getValue()) {
                if (historyEntry.ip.equals(ip)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isLoopback(String ip) {
        return ip == null || ip.equals("127.0.0.1") || ip.equals("0.0.0.0") || ip.startsWith("127.");
    }

    public int countDistinctIpsInWindow(String playerName, long millis) {
        if (playerName == null) return 0;
        List<IpHistoryEntry> entries = history.getOrDefault(playerName, Collections.emptyList());
        long now = System.currentTimeMillis();
        int count = 0;
        for (IpHistoryEntry entry : entries) {
            if (now - entry.timestamp <= millis) {
                count++;
            }
        }
        return count;
    }

    private static class IpHistoryEntry {
        private String ip;
        private long timestamp;

        private IpHistoryEntry(String ip, long timestamp) {
            this.ip = ip;
            this.timestamp = timestamp;
        }
    }
}
