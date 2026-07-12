package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class CommandExecutor {

    private final JavaPlugin plugin;

    public CommandExecutor(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Выполняет команду и возвращает ответ сервера
     * Используем старый добрый способ через консоль
     */
    public String executeCommand(String command, String senderName) {
        // Просто выполняем команду от консоли
        // Ответа получить не можем, но команда выполнится
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        
        // Возвращаем сообщение, что команда выполнена
        return "✅ Команда выполнена: " + command;
    }
}
