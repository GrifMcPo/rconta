package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
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
            long chatId = update.getMessage().getChatId();
            long userId = update.getMessage().getFrom().getId();

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
                    sendMessage(chatId, "❌ Используйте: !rcon admin add <айди> <ник>  или  !rcon admin remove <айди>");
                    return;
                }

                // Проверяем, что команду отправил ОВНЕР (только ты)
                if (userId != plugin.getOwnerId()) {
                    sendMessage(chatId, "⛔ Только владелец может управлять админами!");
                    return;
                }

                String action = parts[1];
                String adminId = parts[2];

                if (action.equalsIgnoreCase("add")) {
                    if (parts.length < 4) {
                        sendMessage(chatId, "❌ Укажите ник игрока: !rcon admin add <айди> <ник>");
                        return;
                    }
                    String playerName = parts[3];
                    plugin.addAdmin(adminId, playerName);
                    sendMessage(chatId, "✅ Админ " + playerName + " (ID: " + adminId + ") добавлен!");
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
                if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                    sendMessage(chatId, "⛔ У вас нет прав.");
                    return;
                }
                StringBuilder list = new StringBuilder("📋 Список администраторов:\n");
                for (String id : plugin.getAdmins().keySet()) {
                    list.append("• ").append(id).append(" → ").append(plugin.getAdmins().get(id)).append("\n");
                }
                sendMessage(chatId, list.toString());
                return;
            }

            // --- ПРОВЕРКА ПРАВ (только админы могут выполнять команды) ---
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ У вас нет прав для выполнения команд.");
                return;
            }

            // --- ВЫПОЛНЕНИЕ ОСНОВНОЙ КОМАНДЫ ---
            // Получаем ник игрока для подмены отправителя
            String playerName = plugin.getPlayerName(userId);
            if (playerName == null && userId == plugin.getOwnerId()) {
                playerName = "pley1657"; // твой ник, если владелец
            }

            // Отправляем подтверждение
            sendMessage(chatId, "✅ Команда выполняется от имени " + playerName + ": " + command);

            // FIX: Создаём финальные копии переменных для лямбды
            final String finalCommand = command;
            final String finalPlayerName = playerName;

            // Выполняем команду от имени игрока (через sudo)
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (finalPlayerName != null && !finalPlayerName.isEmpty()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sudo " + finalPlayerName + " " + finalCommand);
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
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
