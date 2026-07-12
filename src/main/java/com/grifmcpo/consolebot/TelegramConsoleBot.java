package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class TelegramConsoleBot extends JavaPlugin {

    private Map<String, String> admins = new HashMap<>();
    private long ownerId = 8889631346L;
    private File adminsFile;
    private PlayerManager playerManager;
    private CommandLogger commandLogger;
    private LogsCommand logsCommand;
    private CommandExecutor commandExecutor;
    private PunishmentManager punishmentManager;
    private AdminLogger adminLogger;
    private AuthManager authManager;
    private TelegramBotHandler botHandler;

    @Override
    public void onEnable() {
        getLogger().info("✅ ConsoleBot включен!");

        saveDefaultConfig();
        String token = getConfig().getString("telegram-token");
        if (token == null || token.isEmpty()) {
            getLogger().severe("❌ Токен не найден в config.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadAdmins();
        playerManager = new PlayerManager(this);
        commandLogger = new CommandLogger(this);
        logsCommand = new LogsCommand(this);
        commandExecutor = new CommandExecutor(this);
        adminLogger = new AdminLogger(this);
        punishmentManager = new PunishmentManager(this, adminLogger);
        authManager = new AuthManager(this);

        Bukkit.getPluginManager().registerEvents(new CommandListener(commandLogger, punishmentManager, authManager), this);

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botHandler = new TelegramBotHandler(token, this, playerManager, commandLogger, logsCommand, commandExecutor, punishmentManager, authManager);
            botsApi.registerBot(botHandler);
            
            // Передаём botHandler в AuthManager
            authManager.setBotHandler(botHandler);
            
            getLogger().info("✅ Telegram-бот успешно зарегистрирован!");
        } catch (TelegramApiException e) {
            getLogger().severe("❌ Ошибка при регистрации бота: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ... остальные методы без изменений
}
