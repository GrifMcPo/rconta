package com.grifmcpo.consolebot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class TelegramConsoleBot extends JavaPlugin {

    private Map<String, String> admins = new HashMap<>();
    private long ownerId = 8308522569L; // ТВОЙ TELEGRAM ID
    private File adminsFile;
    private FileConfiguration adminsConfig;

    @Override
    public void onEnable() {
        getLogger().info("✅ ConsoleBot включен!");

        // Загружаем конфиг
        saveDefaultConfig();
        String token = getConfig().getString("telegram-token");
        if (token == null || token.isEmpty()) {
            getLogger().severe("❌ Токен не найден в config.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Загружаем админов из admins.yml
        loadAdmins();

        // Регистрируем бота
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new TelegramBotHandler(token, this));
            getLogger().info("✅ Telegram-бот успешно зарегистрирован!");
        } catch (TelegramApiException e) {
            getLogger().severe("❌ Ошибка при регистрации бота: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("❌ ConsoleBot выключен.");
    }

    private void loadAdmins() {
        adminsFile = new File(getDataFolder(), "admins.yml");
        if (!adminsFile.exists()) {
            saveResource("admins.yml", false);
        }
        adminsConfig = YamlConfiguration.loadConfiguration(adminsFile);
        admins.clear();
        for (String key : adminsConfig.getKeys(false)) {
            admins.put(key, adminsConfig.getString(key));
        }
        getLogger().info("✅ Загружено администраторов: " + admins.size());
    }

    public void saveAdmins() {
        for (Map.Entry<String, String> entry : admins.entrySet()) {
            adminsConfig.set(entry.getKey(), entry.getValue());
        }
        try {
            adminsConfig.save(adminsFile);
        } catch (Exception e) {
            getLogger().severe("❌ Ошибка сохранения admins.yml: " + e.getMessage());
        }
    }

    public Map<String, String> getAdmins() {
        return admins;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public void addAdmin(String telegramId, String playerName) {
        admins.put(telegramId, playerName);
        saveAdmins();
    }

    public void removeAdmin(String telegramId) {
        admins.remove(telegramId);
        saveAdmins();
    }

    public boolean isAdmin(long telegramId) {
        return admins.containsKey(String.valueOf(telegramId));
    }

    public String getPlayerName(long telegramId) {
        return admins.get(String.valueOf(telegramId));
    }
}
