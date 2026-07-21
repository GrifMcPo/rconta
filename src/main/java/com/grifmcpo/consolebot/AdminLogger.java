package com.grifmcpo.consolebot; 

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AdminLogger {

    private final JavaPlugin plugin;
    private final File logFile;

    public AdminLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "admin_logs.txt");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("❌ Не удалось создать admin_logs.txt");
            }
        }
    }

    public void log(String action, String target, String issuer, String reason, String duration, String visibility) {
        String timestamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
        String logEntry = String.format("[%s] %s | Цель: %s | Выдал: %s | Причина: %s | Срок: %s | Видимость: %s%n",
                timestamp, action, target, issuer, reason, duration, visibility);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(logEntry);
        } catch (IOException e) {
            plugin.getLogger().severe("❌ Ошибка записи в лог: " + e.getMessage());
        }
    }
}
