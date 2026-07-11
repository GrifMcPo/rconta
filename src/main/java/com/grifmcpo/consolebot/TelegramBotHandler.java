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
            long userId = update.getMessage().getFrom().getId();
            long chatId = update.getMessage().getChatId();

            // Логируем все сообщения
            plugin.getLogger().info("🔥🔥🔥 ПОЛУЧЕНО СООБЩЕНИЕ ОТ TELEGRAM! 🔥🔥🔥");
            plugin.getLogger().info("📩 Текст: " + messageText);
            plugin.getLogger().info("🆔 От пользователя: " + userId);
            plugin.getLogger().info("📌 Команда начинается с '!rcon'? " + messageText.startsWith("!rcon"));

            if (!messageText.startsWith("!rcon")) {
                return;
            }

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
            String customSender = plugin.getCustomSender(userId);
            if (customSender == null && userId == plugin.getOwnerId()) {
                customSender = "RCON@pley1657"; // Твой кастомный текст для владельца
            }

            // Отправляем подтверждение
            sendMessage(chatId, "✅ Команда выполняется от имени " + customSender + ": " + command);

            final String finalCommand = command;
            final String finalCustomSender = customSender;

            // Выполняем команду от консоли
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Выполняем команду от консоли
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);

                // Если команда начинается с "ban " или "kick " — отправляем кастомное сообщение в чат
                if (finalCommand.startsWith("ban ") || finalCommand.startsWith("kick ")) {
                    String[] parts = finalCommand.split(" ");
                    if (parts.length >= 2) {
                        String playerName = parts[1];
                        String reason = finalCommand.substring(finalCommand.indexOf(playerName) + playerName.length()).trim();
                        if (reason.isEmpty()) {
                            reason = "Без причины";
                        }
                        String action = finalCommand.startsWith("ban ") ? "забанен" : "кикнут";
                        Bukkit.broadcastMessage("§c" + finalCustomSender + " §f" + action + " игрока §e" + playerName + " §fпо причине: §6" + reason);
                    }
                }

                // Если команда начинается с "mute " — отправляем кастомное сообщение в чат
                if (finalCommand.startsWith("mute ")) {
                    String[] parts = finalCommand.split(" ");
                    if (parts.length >= 2) {
                        String playerName = parts[1];
                        String reason = finalCommand.substring(finalCommand.indexOf(playerName) + playerName.length()).trim();
                        if (reason.isEmpty()) {
                            reason = "Без причины";
                        }
                        Bukkit.broadcastMessage("§c" + finalCustomSender + " §fзамутил игрока §e" + playerName + " §fпо причине: §6" + reason);
                    }
                }

                // Если команда "list" — отправляем список игроков в чат
                if (finalCommand.equalsIgnoreCase("list")) {
                    // Это уже сделает сама команда, но мы можем добавить кастомное сообщение
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
