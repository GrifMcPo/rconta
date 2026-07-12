package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class CommandExecutor {

    private final JavaPlugin plugin;

    public CommandExecutor(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Выполняет команду и возвращает ответ сервера
     * @param command Команда для выполнения
     * @param senderName Имя отправителя (для красивого вывода)
     * @return Ответ сервера в виде строки
     */
    public String executeCommand(String command, String senderName) {
        StringBuilder response = new StringBuilder();
        
        // Создаём кастомного отправителя, который перехватывает сообщения
        CommandSender customSender = new CommandSender() {
            @Override
            public void sendMessage(String message) {
                response.append(message).append("\n");
            }

            @Override
            public void sendMessage(String[] messages) {
                for (String msg : messages) {
                    response.append(msg).append("\n");
                }
            }

            @Override
            public String getName() {
                return senderName != null ? senderName : "RCON";
            }

            @Override
            public boolean isPermissionSet(String name) {
                return true;
            }

            @Override
            public boolean hasPermission(String name) {
                return true;
            }

            @Override
            public boolean hasPermission(Permission perm) {
                return true;
            }

            @Override
            public boolean isOp() {
                return true;
            }

            @Override
            public void setOp(boolean value) {}

            @Override
            public Spigot spigot() {
                return Bukkit.getConsoleSender().spigot();
            }
        };

        // Выполняем команду
        Bukkit.dispatchCommand(customSender, command);

        // Если ответ пустой, возвращаем сообщение об этом
        if (response.toString().trim().isEmpty()) {
            return "Команда выполнена, ответа нет.";
        }

        return response.toString().trim();
    }

    /**
     * Форматирует ответ для отправки в Telegram
     * @param command Исходная команда
     * @param response Ответ сервера
     * @return Отформатированное сообщение
     */
    public String formatResponse(String command, String response) {
        if (response == null || response.isEmpty()) {
            return "⚠️ Команда выполнена, но ответа нет.";
        }

        // Убираем лишние пробелы и переносы
        String cleanResponse = response.replaceAll("\\s+", " ").trim();

        // Если ответ слишком длинный, обрезаем
        if (cleanResponse.length() > 4000) {
            cleanResponse = cleanResponse.substring(0, 4000) + "...\n(сообщение обрезано)";
        }

        return "📋 Ответ от сервера:\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" + cleanResponse + "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
    }
}
