package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.Spigot;
import org.bukkit.plugin.java.JavaPlugin;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class TelegramBotHandler extends TelegramLongPollingBot {

    private final String botToken;
    private final TelegramConsoleBot plugin;

    public TelegramBotHandler(String token, TelegramConsoleBot plugin) {
        this.botToken = token;
        this.plugin = plugin;
    }

    @Override
    public String getBotUsername() {
        return "TelegramConsoleBot";
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long userId = update.getMessage().getFrom().getId();
            long chatId = update.getMessage().getChatId();

            // Логируем все сообщения
            plugin.getLogger().info("🔥🔥🔥 ПОЛУЧЕНО СООБЩЕНИЕ ОТ TELEGRAM! 🔥🔥🔥");
            plugin.getLogger().info("📩 Текст: " + messageText);
            plugin.getLogger().info("🆔 От пользователя: " + userId);
            plugin.getLogger().info("📌 Команда начинается с '!rcon'? " + messageText.startsWith("!rcon"));

            // Проверяем, что команда начинается с !rcon
            if (!messageText.startsWith("!rcon")) {
                return;
            }

            // Убираем "!rcon " из команды
            String command = messageText.substring(6).trim();
            if (command.isEmpty()) {
                sendMessage(chatId, "ℹ️ Введите команду после !rcon");
                return;
            }

            // --- ОБРАБОТКА АДМИН-КОМАНД ---
            if (command.startsWith("admin ")) {
                String[] parts = command.split(" ");
                if (parts.length < 3) {
                    sendMessage(chatId, "❌ Используйте: !rcon admin add <айди> <текст>  или  !rcon admin remove <айди>");
                    return;
                }

                if (userId != plugin.getOwnerId()) {
                    sendMessage(chatId, "⛔ Только владелец может управлять админами!");
                    return;
                }

                String action = parts[1];
                String adminId = parts[2];

                if (action.equalsIgnoreCase("add")) {
                    if (parts.length < 4) {
                        sendMessage(chatId, "❌ Укажите кастомный текст: !rcon admin add <айди> <текст>");
                        return;
                    }
                    String customText = parts[3];
                    plugin.addAdmin(adminId, customText);
                    sendMessage(chatId, "✅ Админ с ID " + adminId + " добавлен с текстом: " + customText);
                } else if (action.equalsIgnoreCase("remove")) {
                    if (!plugin.getAdmins().containsKey(adminId)) {
                        sendMessage(chatId, "❌ Админ с ID " + adminId + " не найден.");
                        return;
                    }
                    plugin.removeAdmin(adminId);
                    sendMessage(chatId, "✅ Админ с ID " + adminId + " удалён.");
                } else {
                    sendMessage(chatId, "❌ Неизвестное действие. Используйте add или remove.");
                }
                return;
            }

            // --- ОБРАБОТКА КОМАНДЫ LIST (список админов) ---
            if (command.equalsIgnoreCase("admin list")) {
                StringBuilder list = new StringBuilder("📋 Список администраторов:\n");
                for (String id : plugin.getAdmins().keySet()) {
                    list.append("• ").append(id).append(" → ").append(plugin.getAdmins().get(id)).append("\n");
                }
                sendMessage(chatId, list.toString());
                return;
            }

            // --- ПРОВЕРКА ПРАВ (только админы) ---
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ У вас нет прав для выполнения команд.");
                return;
            }

            // --- ПОЛУЧАЕМ КАСТОМНЫЙ ТЕКСТ ДЛЯ ЭТОГО ПОЛЬЗОВАТЕЛЯ ---
            String customSenderName = plugin.getCustomSender(userId);
            if (customSenderName == null && userId == plugin.getOwnerId()) {
                customSenderName = "RCON@Grif_Mo";
            }

            final String finalCommand = command;
            final String finalSenderName = customSenderName;

            // Отправляем подтверждение в Telegram
            sendMessage(chatId, "✅ Команда выполняется от имени " + finalSenderName + ": " + command);

            // Выполняем команду от кастомного отправителя
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Создаём кастомного отправителя
                CommandSender customSender = new CommandSender() {
                    @Override
                    public void sendMessage(String message) {}
                    @Override
                    public void sendMessage(String[] messages) {}
                    @Override
                    public String getName() {
                        return finalSenderName;
                    }
                    @Override
                    public boolean isPermissionSet(String name) { return true; }
                    @Override
                    public boolean hasPermission(String name) { return true; }
                    @Override
                    public boolean hasPermission(Permission perm) { return true; }
                    @Override
                    public boolean isOp() { return true; }
                    @Override
                    public void setOp(boolean value) {}
                    @Override
                    public Spigot spigot() { return null; }
                };

                // Выполняем команду от кастомного отправителя
                boolean success = Bukkit.dispatchCommand(customSender, finalCommand);
                if (!success) {
                    plugin.getLogger().warning("❌ Команда не выполнена: " + finalCommand);
                }
            });
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
