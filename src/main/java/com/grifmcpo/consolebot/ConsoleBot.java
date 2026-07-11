package com.grifmcpo.consolebot;

import org.bukkit.plugin.java.JavaPlugin;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class TelegramConsoleBot extends JavaPlugin {

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

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            // Передаём ссылку на плагин в бота
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
}
