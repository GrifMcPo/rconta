package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
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

    public TelegramBotHandler(String token, TelegramConsoleBot plugin, PlayerManager playerManager) {
        this.botToken = token;
        this.plugin = plugin;
        this.playerManager = playerManager;
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
                sendMessage(Long.parseLong(chatId), "✅ Вход для " + playerName + " разрешён.");
            } else if (data.startsWith("auth_deny_")) {
                String playerName = data.substring(10);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player player = Bukkit.getPlayerExact(playerName);
                    if (player != null && player.isOnline()) {
                        player.kickPlayer("§cВход запрещён через Telegram!");
                    }
                });
                sendMessage(Long.parseLong(chatId), "❌ Вход для " + playerName + " запрещён.");
            }
            return;
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String messageText = update.getMessage().getText();
        long userId = update.getMessage().getFrom().getId();
        long chatId = update.getMessage().getChatId();

        plugin.getLogger().info("🔥🔥🔥 ПОЛУЧЕНО СООБЩЕНИЕ ОТ TELEGRAM! 🔥🔥🔥");
        plugin.getLogger().info("📩 Текст: " + messageText);
        plugin.getLogger().info("🆔 От пользователя: " + userId);

        // --- КОМАНДЫ ДЛЯ ВСЕХ ---
        if (messageText.equalsIgnoreCase("/start")) {
            sendMessage(chatId, "🤖 **Бот управления сервером**\n\n" +
                    "Если вы игрок, привяжите аккаунт через /tg в игре.\n" +
                    "Если вы админ, используйте !rcon <команда>");
            return;
        }

        // --- КОМАНДА /register ---
        if (messageText.startsWith("/register ")) {
            String code = messageText.substring(10).trim();
            if (playerManager.registerPlayer(code, String.valueOf(userId))) {
                sendMessage(chatId, "✅ Аккаунт успешно привязан!\n" +
                        "Теперь вы можете использовать /kick my account и /unreg");
            } else {
                sendMessage(chatId, "❌ Неверный код или код уже использован.\n" +
                        "Получите новый код через /tg в игре.");
            }
            return;
        }

        // --- КОМАНДА /kick my account ---
        if (messageText.equalsIgnoreCase("/kick my account")) {
            String playerName = playerManager.getPlayerNameByTelegram(String.valueOf(userId));
            if (playerName != null) {
                playerManager.kickAccount(playerName);
                sendMessage(chatId, "✅ Игрок " + playerName + " был кикнут.");
            } else {
                sendMessage(chatId, "❌ Вы не привязали аккаунт. Используйте /tg в игре.");
            }
            return;
        }

        // --- КОМАНДА /unreg ---
        if (messageText.equalsIgnoreCase("/unreg")) {
            String playerName = playerManager.getPlayerNameByTelegram(String.valueOf(userId));
            if (playerName != null) {
                playerManager.unregister(playerName);
                sendMessage(chatId, "✅ Аккаунт " + playerName + " отвязан.");
            } else {
                sendMessage(chatId, "❌ Вы не привязали аккаунт.");
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

        // --- АДМИН-КОМАНДЫ (требуют прав) ---
        if (!messageText.startsWith("!rcon")) {
            return;
        }

        String command = messageText.substring(6).trim();
        if (command.isEmpty()) {
            sendMessage(chatId, "ℹ️ Введите команду после !rcon");
            return;
        }

        // Проверка прав для админ-команд
        if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
            sendMessage(chatId, "⛔ У вас нет прав для выполнения команд.");
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

        // --- ПОЛУЧАЕМ КАСТОМНЫЙ ТЕКСТ ---
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
            int ping = p.getPing();
            sendMessage(chatId, "📶 **Пинг игрока " + p.getName() + ":** " + ping + " мс");
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
                "**Для всех:**\n" +
                "!online / !онлайн - список игроков\n" +
                "!tps / !тпс - производительность\n" +
                "!ping / !пинг - пинг игрока\n" +
                "!rules / !правила - правила\n" +
                "!info / !инфо - информация о сервере\n" +
                "/register <код> - привязать аккаунт\n" +
                "/kick my account - кикнуть свой аккаунт\n" +
                "/unreg - отвязать аккаунт\n\n" +
                "**Для админов:**\n" +
                "!rcon <команда> - выполнить команду\n" +
                "!rcon admin add <айди> <текст> - добавить админа\n" +
                "!rcon admin remove <айди> - удалить админа\n" +
                "!rcon admin list - список админов";
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
            case "ban": actionName = "забанил"; color = "§c"; break;
            case "mute": actionName = "замутил"; color = "§e"; break;
            case "kick": actionName = "выгнал"; color = "§6"; break;
            case "warn": actionName = "выдал предупреждение"; color = "§5"; break;
            case "jail": actionName = "посадил в тюрьму"; color = "§8"; break;
            default: return;
        }

        String message = color + "✦ " + sender + " §f" + actionName + " игрока §a" + playerName;
        if (!action.equals("kick")) message += " §fна срок §b" + time;
        if (!reason.isEmpty()) message += " §fпо причине: §6" + reason;

        message = "§8§m----------------------------§r\n" + message + "\n§8§m----------------------------§r";
        Bukkit.broadcastMessage(message);
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
