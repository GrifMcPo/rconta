package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {

    private final JavaPlugin plugin;
    private File authFile;
    private FileConfiguration authConfig;
    private final Map<String, String> pendingAuth = new ConcurrentHashMap<>(); // UUID -> код
    private final Map<String, Long> pendingAuthTime = new ConcurrentHashMap<>(); // UUID -> время
    private final Map<UUID, String> frozenPlayers = new ConcurrentHashMap<>(); // замороженные игроки

    private static final long AUTH_TIMEOUT = 3 * 60 * 1000; // 3 минуты
    private static final long SESSION_DURATION = 12 * 60 * 60 * 1000; // 12 часов

    public AuthManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadAuthData();
    }

    private void loadAuthData() {
        authFile = new File(plugin.getDataFolder(), "auth.yml");
        if (!authFile.exists()) {
            try {
                authFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("❌ Не удалось создать auth.yml");
            }
        }
        authConfig = YamlConfiguration.loadConfiguration(authFile);
    }

    public void saveAuthData() {
        try {
            authConfig.save(authFile);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка сохранения auth.yml");
        }
    }

    // ===== ХЕШИРОВАНИЕ ПАРОЛЯ (SHA-256) =====
    public String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return password;
        }
    }

    public boolean checkPassword(String password, String hash) {
        return hashPassword(password).equals(hash);
    }

    // ===== РЕГИСТРАЦИЯ =====
    public boolean registerPlayer(String playerName, String password, String telegramId, String ip) {
        UUID uuid = Bukkit.getPlayerUniqueId(playerName);
        if (uuid == null) return false;
        if (isRegistered(playerName)) return false;

        String hash = hashPassword(password);
        long now = System.currentTimeMillis();

        authConfig.set(playerName + ".uuid", uuid.toString());
        authConfig.set(playerName + ".telegram_id", telegramId);
        authConfig.set(playerName + ".password_hash", hash);
        authConfig.set(playerName + ".ip_last", ip);
        authConfig.set(playerName + ".registered_at", now);
        authConfig.set(playerName + ".session_active", false);
        authConfig.set(playerName + ".session_expires", 0);
        authConfig.set(playerName + ".registered", true);

        saveAuthData();
        return true;
    }

    public boolean isRegistered(String playerName) {
        return authConfig.getBoolean(playerName + ".registered", false);
    }

    public boolean isRegisteredByTelegram(String telegramId) {
        for (String key : authConfig.getKeys(false)) {
            String id = authConfig.getString(key + ".telegram_id");
            if (id != null && id.equals(telegramId)) {
                return true;
            }
        }
        return false;
    }

    public String getTelegramId(String playerName) {
        return authConfig.getString(playerName + ".telegram_id");
    }

    public String getPlayerNameByTelegram(String telegramId) {
        for (String key : authConfig.getKeys(false)) {
            String id = authConfig.getString(key + ".telegram_id");
            if (id != null && id.equals(telegramId)) {
                return key;
            }
        }
        return null;
    }

    // ===== ГЕНЕРАЦИЯ КОДА =====
    public String generateAuthCode(String playerName) {
        String code = String.format("%06d", new Random().nextInt(999999));
        UUID uuid = Bukkit.getPlayerUniqueId(playerName);
        if (uuid != null) {
            pendingAuth.put(uuid.toString(), code);
            pendingAuthTime.put(uuid.toString(), System.currentTimeMillis());
        }
        return code;
    }

    public boolean verifyAuthCode(String playerName, String code) {
        UUID uuid = Bukkit.getPlayerUniqueId(playerName);
        if (uuid == null) return false;

        String storedCode = pendingAuth.get(uuid.toString());
        Long time = pendingAuthTime.get(uuid.toString());

        if (storedCode == null || time == null) return false;
        if (System.currentTimeMillis() - time > AUTH_TIMEOUT) {
            pendingAuth.remove(uuid.toString());
            pendingAuthTime.remove(uuid.toString());
            return false;
        }

        if (storedCode.equals(code)) {
            pendingAuth.remove(uuid.toString());
            pendingAuthTime.remove(uuid.toString());
            return true;
        }
        return false;
    }

    // ===== СЕССИЯ =====
    public boolean activateSession(String playerName, String ip) {
        if (!isRegistered(playerName)) return false;

        long now = System.currentTimeMillis();
        authConfig.set(playerName + ".session_active", true);
        authConfig.set(playerName + ".session_expires", now + SESSION_DURATION);
        authConfig.set(playerName + ".ip_last", ip);
        saveAuthData();
        return true;
    }

    public boolean validateSession(String playerName, String ip) {
        if (!isRegistered(playerName)) return false;

        boolean active = authConfig.getBoolean(playerName + ".session_active", false);
        long expires = authConfig.getLong(playerName + ".session_expires", 0);
        String lastIp = authConfig.getString(playerName + ".ip_last", "");

        if (!active) return false;
        if (System.currentTimeMillis() > expires) return false;
        if (!lastIp.equals(ip)) return false;

        return true;
    }

    public boolean isFrozen(Player player) {
        return frozenPlayers.containsKey(player.getUniqueId());
    }

    public void freezePlayer(Player player) {
        frozenPlayers.put(player.getUniqueId(), player.getName());
        player.setWalkSpeed(0);
        player.setFlySpeed(0);
        player.sendMessage("§e🔐 Требуется подтверждение входа! Введите код из Telegram.");
    }

    public void unfreezePlayer(Player player) {
        frozenPlayers.remove(player.getUniqueId());
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.sendMessage("§a✅ Вход подтверждён! Добро пожаловать!");
    }

    public void kickPlayer(Player player, String reason) {
        frozenPlayers.remove(player.getUniqueId());
        player.kickPlayer("§c" + reason);
    }

    // ===== ПРОВЕРКА IP =====
    public boolean isIPDifferent(String playerName, String ip) {
        String lastIp = authConfig.getString(playerName + ".ip_last", "");
        return !lastIp.equals(ip);
    }

    public void updateIP(String playerName, String ip) {
        authConfig.set(playerName + ".ip_last", ip);
        saveAuthData();
    }
}
