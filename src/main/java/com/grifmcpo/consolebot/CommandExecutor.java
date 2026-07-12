package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class CommandExecutor {

    private final JavaPlugin plugin;

    public CommandExecutor(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Выполняет команду и возвращает ответ сервера
     */
    public String executeCommand(String command, String senderName) {
        StringBuilder response = new StringBuilder();

        // Получаем консоль как основу
        CommandSender console = Bukkit.getConsoleSender();

        // Создаём враппер вокруг консоли с переопределённым именем
        CommandSender customSender = new CommandSender() {
            @Override
            public void sendMessage(String message) {
                if (message != null && !message.isEmpty()) {
                    response.append(message).append("\n");
                }
            }

            @Override
            public void sendMessage(String[] messages) {
                for (String msg : messages) {
                    if (msg != null && !msg.isEmpty()) {
                        response.append(msg).append("\n");
                    }
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
                return console.spigot();
            }
        };

        // Выполняем команду
        Bukkit.dispatchCommand(customSender, command);

        String result = response.toString().trim();
        if (result.isEmpty()) {
            return "✅ Команда выполнена (ответа нет)";
        }

        return result;
    }
}
