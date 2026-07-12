package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.File;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {

    private final JavaPlugin plugin;
    private File authFile;
    private FileConfiguration authConfig;
    private final Map<String, String> pendingAuth = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingAuthTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> frozenPlayers = new ConcurrentHashMap<>();
    private TelegramBotHandler botHandler;

    private static final long AUTH_TIMEOUT = 3 * 60 * 1000;
    private static final long SESSION_DURATION = 12 * 60 * 60 * 1000;

    public AuthManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadAuthData();
    }

    public void setBotHandler(TelegramBotHandler botHandler) {
        this.botHandler = botHandler;
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

    // ===== ХЕШИРОВАНИЕ =====
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
    public boolean registerPlayer(String playerName, String telegramId, String ip) {
        UUID uuid = Bukkit.getPlayerUniqueId(playerName);
        if (uuid == null) return false;
        if (isRegistered(playerName)) return false;

        long now = System.currentTimeMillis();

        authConfig.set(playerName + ".uuid", uuid.toString());
        authConfig.set(playerName + ".telegram_id", telegramId);
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

    public String getPlayerUUID(String playerName) {
        return authConfig.getString(playerName + ".uuid");
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

    public String getPendingCode(String playerName) {
        UUID uuid = Bukkit.getPlayerUniqueId(playerName);
        if (uuid == null) return null;
        return pendingAuth.get(uuid.toString());
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

    public long getSessionExpires(String playerName) {
        return authConfig.getLong(playerName + ".session_expires", 0);
    }

    public String getSessionTimeLeft(String playerName) {
        long expires = getSessionExpires(playerName);
        long left = expires - System.currentTimeMillis();
        if (left <= 0) return "Истекла";
        long hours = left / (60 * 60 * 1000);
        long minutes = (left % (60 * 60 * 1000)) / (60 * 1000);
        return hours + "ч " + minutes + "м";
    }

    public boolean isFrozen(Player player) {
        return frozenPlayers.containsKey(player.getUniqueId());
    }

    public void freezePlayer(Player player) {
        frozenPlayers.put(player.getUniqueId(), player.getName());
        player.setWalkSpeed(0);
        player.setFlySpeed(0);
        player.sendMessage("§e🔐 Требуется подтверждение входа!");
        player.sendMessage("§e📝 Введите код из чата или подтвердите в Telegram");
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

    // ===== IP =====
    public boolean isIPDifferent(String playerName, String ip) {
        String lastIp = authConfig.getString(playerName + ".ip_last", "");
        return !lastIp.equals(ip);
    }

    public void updateIP(String playerName, String ip) {
        authConfig.set(playerName + ".ip_last", ip);
        saveAuthData();
    }

    // ===== ДАННЫЕ ДЛЯ !ME =====
    public String getRegisteredAt(String playerName) {
        long time = authConfig.getLong(playerName + ".registered_at", 0);
        if (time == 0) return "Неизвестно";
        return new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(time));
    }

    public boolean isSessionActive(String playerName) {
        return authConfig.getBoolean(playerName + ".session_active", false);
    }

    public String getIpLast(String playerName) {
        return authConfig.getString(playerName + ".ip_last", "Неизвестно");
    }

    // ===== ОТПРАВКА КНОПОК В TELEGRAM ПО НИКУ =====
    public void sendAuthButtons(String playerName, String ip) {
        String telegramId = getTelegramId(playerName);
        if (telegramId == null || telegramId.isEmpty() || telegramId.equals("0")) {
            plugin.getLogger().warning("❌ Telegram ID не найден для " + playerName);
            return;
        }

        if (botHandler == null) {
            plugin.getLogger().warning("❌ BotHandler не инициализирован");
            return;
        }

        try {
            SendMessage message = new SendMessage();
            message.setChatId(telegramId);
            message.setText("🔐 Подтверди вход на сервер:\n" +
                    "👤 Игрок: " + playerName + "\n" +
                    "🌐 IP: " + ip + "\n\n" +
                    "Разрешить вход?");

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton allowBtn = new InlineKeyboardButton();
            allowBtn.setText("✅ Разрешить");
            allowBtn.setCallbackData("auth_allow_" + playerName);

            InlineKeyboardButton denyBtn = new InlineKeyboardButton();
            denyBtn.setText("❌ Запроетить");
            denyBtn.setCallbackData("auth_deny_" + playerName);

            row.add(allowBtn);
            row.add(denyBtn);
            rows.add(row);
            markup.setKeyboard(rows);
            message.setReplyMarkup(markup);

            // Отправляем через бота
            try {
                java.lang.reflect.Method method = botHandler.getClass().getMethod("execute", org.telegram.telegrambots.meta.api.methods.BotApiMethod.class);
                method.invoke(botHandler, message);
                plugin.getLogger().info("✅ Кнопки отправлены в Telegram ID: " + telegramId);
            } catch (Exception e) {
                plugin.getLogger().warning("❌ Ошибка отправки кнопок: " + e.getMessage());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("❌ Ошибка создания кнопок: " + e.getMessage());
        }
    }
}
