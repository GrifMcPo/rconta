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
    private final Map<String, String> pendingCodes = new HashMap<>(); // код -> игрок
    private final Map<UUID, String> playerSessions = new HashMap<>(); // UUID -> TelegramID

    public PlayerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadAuthData();
    }

    private void loadAuthData() {
        authFile = new File(plugin.getDataFolder(), "auth.yml");
        if (!authFile.exists()) {
            plugin.saveResource("auth.yml", false);
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

    // Генерация кода для привязки
    public String generateCode(String playerName) {
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        pendingCodes.put(code, playerName);
        plugin.getLogger().info("📝 Код для " + playerName + ": " + code);
        return code;
    }

    // Привязка аккаунта
    public boolean registerPlayer(String code, String telegramId) {
        if (!pendingCodes.containsKey(code)) {
            return false;
        }
        String playerName = pendingCodes.remove(code);
        UUID uuid = Bukkit.getPlayerUniqueId(playerName);
        if (uuid == null) return false;

        // Сохраняем данные
        authConfig.set(playerName + ".telegramId", telegramId);
        authConfig.set(playerName + ".uuid", uuid.toString());
        authConfig.set(playerName + ".ip", getPlayerIP(playerName));
        authConfig.set(playerName + ".sessionTime", System.currentTimeMillis());
        authConfig.set(playerName + ".registered", true);
        saveAuthData();
        playerSessions.put(uuid, telegramId);
        return true;
    }

    // Проверка, зарегистрирован ли игрок
    public boolean isRegistered(String playerName) {
        return authConfig.getBoolean(playerName + ".registered", false);
    }

    public boolean isRegistered(UUID uuid) {
        return playerSessions.containsKey(uuid);
    }

    // Получение Telegram ID по нику
    public String getTelegramId(String playerName) {
        return authConfig.getString(playerName + ".telegramId");
    }

    // Проверка сессии (12 часов)
    public boolean isSessionValid(String playerName) {
        long sessionTime = authConfig.getLong(playerName + ".sessionTime", 0);
        return (System.currentTimeMillis() - sessionTime) < 12 * 60 * 60 * 1000;
    }

    // Обновление сессии
    public void refreshSession(String playerName) {
        authConfig.set(playerName + ".sessionTime", System.currentTimeMillis());
        saveAuthData();
    }

    // Проверка IP
    public boolean isIPMatch(String playerName, String ip) {
        String savedIP = authConfig.getString(playerName + ".ip");
        return savedIP != null && savedIP.equals(ip);
    }

    public void updateIP(String playerName, String ip) {
        authConfig.set(playerName + ".ip", ip);
        saveAuthData();
    }

    private String getPlayerIP(String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        return player != null ? player.getAddress().getHostString() : "0.0.0.0";
    }

    // Отвязка аккаунта
    public void unregister(String playerName) {
        authConfig.set(playerName, null);
        playerSessions.entrySet().removeIf(entry -> entry.getValue().equals(playerName));
        saveAuthData();
    }

    // Кикнуть аккаунт
    public void kickAccount(String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null && player.isOnline()) {
            player.kickPlayer("§cАккаунт был исключен с бота");
        }
    }
}
