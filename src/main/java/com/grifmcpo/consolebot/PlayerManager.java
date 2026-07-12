package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerManager {

    private final JavaPlugin plugin;
    private File authFile;
    private FileConfiguration authConfig;
    private final Map<String, String> pendingCodes = new HashMap<>();
    private final Map<UUID, String> playerSessions = new HashMap<>();
    private final Map<String, Long> codeTimestamps = new HashMap<>();

    public PlayerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadAuthData();
    }

    private void loadAuthData() {
        authFile = new File(plugin.getDataFolder(), "auth.yml");
        if (!authFile.exists()) {
            try {
                authFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("❌ Не удалось создать auth.yml: " + e.getMessage());
            }
        }
        authConfig = YamlConfiguration.loadConfiguration(authFile);
    }

    public void saveAuthData() {
        try {
            authConfig.save(authFile);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка сохранения auth.yml: " + e.getMessage());
        }
    }

    public boolean registerPlayer(String playerName, String password, String telegramId) {
        if (isRegistered(playerName)) {
            return false;
        }

        UUID uuid = Bukkit.getPlayerUniqueId(playerName);
        if (uuid == null) {
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null) {
                uuid = player.getUniqueId();
            } else {
                return false;
            }
        }

        authConfig.set(playerName + ".telegramId", telegramId);
        authConfig.set(playerName + ".uuid", uuid.toString());
        authConfig.set(playerName + ".password", password);
        authConfig.set(playerName + ".ip", getPlayerIP(playerName));
        authConfig.set(playerName + ".sessionTime", System.currentTimeMillis());
        authConfig.set(playerName + ".registered", true);
        saveAuthData();
        playerSessions.put(uuid, telegramId);
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
            if (id != null && id.equals(telegramId)) {
                return key;
            }
        }
        return null;
    }

    public boolean checkPassword(String playerName, String password) {
        String savedPassword = authConfig.getString(playerName + ".password");
        return savedPassword != null
