package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TelegramBotHandler extends TelegramLongPollingBot {

    private final String botToken;
    private final TelegramConsoleBot plugin;
    private final PlayerManager playerManager;
    private final CommandLogger commandLogger;
    private final LogsCommand logsCommand;
    private final CommandExecutor commandExecutor;

    public TelegramBotHandler(String token, TelegramConsoleBot plugin, PlayerManager playerManager,
                              CommandLogger commandLogger, LogsCommand logsCommand, CommandExecutor commandExecutor) {
        this.botToken = token;
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.commandLogger = commandLogger;
        this.logsCommand = logsCommand;
        this.commandExecutor = commandExecutor;
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
        // --- КНОПКИ (2FA) ---
        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            String chatId = update.getCallbackQuery().getMessage().getChatId().toString();

            if (data.startsWith("auth_allow_")) {
                String playerName = data.substring(11);
                playerManager.refreshSession(playerName);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player player = Bukkit.getPlayerExact(playerName);
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§aВход разрешён через Telegram!");
                    }
                });
                sendMessage(Long.parseLong(chatId), "✅ Вход для " + playerName + " разрешён!");
            } else if (data.startsWith("auth_deny_")) {
                String playerName = data.substring(10);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player player = Bukkit.getPlayerExact(playerName);
                    if (player != null && player.isOnline()) {
                        player.kickPlayer("§cВход запрещён через Telegram!");
                    }
                });
                sendMessage(Long.parseLong(chatId), "❌ Вход для " + playerName + " запрещён!");
            }
            return;
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String messageText = update.getMessage().getText();
        long userId = update.getMessage().getFrom().getId();
        long chatId = update.getMessage().getChatId();

        plugin.getLogger().info("📩 Получено: " + messageText + " от " + userId);

        // --- /start ---
        if (messageText.equalsIgnoreCase("/start")) {
            sendWelcome(chatId);
            return;
        }

        // --- /reg ---
        if (messageText.startsWith("/reg ") || messageText.startsWith("/register ")) {
            String[] parts = messageText.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: /reg <ник> <пароль>\nПример: /reg pley1657 mypass123");
                return;
            }

            String playerName = parts[1];
            String password = parts[2];

            Player player = Bukkit.getPlayerExact(playerName);
            if (player == null && Bukkit.getPlayerUniqueId(playerName) == null) {
                sendMessage(chatId, "❌ Игрок " + playerName + " не найден на сервере!");
                return;
            }

            if (playerManager.registerPlayer(playerName, password, String.valueOf(userId))) {
                sendMessage(chatId, "✅ Аккаунт " + playerName + " зарегистрирован!\n\n" +
                        "🔐 Теперь просто заходи на сервер.\n" +
                        "📱 Бот запросит подтверждение входа.");
            } else {
                sendMessage(chatId, "❌ Игрок " + playerName + " уже зарегистрирован!");
            }
            return;
        }

        // --- !rcon ---
        if (!messageText.startsWith("!rcon")) {
            return;
        }

        if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
            sendMessage(chatId, "⛔ Доступ запрещён.");
            return;
        }

        String command = messageText.substring(6).trim();
        if (command.isEmpty()) {
            sendMessage(chatId, "ℹ️ Введи команду после !rcon");
            return;
        }

        // --- !rcon logs ---
        if (command.startsWith("logs ")) {
            String[] args = command.split(" ");
            SendMessage response = logsCommand.handleLogs(chatId, args);
            try {
                execute(response);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;
        }

        // --- admin add/remove/list ---
        if (command.startsWith("admin ")) {
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon admin add <айди> <текст>  или  !rcon admin remove <айди>");
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
                    sendMessage(chatId, "❌ Укажи текст: !rcon admin add <айди> <текст>");
                    return;
                }
                String customText = parts[3];
                plugin.addAdmin(adminId, customText);
                sendMessage(chatId, "✅ Админ с ID " + adminId + " добавлен");
            } else if (action.equalsIgnoreCase("remove")) {
                if (!plugin.getAdmins().containsKey(adminId)) {
                    sendMessage(chatId, "❌ Админ с ID " + adminId + " не найден.");
                    return;
                }
                plugin.removeAdmin(adminId);
                sendMessage(chatId, "✅ Админ с ID " + adminId + " удалён.");
            } else {
                sendMessage(chatId, "❌ Неизвестное действие.");
            }
            return;
        }

        if (command.equalsIgnoreCase("admin list")) {
            StringBuilder list = new StringBuilder("📋 Список администраторов:\n");
            for (String id : plugin.getAdmins().keySet()) {
                list.append("• ").append(id).append(" → ").append(plugin.getAdmins().get(id)).append("\n");
            }
            sendMessage(chatId, list.toString());
            return;
        }

        // --- ВЫПОЛНЕНИЕ КОМАНДЫ С ПОЛУЧЕНИЕМ ОТВЕТА ---
        String customSender = plugin.getCustomSender(userId);
        if (customSender == null && userId == plugin.getOwnerId()) {
            customSender = "RCON";
        }

        final String finalCommand = command;
        final String finalCustomSender = customSender;

        // Отправляем сообщение о выполнении
        sendMessage(chatId, "⏳ Выполняю: " + command);

        Bukkit.getScheduler().runTask(plugin, () -> {
            // Получаем ответ от сервера
            String response = commandExecutor.executeCommand(finalCommand, finalCustomSender);
            
            // Форматируем ответ
            String formattedResponse = commandExecutor.formatResponse(finalCommand, response);
            
            // Отправляем ответ в Telegram
            sendMessage(chatId, "📋 Ответ от сервера:\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" + response + "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        });
    }

    // --- ПРИВЕТСТВИЕ ---
    private void sendWelcome(long chatId) {
        String welcome = "🎮 Добро пожаловать!\n" +
                "━━━━━━━━━━━━━━━━━━━━\n" +
                "📝 Регистрация:\n" +
                "/reg <ник> <пароль> — привязать аккаунт\n" +
                "Пример: /reg pley1657 mypass123\n\n" +
                "🔐 После регистрации просто заходи на сервер.\n" +
                "Бот запросит подтверждение входа.\n" +
                "━━━━━━━━━━━━━━━━━━━━\n" +
                "💡 Для помощи напиши /help";
        sendMessage(chatId, welcome);
    }

    // --- ОТПРАВКА СООБЩЕНИЯ ---
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
