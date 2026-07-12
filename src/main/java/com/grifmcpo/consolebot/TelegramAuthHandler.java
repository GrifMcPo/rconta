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

    public TelegramBotHandler(String token, TelegramConsoleBot plugin, PlayerManager playerManager, 
                              CommandLogger commandLogger, LogsCommand logsCommand) {
        this.botToken = token;
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.commandLogger = commandLogger;
        this.logsCommand = logsCommand;
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
        // --- ОБРАБОТКА КНОПОК (2FA) ---
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
                sendMessage(Long.parseLong(chatId), "✅ Вход для **" + playerName + "** разрешён!");
            } else if (data.startsWith("auth_deny_")) {
                String playerName = data.substring(10);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player player = Bukkit.getPlayerExact(playerName);
                    if (player != null && player.isOnline()) {
                        player.kickPlayer("§cВход запрещён через Telegram!");
                    }
                });
                sendMessage(Long.parseLong(chatId), "❌ Вход для **" + playerName + "** запрещён!");
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

        // --- КОМАНДА /start ---
        if (messageText.equalsIgnoreCase("/start")) {
            sendWelcome(chatId);
            return;
        }

        // --- КОМАНДА /reg <ник> <пароль> ---
        if (messageText.startsWith("/reg ") || messageText.startsWith("/register ")) {
            String[] parts = messageText.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используйте: `/reg <ник> <пароль>`\nПример: `/reg pley1657 mypass123`");
                return;
            }
            
            String playerName = parts[1];
            String password = parts[2];
            
            // Проверяем, существует ли игрок
            Player player = Bukkit.getPlayerExact(playerName);
            if (player == null && Bukkit.getPlayerUniqueId(playerName) == null) {
                sendMessage(chatId, "❌ Игрок с ником **" + playerName + "** не найден на сервере!");
                return;
            }
            
            if (playerManager.registerPlayer(playerName, password, String.valueOf(userId))) {
                sendMessage(chatId, "✅ Аккаунт **" + playerName + "** успешно зарегистрирован!\n" +
                        "Теперь вы можете использовать команды:\n" +
                        "• `/kick my account` — кикнуть свой аккаунт\n" +
                        "• `/unreg` — отвязать аккаунт");
            } else {
                sendMessage(chatId, "❌ Игрок **" + playerName + "** уже зарегистрирован!\n" +
                        "Если это вы, просто зайдите на сервер.");
            }
            return;
        }

        // --- КОМАНДА /kick my account ---
        if (messageText.equalsIgnoreCase("/kick my account")) {
            String playerName = playerManager.getPlayerNameByTelegram(String.valueOf(userId));
            if (playerName != null) {
                playerManager.kickAccount(playerName);
                sendMessage(chatId, "✅ Игрок **" + playerName + "** был кикнут с сервера.");
            } else {
                sendMessage(chatId, "❌ Вы не зарегистрированы. Используйте `/reg <ник> <пароль>`");
            }
            return;
        }

        // --- КОМАНДА /unreg ---
        if (messageText.equalsIgnoreCase("/unreg")) {
            String playerName = playerManager.getPlayerNameByTelegram(String.valueOf(userId));
            if (playerName != null) {
                playerManager.unregister(playerName);
                sendMessage(chatId, "✅ Аккаунт **" + playerName + "** отвязан от Telegram.");
            } else {
                sendMessage(chatId, "❌ Вы не зарегистрированы.");
            }
            return;
        }

        // --- ИГРОВЫЕ КОМАНДЫ (доступны всем) ---
        if (messageText.startsWith("!online") || messageText.startsWith("!онлайн")) {
            sendOnline(chatId);
            return;
        }

        if (messageText.startsWith("!tps") || messageText.startsWith("!тпс")) {
            sendTps(chatId);
            return;
        }

        if (messageText.startsWith("!ping") || messageText.startsWith("!пинг")) {
            sendPing(chatId);
            return;
        }

        if (messageText.startsWith("!rules") || messageText.startsWith("!правила")) {
            sendRules(chatId);
            return;
        }

        if (messageText.startsWith("!help") || messageText.startsWith("!помощь")) {
            sendHelp(chatId);
            return;
        }

        if (messageText.startsWith("!info") || messageText.startsWith("!инфо")) {
            sendInfo(chatId);
            return;
        }

        // --- АДМИН-КОМАНДЫ ---
        if (!messageText.startsWith("!rcon")) {
            // Просто игнорируем другие сообщения
            return;
        }

        // Проверка прав для админ-команд
        if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
            sendMessage(chatId, "⛔ У вас нет прав для выполнения этой команды.");
            return;
        }

        String command = messageText.substring(6).trim();
        if (command.isEmpty()) {
            sendMessage(chatId, "ℹ️ Введите команду после !rcon");
            return;
        }

        // --- ОБРАБОТКА !rcon logs ---
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

        // --- ОБРАБОТКА АДМИН-КОМАНД (admin add/remove/list) ---
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
                    sendMessage(chatId, "❌ Укажите текст: !rcon admin add <айди> <текст>");
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

        if (command.equalsIgnoreCase("admin list")) {
            StringBuilder list = new StringBuilder("📋 Список администраторов:\n");
            for (String id : plugin.getAdmins().keySet()) {
                list.append("• ").append(id).append(" → ").append(plugin.getAdmins().get(id)).append("\n");
            }
            sendMessage(chatId, list.toString());
            return;
        }

        // --- ВЫПОЛНЕНИЕ ОБЫЧНОЙ КОМАНДЫ ОТ АДМИНА ---
        String customSender = plugin.getCustomSender(userId);
        if (customSender == null && userId == plugin.getOwnerId()) {
            customSender = "RCON@Grif_Mo";
        }

        final String finalCommand = command;
        final String finalCustomSender = customSender;

        sendMessage(chatId, "✅ Команда выполняется: " + command);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
            sendCustomMessage(finalCommand, finalCustomSender);
        });
    }

    // --- ПРИВЕТСТВИЕ (без упоминания !rcon) ---
    private void sendWelcome(long chatId) {
        String welcome = "🎮 **Добро пожаловать в бот управления сервером!**\n\n" +
                "🔹 **Регистрация:**\n" +
                "`/reg <ник> <пароль>` — привязать аккаунт\n" +
                "Пример: `/reg pley1657 mypass123`\n\n" +
                "🔹 **Управление аккаунтом:**\n" +
                "`/kick my account` — кикнуть свой аккаунт с сервера\n" +
                "`/unreg` — отвязать аккаунт\n\n" +
                "🔹 **Информация:**\n" +
                "`!online` — список игроков онлайн\n" +
                "`!tps` — производительность сервера\n" +
                "`!ping` — пинг игрока\n" +
                "`!rules` — правила сервера\n" +
                "`!info` — информация о сервере\n\n" +
                "🔹 **Помощь:**\n" +
                "`!help` — список всех команд\n\n" +
                "💡 *Для входа на сервер после регистрации просто зайдите в игру!*\n" +
                "🔒 *Бот запросит подтверждение входа для безопасности.*";
        sendMessage(chatId, welcome);
    }

    // --- ИГРОВЫЕ КОМАНДЫ ---
    private void sendOnline(long chatId) {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        StringBuilder players = new StringBuilder();
        for (Player p : Bukkit.getOnlinePlayers()) {
            players.append("• ").append(p.getName()).append("\n");
        }
        String msg = "👥 **Онлайн:** " + online + "/" + max + "\n\n" + players.toString();
        sendMessage(chatId, msg);
    }

    private void sendTps(long chatId) {
        double tps = Bukkit.getTPS()[0];
        String status = tps >= 20 ? "🟢 Отлично" : tps >= 18 ? "🟡 Хорошо" : tps >= 15 ? "🟠 Средне" : "🔴 Плохо";
        String msg = "⚡ **TPS:** " + String.format("%.2f", tps) + "\n📊 **Статус:** " + status;
        sendMessage(chatId, msg);
    }

    private void sendPing(long chatId) {
        Player p = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (p != null) {
            sendMessage(chatId, "📶 **Пинг игрока " + p.getName() + ":** " + p.getPing() + " мс");
        } else {
            sendMessage(chatId, "❌ На сервере нет игроков.");
        }
    }

    private void sendRules(long chatId) {
        String rules = "📜 **Правила сервера:**\n\n" +
                "1. Не читерить\n" +
                "2. Не оскорблять игроков\n" +
                "3. Не гриферить\n" +
                "4. Слушаться администрацию\n\n" +
                "Нарушение правил = наказание!";
        sendMessage(chatId, rules);
    }

    private void sendHelp(long chatId) {
        String help = "🤖 **Доступные команды:**\n\n" +
                "**Регистрация:**\n" +
                "`/reg <ник> <пароль>` — привязать аккаунт\n\n" +
                "**Управление аккаунтом:**\n" +
                "`/kick my account` — кикнуть свой аккаунт\n" +
                "`/unreg` — отвязать аккаунт\n\n" +
                "**Информация:**\n" +
                "`!online` — список игроков онлайн\n" +
                "`!tps` — производительность сервера\n" +
                "`!ping` — пинг игрока\n" +
                "`!rules` — правила сервера\n" +
                "`!info` — информация о сервере";
        sendMessage(chatId, help);
    }

    private void sendInfo(long chatId) {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        String version = Bukkit.getVersion();
        String msg = "🖥️ **Информация о сервере:**\n\n" +
                "📌 Версия: " + version + "\n" +
                "👥 Игроки: " + online + "/" + max;
        sendMessage(chatId, msg);
    }

    // --- КАСТОМНОЕ СООБЩЕНИЕ ОТ БОТА ---
    private void sendCustomMessage(String command, String sender) {
        String[] parts = command.split(" ");
        if (parts.length < 2) return;

        String action = parts[0].toLowerCase();
        String playerName = parts[1];
        String time = "";
        String reason = "";

        if (action.equals("ban") || action.equals("mute") || action.equals("warn") || action.equals("jail")) {
            if (parts.length >= 3) {
                String possibleTime = parts[2];
                if (possibleTime.matches("\\d+[smhdwMy]")) {
                    time = possibleTime;
                    if (parts.length > 3) reason = String.join(" ", Arrays.copyOfRange(parts, 3, parts.length));
                    else reason = "Без причины";
                } else {
                    time = "перманентно";
                    reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
                }
            } else {
                time = "перманентно";
                reason = "Без причины";
            }
        } else if (action.equals("kick")) {
            if (parts.length > 2) reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
            else reason = "Без причины";
        } else {
            return;
        }

        String actionName = "";
        String color = "";
        switch (action) {
            case "ban": actionName = "забанил"; color = "§c";
