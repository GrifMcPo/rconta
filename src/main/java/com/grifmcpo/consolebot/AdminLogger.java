package com.grifmcpo.consolebot;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class AdminLogger {

    private final JavaPlugin plugin;
    private final SimpleDateFormat dateFormat;
    private File logFile;

    public AdminLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
        initLogFile();
    }

    private void initLogFile() {
        logFile = new File(plugin.getDataFolder(), "bot_actions.log");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("❌ Не удалось создать bot_actions.log");
            }
        }
    }

    public void log(String telegramId, String command, String target, String reason, String status, String errorMessage) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            String timestamp = dateFormat.format(new Date());
            String logLine = String.format("[%s] | TG: %s | CMD: %s | TARGET: %s | REASON: %s | STATUS: %s | ERROR: %s",
                    timestamp, telegramId, command, target, reason, status, errorMessage != null ? errorMessage : "OK");
            writer.write(logLine);
            writer.newLine();
        } catch (IOException e) {
            plugin.getLogger().warning("❌ Ошибка записи лога админа: " + e.getMessage());
        }
    }

    public void logSuccess(String telegramId, String command, String target, String reason) {
        log(telegramId, command, target, reason, "SUCCESS", null);
    }

    public void logError(String telegramId, String command, String target, String reason, String error) {
        log(telegramId, command, target, reason, "ERROR", error);
    }
}
