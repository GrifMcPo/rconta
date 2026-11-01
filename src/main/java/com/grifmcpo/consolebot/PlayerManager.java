package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerManager {

    private final JavaPlugin plugin;
    private final IpHistoryTracker ipHistoryTracker;
    private File authFile;
    private FileConfiguration authConfig;
    private final Map<String, String> pendingCodes = new HashMap<>();
    private final Map<UUID, String> playerSessions = new HashMap<>();
    private final Map<String, Long> codeTimestamps = new HashMap<>();

    public PlayerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.ipHistoryTracker = new IpHistoryTracker();
        loadAuthData();
    }

    private void loadAuthData() {
        authFile = new File(plugin.getDataFolder(), "auth.yml");
        if (!authFile.exists()) {
            try {
                authFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("Не удалось создать auth.yml");
            }
        }
        authConfig = YamlConfiguration.loadConfiguration(authFile);
    }

    public void saveAuthData() {
        try {
            authConfig.save(authFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка сохранения auth.yml");
        }
    }

    public String generateCode(String playerName) {
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        pendingCodes.put(code, playerName);
        codeTimestamps.put(code, System.currentTimeMillis());
        plugin.getLogger().info("Код для " + playerName + ": " + code);
        return code;
    }

    public boolean registerPlayer(String code, String telegramId) {
        if (!pendingCodes.containsKey(code)) return false;

        Long timestamp = codeTimestamps.get(code);
        if (timestamp == null || (System.currentTimeMillis() - timestamp) > 5 * 60 * 1000) {
            pendingCodes.remove(code);
            codeTimestamps.remove(code);
            return false;
        }

        String playerName = pendingCodes.remove(code);
        codeTimestamps.remove(code);
        UUID uuid = Bukkit.getPlayerUniqueId(playerName);
        if (uuid == null) {
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null) uuid = player.getUniqueId();
            else return false;
        }

        String ip = normalizeIp(getPlayerIP(playerName));
        authConfig.set(playerName + ".telegramId", telegramId);
        authConfig.set(playerName + ".uuid", uuid.toString());
        authConfig.set(playerName + ".ip", ip != null ? ip : "0.0.0.0");
        authConfig.set(playerName + ".sessionTime", System.currentTimeMillis());
        authConfig.set(playerName + ".registered", true);
        saveAuthData();
        playerSessions.put(uuid, telegramId);

        if (ip != null) {
            ipHistoryTracker.recordIpHistory(playerName, ip);
        }

        return true;
    }

    public boolean isRegistered(String playerName) {
        return authConfig.getBoolean(playerName + ".registered", false);
    }

    public boolean isRegistered(UUID uuid) {
        return playerSessions.containsKey(uuid);
    }

    public String getTelegramId(String playerName) {
        return authConfig.getString(playerName + ".telegramId");
    }

    public String getPlayerNameByTelegram(String telegramId) {
        if (telegramId == null) return null;
        for (String key : authConfig.getKeys(false)) {
            String id = authConfig.getString(key + ".telegramId");
            if (id != null && id.equals(telegramId)) return key;
        }
        return null;
    }

    public boolean isSessionValid(String playerName) {
        long sessionTime = authConfig.getLong(playerName + ".sessionTime", 0);
        return (System.currentTimeMillis() - sessionTime) < 12 * 60 * 60 * 1000;
    }

    public void refreshSession(String playerName) {
        authConfig.set(playerName + ".sessionTime", System.currentTimeMillis());
        saveAuthData();
    }

    public boolean isIPMatch(String playerName, String ip) {
        String savedIP = authConfig.getString(playerName + ".ip");
        return savedIP != null && savedIP.equals(normalizeIp(ip));
    }

    public void updateIP(String playerName, String ip) {
        String normalizedIp = normalizeIp(ip);
        if (normalizedIp != null) {
            authConfig.set(playerName + ".ip", normalizedIp);
            saveAuthData();
            ipHistoryTracker.recordIpHistory(playerName, normalizedIp);
        }
    }

    public String getPlayerIp(String playerName) {
        String ip = authConfig.getString(playerName + ".ip");
        String normalized = normalizeIp(ip);
        if (normalized != null) {
            return normalized;
        }

        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null && player.isOnline() && player.getAddress() != null) {
            String address = normalizeIp(player.getAddress().getHostString());
            if (address != null) {
                authConfig.set(playerName + ".ip", address);
                saveAuthData();
                ipHistoryTracker.recordIpHistory(playerName, address);
                return address;
            }
        }

        return "—";
    }

    public List<String> getPlayersByIp(String ip) {
        List<String> players = new ArrayList<>();
        String normalizedIp = normalizeIp(ip);
        if (normalizedIp == null) return players;

        for (String key : authConfig.getKeys(false)) {
            String savedIp = authConfig.getString(key + ".ip");
            if (normalizedIp.equals(normalizeIp(savedIp))) {
                if (!players.contains(key)) players.add(key);
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerIp = player.getAddress() != null ? normalizeIp(player.getAddress().getHostString()) : null;
            if (normalizedIp.equals(playerIp) && !players.contains(player.getName())) {
                players.add(player.getName());
            }
        }

        return players;
    }

    public boolean isProxyLoopback(String ip) {
        return ip == null || ip.equals("127.0.0.1") || ip.equals("0.0.0.0") || ip.startsWith("127.");
    }

    public boolean isPotentialDynamicIp(String playerName, String ip) {
        return ipHistoryTracker.isLikelyDynamicIp(playerName, ip);
    }

    public boolean isPotentialIpEscape(String playerName, String ip) {
        return ipHistoryTracker.isPotentialIpEscape(playerName, ip);
    }

    public List<String> getIpHistory(String playerName) {
        return ipHistoryTracker.getRecentHistory(playerName);
    }

    public void recordIpHistory(String playerName, String ip) {
        if (playerName == null || ip == null) return;
        ipHistoryTracker.recordIpHistory(playerName, normalizeIp(ip));
    }

    private String normalizeIp(String ip) {
        if (ip == null) return null;
        String result = ip.trim();
        if (result.isEmpty() || result.equals("—") || result.equals("0.0.0.0") || result.equals("127.0.0.1")) {
            return null;
        }
        return result.matches("\\d{1,3}(\\.\\d{1,3}){3}") ? result : null;
    }

    private String getPlayerIP(String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null && player.getAddress() != null) {
            String ip = normalizeIp(player.getAddress().getHostString());
            if (ip != null && !isProxyLoopback(ip)) {
                return ip;
            }
            return player.getAddress().getHostString();
        }
        return "0.0.0.0";
    }

    public void unregister(String playerName) {
        authConfig.set(playerName, null);
        playerSessions.entrySet().removeIf(entry -> {
            String name = getPlayerNameByTelegram(entry.getValue());
            return name != null && name.equals(playerName);
        });
        ipHistoryTracker.clearHistory(playerName);
        saveAuthData();
    }

    public void kickAccount(String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null && player.isOnline()) {
            player.kickPlayer("Аккаунт был исключен с бота");
        }
    }
}
