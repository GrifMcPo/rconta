package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

public class CommandExecutor {

    private final JavaPlugin plugin;

    public CommandExecutor(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String executeCommand(String command, String senderName) {
        StringBuilder response = new StringBuilder();
        
        // Создаём кастомного отправителя
        CommandSender customSender = new ResponseCommandSender(senderName != null ? senderName : "RCON", response);
        Bukkit.dispatchCommand(customSender, command);

        String result = response.toString().trim();
        if (result.isEmpty()) {
            return "✅ Команда выполнена (ответа нет)";
        }
        return result;
    }

    // Внутренний класс для перехвата ответов
    private static class ResponseCommandSender implements CommandSender {

        private final String name;
        private final StringBuilder output;

        public ResponseCommandSender(String name, StringBuilder output) {
            this.name = name;
            this.output = output;
        }

        @Override
        public void sendMessage(String message) {
            if (message != null && !message.isEmpty()) {
                output.append(message).append("\n");
            }
        }

        @Override
        public void sendMessage(String[] messages) {
            for (String msg : messages) {
                if (msg != null && !msg.isEmpty()) {
                    output.append(msg).append("\n");
                }
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isPermissionSet(String name) {
            return true;
        }

        @Override
        public boolean isPermissionSet(org.bukkit.permissions.Permission perm) {
            return true;
        }

        @Override
        public boolean hasPermission(String name) {
            return true;
        }

        @Override
        public boolean hasPermission(org.bukkit.permissions.Permission perm) {
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
    }
}
