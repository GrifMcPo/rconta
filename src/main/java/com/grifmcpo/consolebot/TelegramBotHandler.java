package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TelegramBotHandler extends TelegramLongPollingBot {

    private final String botToken;
    private final TelegramConsoleBot plugin;
    private final PlayerManager playerManager;
    private final CommandLogger commandLogger;
    private final LogsCommand logsCommand;
    private final CommandExecutor commandExecutor;
    private final PunishmentManager punishmentManager;
    private final RankManager rankManager;
    private final BotBanManager botBanManager;

    private static final String SEPARATOR = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    public TelegramBotHandler(String token, TelegramConsoleBot plugin, PlayerManager playerManager,
                              CommandLogger commandLogger, LogsCommand logsCommand,
                              CommandExecutor commandExecutor, PunishmentManager punishmentManager,
                              RankManager rankManager, BotBanManager botBanManager) {
        this.botToken = token;
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.commandLogger = commandLogger;
        this.logsCommand = logsCommand;
        this.commandExecutor = commandExecutor;
        this.punishmentManager = punishmentManager;
        this.rankManager = rankManager;
        this.botBanManager = botBanManager;
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
        // ============================================
        // ==== КНОПКИ =====
        // ============================================
        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            String chatId = update.getCallbackQuery().getMessage().getChatId().toString();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();

            if (data.startsWith("reply_")) {
                String[] parts = data.split("_");
                String playerName = parts[1];
                long playerId = Long.parseLong(parts[2]);
                plugin.getLogger().info("📩 Ответ на репорт от " + playerName + " (ID: " + playerId + ")");
                sendMessage(Long.parseLong(chatId), "✉️ Введите сообщение для ответа игроку " + playerName + ":");
                deleteMessage(chatId, messageId);
                return;
            }

            if (data.startsWith("confirm_")) {
                String[] parts = data.split("_");
                String action = parts[1];
                String playerName = parts[2];
                String duration = parts.length > 3 ? parts[3] : "навсегда";
                String reason = parts.length > 4 ? parts[4] : "Без причины";
                String issuer = update.getCallbackQuery().getFrom().getId().toString();

                String issuerName = plugin.getCustomSender(Long.parseLong(issuer));
                if (issuerName == null) issuerName = "RCON";

                boolean success = false;
                String result = "";
                switch (action) {
                    case "ban":
                        success = punishmentManager.banPlayer(playerName, issuerName, reason, duration);
                        result = success ? "✅ " + playerName + " забанен на " + duration : "❌ " + playerName + " уже забанен!";
                        break;
                    case "mute":
                        success = punishmentManager.mutePlayer(playerName, issuerName, reason, duration);
                        result = success ? "✅ " + playerName + " замучен на " + duration : "❌ " + playerName + " уже замучен!";
                        break;
                    case "kick":
                        success = punishmentManager.kickPlayer(playerName, issuerName, reason);
                        result = success ? "✅ " + playerName + " кикнут!" : "❌ " + playerName + " не найден!";
                        break;
                    case "unban":
                        success = punishmentManager.unbanPlayer(playerName, issuerName, reason);
                        result = success ? "✅ " + playerName + " разбанен!" : "❌ " + playerName + " не забанен!";
                        break;
                    case "unmute":
                        success = punishmentManager.unmutePlayer(playerName, issuerName, reason);
                        result = success ? "✅ " + playerName + " размучен!" : "❌ " + playerName + " не замучен!";
                        break;
                }

                deleteMessage(chatId, messageId);
                sendResponse(Long.parseLong(chatId), result);
                return;
            }

            if (data.startsWith("cancel_")) {
                deleteMessage(chatId, messageId);
                sendMessage(Long.parseLong(chatId), "❌ Операция отменена.");
                return;
            }

            if (data.startsWith("page_")) {
                String[] parts = data.split("_");
                String type = parts[1];
                String playerName = parts[2];
                int page = Integer.parseInt(parts[3]);
                long chatIdLong = Long.parseLong(chatId);

                handlePagination(chatIdLong, type, playerName, page, messageId);
                return;
            }

            return;
        }

        // ============================================
        // ==== СООБЩЕНИЯ =====
        // ============================================
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String messageText = update.getMessage().getText();
        long userId = update.getMessage().getFrom().getId();
        long chatId = update.getMessage().getChatId();

        plugin.getLogger().info("📩 Получено: " + messageText + " от " + userId);

        // Сохраняем пользователя
        rankManager.addUser(userId);

        // ============================================
        // ==== ПРОВЕРКА БАНА В БОТЕ =====
        // ============================================
        if (botBanManager.isBanned(userId)) {
            if (userId != plugin.getOwnerId() && !plugin.isAdmin(userId)) {
                sendMessage(chatId, botBanManager.getBanMessage(userId));
                return;
            }
        }

        if (rankManager.isTechWork() && !plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
            sendMessage(chatId, "🔧 На сервере ведутся технические работы. Попробуйте позже.");
            return;
        }

        if (messageText.equalsIgnoreCase("/start")) {
            sendWelcome(chatId);
            return;
        }

        if (messageText.equalsIgnoreCase("!me")) {
            sendMe(chatId, userId);
            return;
        }

        if (messageText.startsWith("!online") || messageText.startsWith("!онлайн")) {
            sendOnline(chatId);
            return;
        }

        if (messageText.startsWith("!tps") || messageText.startsWith("!тпс")) {
            sendTps(chatId);
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

        if (!messageText.startsWith("!rcon")) {
            return;
        }

        String command = messageText.substring(6).trim();
        if (command.isEmpty()) {
            sendMessage(chatId, "ℹ️ Введи команду после !rcon");
            return;
        }

        // ============================================
        // ==== ПРОВЕРКА ПРАВ =====
        // ============================================
        if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
            String rankName = rankManager.getUserRank(userId);
            if (rankName == null) {
                sendMessage(chatId, "⛔ У вас нет доступа к этой команде!");
                return;
            }
            String fullCommand = "!rcon " + command.split(" ")[0];
            if (!rankManager.hasPermission(userId, fullCommand)) {
                sendMessage(chatId, "⛔ У вашего ранга \"" + rankName + "\" нет доступа к команде " + fullCommand);
                return;
            }
        }

        // --- Обработка команд ---
        handleRconCommand(chatId, command, userId);
    }

    // ============================================
    // ==== ПРОВЕРКА ПРАВ ПЕРЕД КАЖДОЙ КОМАНДОЙ =====
    // ============================================
    private boolean checkRankPermission(long userId, String command, long chatId) {
        if (plugin.isAdmin(userId) || userId == plugin.getOwnerId()) {
            return true;
        }
        String rankName = rankManager.getUserRank(userId);
        if (rankName == null) {
            sendMessage(chatId, "⛔ У вас нет доступа к этой команде!");
            return false;
        }
        String fullCommand = "!rcon " + command.split(" ")[0];
        if (!rankManager.hasPermission(userId, fullCommand)) {
            sendMessage(chatId, "⛔ У вашего ранга \"" + rankName + "\" нет доступа к команде " + fullCommand);
            return false;
        }
        return true;
    }

    // ============================================
    // ==== ОБРАБОТКА RCON КОМАНД =====
    // ============================================
    private void handleRconCommand(long chatId, String command, long userId) {

        // ============================================
        // ==== !rcon messageall (С ПРЕФИКСОМ РАНГА) =====
        // ============================================
        if (command.startsWith("messageall ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только админы могут рассылать сообщения.");
                return;
            }
            
            String message = command.substring(11);
            if (message.trim().isEmpty()) {
                sendMessage(chatId, "❌ Введите текст сообщения!");
                return;
            }
            
            // Получаем ранг отправителя
            String rankDisplay = rankManager.getFullRankDisplay(userId);
            String senderName = plugin.getCustomSender(userId);
            if (senderName == null && userId == plugin.getOwnerId()) {
                senderName = "Владелец";
            }
            
            // Формируем красивое сообщение с рангом
            String formattedMessage = "";
            if (!rankDisplay.isEmpty()) {
                formattedMessage += rankDisplay + "\n";
            }
            formattedMessage += "📢 " + message;
            
            // Отправляем всем
            List<Long> allUsers = rankManager.getAllUsers();
            int count = 0;
            for (long id : allUsers) {
                try {
                    sendMessage(id, formattedMessage);
                    count++;
                } catch (Exception e) {
                    // Игнорируем
                }
            }
            
            // Подтверждение для админа
            sendResponse(chatId, "✅ Сообщение отправлено " + count + " пользователям!\n" +
                    "👤 Отправитель: " + rankDisplay + " " + senderName);
            return;
        }

        // ============================================
        // ==== !rcon botban =====
        // ============================================
        if (command.startsWith("botban ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только админы могут банить в боте.");
                return;
            }

            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon botban <айди> [время] <причина>\n" +
                        "Пример: !rcon botban 123456789 7d спам");
                return;
            }

            long targetId;
            try {
                targetId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Неверный ID!");
                return;
            }

            if (targetId == plugin.getOwnerId()) {
                sendMessage(chatId, "❌ Нельзя забанить владельца бота!");
                return;
            }

            if (plugin.isAdmin(targetId)) {
                sendMessage(chatId, "❌ Нельзя забанить админа бота!");
                return;
            }

            String duration = "навсегда";
            String reason = "";
            int start = 2;

            if (parts.length > 2 && (parts[2].matches("\\d+[smhdwMy]") || parts[2].equals("навсегда"))) {
                duration = parts[2];
                start = 3;
            }

            if (parts.length > start) {
                reason = String.join(" ", Arrays.copyOfRange(parts, start, parts.length));
            } else {
                reason = "Без причины";
            }

            String issuer = plugin.getCustomSender(userId);
            if (issuer == null) issuer = "RCON";

            if (botBanManager.banUser(targetId, reason, duration, issuer)) {
                sendResponse(chatId, "✅ Пользователь " + targetId + " забанен в боте!\n" +
                        "📝 Причина: " + reason + "\n" +
                        "⏱ Срок: " + duration);
            } else {
                sendMessage(chatId, "❌ Пользователь " + targetId + " уже забанен!");
            }
            return;
        }

        // ============================================
        // ==== !rcon botunban =====
        // ============================================
        if (command.startsWith("botunban ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только админы могут разбанивать в боте.");
                return;
            }

            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon botunban <айди> <причина>\n" +
                        "Пример: !rcon botunban 123456789 ошибка");
                return;
            }

            long targetId;
            try {
                targetId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Неверный ID!");
                return;
            }

            String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));

            String issuer = plugin.getCustomSender(userId);
            if (issuer == null) issuer = "RCON";

            if (botBanManager.unbanUser(targetId, reason, issuer)) {
                sendResponse(chatId, "✅ Пользователь " + targetId + " разбанен в боте!\n" +
                        "📝 Причина снятия: " + reason);
            } else {
                sendMessage(chatId, "❌ Пользователь " + targetId + " не забанен!");
            }
            return;
        }

        // ============================================
        // ==== !rcon botbanlist =====
        // ============================================
        if (command.equalsIgnoreCase("botbanlist") || command.startsWith("botbanlist ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Доступ запрещён.");
                return;
            }

            int page = 1;
            String[] parts = command.split(" ");
            if (parts.length > 1) {
                try { page = Integer.parseInt(parts[1]); } catch (NumberFormatException e) {}
            }

            List<BotBanManager.BanData> allBans = botBanManager.getAllBans();
            int pageSize = 10;
            int totalPages = (int) Math.ceil((double) allBans.size() / pageSize);
            if (page < 1) page = 1;
            if (page > totalPages && totalPages > 0) page = totalPages;

            if (allBans.isEmpty()) {
                sendResponse(chatId, "📋 Список банов в боте пуст.");
                return;
            }

            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, allBans.size());

            StringBuilder response = new StringBuilder();
            response.append("📋 БАНЫ В БОТЕ (Страница ").append(page).append("/").append(totalPages).append(")\n\n");

            for (int i = start; i < end; i++) {
                BotBanManager.BanData ban = allBans.get(i);
                response.append("🆔 ").append(ban.userId).append("\n");
                response.append("📝 ").append(ban.reason).append("\n");
                response.append("⏱ ").append(ban.duration).append(" (").append(ban.getStatus()).append(")\n");
                response.append("👤 ").append(ban.issuer).append("\n");
                response.append(SEPARATOR).append("\n");
            }

            if (totalPages > 1) {
                response.append("\n📌 !rcon botbanlist ").append(page + 1).append(" — следующая страница");
            }

            sendResponse(chatId, response.toString());
            return;
        }

        // ============================================
        // ==== !rcon botbaninfo =====
        // ============================================
        if (command.startsWith("botbaninfo ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Доступ запрещён.");
                return;
            }

            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon botbaninfo <айди>");
                return;
            }

            long targetId;
            try {
                targetId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Неверный ID!");
                return;
            }

            BotBanManager.BanData ban = botBanManager.getBanData(targetId);
            if (ban == null) {
                sendMessage(chatId, "❌ Пользователь " + targetId + " не забанен!");
                return;
            }

            sendResponse(chatId, "📋 ИНФОРМАЦИЯ О БАНЕ В БОТЕ\n\n" + ban.toString());
            return;
        }

        // ============================================
        // ==== !rcon botbancheck (публичная) =====
        // ============================================
        if (command.startsWith("botbancheck ")) {
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon botbancheck <айди>");
                return;
            }

            long targetId;
            try {
                targetId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Неверный ID!");
                return;
            }

            if (botBanManager.isBanned(targetId)) {
                sendResponse(chatId, "⛔ Пользователь " + targetId + " ЗАБАНЕН в боте!");
            } else {
                sendResponse(chatId, "✅ Пользователь " + targetId + " НЕ забанен в боте.");
            }
            return;
        }

        // ============================================
        // ==== !rcon logs =====
        // ============================================
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

        // ============================================
        // ==== !rcon shist / !rcon hist =====
        // ============================================
        if (command.startsWith("shist ") || command.startsWith("hist ")) {
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon shist <ник>");
                return;
            }
            String target = parts[1];

            List<PunishmentManager.HistoryEntry> history = punishmentManager.getHistory(target);

            if (history.isEmpty()) {
                sendResponse(chatId, "📋 История наказаний для " + target + " пуста.");
                return;
            }

            StringBuilder response = new StringBuilder();
            response.append("📋 История наказаний игрока ").append(target).append(" (Записей: ").append(history.size()).append(")");

            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");

            int count = 0;
            for (PunishmentManager.HistoryEntry entry : history) {
                if (count >= 20) {
                    response.append("\n\n... и ещё ").append(history.size() - 20).append(" записей");
                    break;
                }
                String timeAgo = punishmentManager.getTimeAgo(entry.timestamp);
                String status = entry.type.equals("ban") ?
                    (punishmentManager.isBanned(target) ? "[Активен]" : "[Истек]") :
                    (punishmentManager.isMuted(target) ? "[Активен]" : "[Истек]");
                String actionName = entry.getActionName();

                response.append("\n\n - ").append(timeAgo).append(" -");
                response.append("\n   ").append(target).append(" был ").append(actionName)
                        .append(" на ").append(entry.duration).append(" ")
                        .append(entry.issuer).append(": ").append(entry.reason).append(" ").append(status);
                count++;
            }

            sendResponse(chatId, response.toString());
            return;
        }

        // ============================================
        // ==== !rcon bc / !rcon bcast =====
        // ============================================
        if (command.startsWith("bc ") || command.startsWith("bcast ")) {
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon bc <сообщение>");
                return;
            }
            String message = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));

            String sender = plugin.getCustomSender(userId);
            if (sender == null && userId == plugin.getOwnerId()) {
                sender = "RCON@Grif_Mo";
            }

            String format = "§6[Объявление] §f" + message + " §7(Пишет: " + sender + "§7)";
            Bukkit.broadcastMessage(format);

            sendResponse(chatId, "📢 " + format.replaceAll("§[0-9a-fk-or]", ""));
            return;
        }

        // ============================================
        // ==== !rcon ban =====
        // ============================================
        if (command.startsWith("ban ")) {
            if (!checkRankPermission(userId, command, chatId)) return;
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon ban <ник> [время] <причина>");
                return;
            }
            String target = parts[1];
            String duration = "навсегда";
            String reason = "";
            int start = 2;
            if (parts.length > 2 && punishmentManager.isValidTime(parts[2])) {
                duration = parts[2];
                start = 3;
            }
            if (parts.length > start) {
                reason = String.join(" ", Arrays.copyOfRange(parts, start, parts.length));
            } else {
                reason = "Без причины";
            }

            String issuer = plugin.getCustomSender(userId);
            if (issuer == null && userId == plugin.getOwnerId()) {
                issuer = "RCON@Grif_Mo";
            }

            sendMessage(chatId, "[БОТ] ⏳ Выполняю команду...");

            if (punishmentManager.banPlayer(target, issuer, reason, duration)) {
                String response = "✅ Игрок " + target + " забанен на " + duration + "\n" +
                        "👤 Выдал: " + issuer + "\n" +
                        "📝 Причина: " + reason;
                sendResponse(chatId, response);
            } else {
                sendMessage(chatId, "❌ Игрок " + target + " уже забанен!");
            }
            return;
        }

        // ============================================
        // ==== !rcon mute =====
        // ============================================
        if (command.startsWith("mute ")) {
            if (!checkRankPermission(userId, command, chatId)) return;
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon mute <ник> [время] <причина>");
                return;
            }
            String target = parts[1];
            String duration = "навсегда";
            String reason = "";
            int start = 2;
            if (parts.length > 2 && punishmentManager.isValidTime(parts[2])) {
                duration = parts[2];
                start = 3;
            }
            if (parts.length > start) {
                reason = String.join(" ", Arrays.copyOfRange(parts, start, parts.length));
            } else {
                reason = "Без причины";
            }

            String issuer = plugin.getCustomSender(userId);
            if (issuer == null && userId == plugin.getOwnerId()) {
                issuer = "RCON@Grif_Mo";
            }

            sendMessage(chatId, "[БОТ] ⏳ Выполняю команду...");

            if (punishmentManager.mutePlayer(target, issuer, reason, duration)) {
                String response = "✅ Игрок " + target + " замучен на " + duration + "\n" +
                        "👤 Выдал: " + issuer + "\n" +
                        "📝 Причина: " + reason;
                sendResponse(chatId, response);
            } else {
                sendMessage(chatId, "❌ Игрок " + target + " уже замучен!");
            }
            return;
        }

        // ============================================
        // ==== !rcon kick =====
        // ============================================
        if (command.startsWith("kick ")) {
            if (!checkRankPermission(userId, command, chatId)) return;
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon kick <ник> <причина>");
                return;
            }
            String target = parts[1];
            String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));

            String issuer = plugin.getCustomSender(userId);
            if (issuer == null && userId == plugin.getOwnerId()) {
                issuer = "RCON@Grif_Mo";
            }

            sendMessage(chatId, "[БОТ] ⏳ Выполняю команду...");

            if (punishmentManager.kickPlayer(target, issuer, reason)) {
                String response = "✅ Игрок " + target + " кикнут!\n" +
                        "👤 Выдал: " + issuer + "\n" +
                        "📝 Причина: " + reason;
                sendResponse(chatId, response);
            } else {
                sendMessage(chatId, "❌ Игрок " + target + " не найден!");
            }
            return;
        }

        // ============================================
        // ==== !rcon unban =====
        // ============================================
        if (command.startsWith("unban ")) {
            if (!checkRankPermission(userId, command, chatId)) return;
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon unban <ник> <причина>");
                return;
            }
            String target = parts[1];
            String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));

            String issuer = plugin.getCustomSender(userId);
            if (issuer == null && userId == plugin.getOwnerId()) {
                issuer = "RCON@Grif_Mo";
            }

            sendMessage(chatId, "[БОТ] ⏳ Выполняю команду...");

            if (punishmentManager.unbanPlayer(target, issuer, reason)) {
                String response = "✅ Игрок " + target + " разбанен!\n" +
                        "👤 Выдал: " + issuer + "\n" +
                        "📝 Причина: " + reason;
                sendResponse(chatId, response);
            } else {
                sendMessage(chatId, "❌ Игрок " + target + " не забанен!");
            }
            return;
        }

        // ============================================
        // ==== !rcon unmute =====
        // ============================================
        if (command.startsWith("unmute ")) {
            if (!checkRankPermission(userId, command, chatId)) return;
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon unmute <ник> <причина>");
                return;
            }
            String target = parts[1];
            String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));

            String issuer = plugin.getCustomSender(userId);
            if (issuer == null && userId == plugin.getOwnerId()) {
                issuer = "RCON@Grif_Mo";
            }

            sendMessage(chatId, "[БОТ] ⏳ Выполняю команду...");

            if (punishmentManager.unmutePlayer(target, issuer, reason)) {
                String response = "✅ Игрок " + target + " размучен!\n" +
                        "👤 Выдал: " + issuer + "\n" +
                        "📝 Причина: " + reason;
                sendResponse(chatId, response);
            } else {
                sendMessage(chatId, "❌ Игрок " + target + " не замучен!");
            }
            return;
        }

        // ============================================
        // ==== !rcon banlist =====
        // ============================================
        if (command.equalsIgnoreCase("banlist") || command.startsWith("banlist ")) {
            int page = 1;
            String[] parts = command.split(" ");
            if (parts.length > 1) {
                try { page = Integer.parseInt(parts[1]); } catch (NumberFormatException e) {}
            }
            int pageSize = 10;
            List<String> allBans = punishmentManager.getBanList(1, Integer.MAX_VALUE);
            List<String> bans = punishmentManager.getBanList(page, pageSize);
            int totalPages = (int) Math.ceil((double) allBans.size() / pageSize);

            if (bans.isEmpty()) {
                sendResponse(chatId, "📋 Список банов пуст.");
            } else {
                StringBuilder response = new StringBuilder();
                response.append("📋 Список банов (Страница ").append(page).append("/").append(totalPages).append(")");
                for (String ban : bans) {
                    response.append("\n\n").append(ban);
                }
                if (totalPages > 1) {
                    response.append("\n\n📌 Используй: !rcon banlist ").append(page + 1);
                }
                sendResponse(chatId, response.toString());
            }
            return;
        }

        // ============================================
        // ==== !rcon mutelist =====
        // ============================================
        if (command.equalsIgnoreCase("mutelist") || command.startsWith("mutelist ")) {
            int page = 1;
            String[] parts = command.split(" ");
            if (parts.length > 1) {
                try { page = Integer.parseInt(parts[1]); } catch (NumberFormatException e) {}
            }
            int pageSize = 10;
            List<String> allMutes = punishmentManager.getMuteList(1, Integer.MAX_VALUE);
            List<String> mutes = punishmentManager.getMuteList(page, pageSize);
            int totalPages = (int) Math.ceil((double) allMutes.size() / pageSize);

            if (mutes.isEmpty()) {
                sendResponse(chatId, "📋 Список мутов пуст.");
            } else {
                StringBuilder response = new StringBuilder();
                response.append("📋 Список мутов (Страница ").append(page).append("/").append(totalPages).append(")");
                for (String mute : mutes) {
                    response.append("\n\n").append(mute);
                }
                if (totalPages > 1) {
                    response.append("\n\n📌 Используй: !rcon mutelist ").append(page + 1);
                }
                sendResponse(chatId, response.toString());
            }
            return;
        }

        // ============================================
        // ==== !rcon tex =====
        // ============================================
        if (command.startsWith("tex ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может включать техработы.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon tex on/off");
                return;
            }
            boolean state = parts[1].equalsIgnoreCase("on");
            plugin.getConfig().set("maintenance", state);
            plugin.saveConfig();
            sendResponse(chatId, state ? "🔧 Техработы ВКЛЮЧЕНЫ" : "✅ Техработы ВЫКЛЮЧЕНЫ");
            return;
        }

        // ============================================
        // ==== НОВАЯ СИСТЕМА РАНГОВ =====
        // ============================================

        // --- !rcon rank create ---
        if (command.startsWith("rank create ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может создавать ранги.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon rank create <название> [префикс] [эмодзи] [цвет]\n" +
                        "Пример: !rcon rank create admin [Админ] 🛡️ 🔵");
                return;
            }
            String name = parts[1];
            String prefix = parts.length > 2 ? parts[2] : "";
            String emoji = parts.length > 3 ? parts[3] : "";
            String color = parts.length > 4 ? parts[4] : "";
            
            if (rankManager.createRank(name, prefix, emoji, color)) {
                sendResponse(chatId, "✅ Ранг \"" + name + "\" создан!\n" +
                        "📌 Префикс: " + prefix + "\n" +
                        "🎨 Эмодзи: " + emoji + "\n" +
                        "🎨 Цвет: " + color);
            } else {
                sendMessage(chatId, "❌ Ранг \"" + name + "\" уже существует.");
            }
            return;
        }

        // --- !rcon rank delete ---
        if (command.startsWith("rank delete ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может удалять ранги.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon rank delete <название>");
                return;
            }
            String name = parts[1];
            if (rankManager.deleteRank(name)) {
                sendResponse(chatId, "✅ Ранг \"" + name + "\" удалён!");
            } else {
                sendMessage(chatId, "❌ Ранг \"" + name + "\" не найден.");
            }
            return;
        }

        // --- !rcon rank rename ---
        if (command.startsWith("rank rename ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может переименовывать ранги.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon rank rename <старое> <новое>");
                return;
            }
            String oldName = parts[1];
            String newName = parts[2];
            if (rankManager.renameRank(oldName, newName)) {
                sendResponse(chatId, "✅ Ранг \"" + oldName + "\" переименован в \"" + newName + "\"!");
            } else {
                sendMessage(chatId, "❌ Ранг \"" + oldName + "\" не найден или \"" + newName + "\" уже существует.");
            }
            return;
        }

        // --- !rcon rank clone ---
        if (command.startsWith("rank clone ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может клонировать ранги.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon rank clone <исходный> <новый>");
                return;
            }
            String source = parts[1];
            String target = parts[2];
            if (rankManager.cloneRank(source, target)) {
                sendResponse(chatId, "✅ Ранг \"" + source + "\" склонирован в \"" + target + "\"!");
            } else {
                sendMessage(chatId, "❌ Ранг \"" + source + "\" не найден или \"" + target + "\" уже существует.");
            }
            return;
        }

        // --- !rcon rank list ---
        if (command.startsWith("rank list")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Доступ запрещён.");
                return;
            }
            List<String> rankNames = rankManager.getSortedRankNames();
            if (rankNames.isEmpty()) {
                sendResponse(chatId, "📋 Рангов нет.");
            } else {
                StringBuilder response = new StringBuilder();
                response.append("📋 СПИСОК РАНГОВ (").append(rankNames.size()).append("):\n\n");
                int i = 1;
                for (String name : rankNames) {
                    RankManager.Rank rank = rankManager.getRank(name);
                    response.append(i++).append(". ");
                    if (!rank.getEmoji().isEmpty()) response.append(rank.getEmoji()).append(" ");
                    if (!rank.getPrefix().isEmpty()) response.append(rank.getPrefix()).append(" ");
                    if (!rank.getColor().isEmpty()) response.append(rank.getColor());
                    response.append(" (👤 ").append(rank.getUsers().size()).append(")\n");
                }
                sendResponse(chatId, response.toString());
            }
            return;
        }

        // --- !rcon rank info ---
        if (command.startsWith("rank info ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Доступ запрещён.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon rank info <название>");
                return;
            }
            String name = parts[1];
            RankManager.Rank rank = rankManager.getRank(name);
            if (rank == null) {
                sendMessage(chatId, "❌ Ранг \"" + name + "\" не найден.");
                return;
            }
            
            StringBuilder response = new StringBuilder();
            response.append("📋 ИНФОРМАЦИЯ О РАНГЕ \"").append(name).append("\"\n");
            response.append(SEPARATOR).append("\n");
            response.append("📌 Префикс: ").append(rank.getPrefix()).append("\n");
            response.append("🎨 Эмодзи: ").append(rank.getEmoji()).append("\n");
            response.append("🎨 Цвет: ").append(rank.getColor()).append("\n");
            response.append("🔰 Приоритет: ").append(rank.getPriority()).append("\n");
            response.append("👤 Пользователей: ").append(rank.getUsers().size()).append("\n");
            response.append("📌 Прав: ").append(rank.getPermissions().size()).append("\n");
            if (!rank.getInherits().isEmpty()) {
                response.append("🧬 Наследует: ").append(String.join(", ", rank.getInherits())).append("\n");
            }
            sendResponse(chatId, response.toString());
            return;
        }

        // --- !rcon rank addperm ---
        if (command.startsWith("rank addperm ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может выдавать права.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon rank addperm <название> <команда> [лимит]\n" +
                        "Пример: !rcon rank addperm admin !rcon ban 7d");
                return;
            }
            String name = parts[1];
            String cmd = parts[2];
            String limit = parts.length > 3 ? parts[3] : "навсегда";
            
            if (rankManager.addPermission(name, cmd, limit)) {
                sendResponse(chatId, "✅ Рангу \"" + name + "\" выдана команда \"" + cmd + "\" (лимит: " + limit + ")");
            } else {
                sendMessage(chatId, "❌ Ранг \"" + name + "\" не найден.");
            }
            return;
        }

        // --- !rcon rank removeperm ---
        if (command.startsWith("rank removeperm ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может удалять права.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon rank removeperm <название> <команда>");
                return;
            }
            String name = parts[1];
            String cmd = parts[2];
            
            if (rankManager.removePermission(name, cmd)) {
                sendResponse(chatId, "✅ У ранга \"" + name + "\" удалена команда \"" + cmd + "\"");
            } else {
                sendMessage(chatId, "❌ Ранг \"" + name + "\" не найден.");
            }
            return;
        }

        // --- !rcon rank perms ---
        if (command.startsWith("rank perms ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Доступ запрещён.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon rank perms <название>");
                return;
            }
            String name = parts[1];
            RankManager.Rank rank = rankManager.getRank(name);
            if (rank == null) {
                sendMessage(chatId, "❌ Ранг \"" + name + "\" не найден.");
                return;
            }
            
            Map<String, String> perms = rank.getPermissions();
            if (perms.isEmpty()) {
                sendResponse(chatId, "📋 У ранга \"" + name + "\" нет прав.");
            } else {
                StringBuilder response = new StringBuilder();
                response.append("📋 ПРАВА РАНГА \"").append(name).append("\"\n");
                response.append(SEPARATOR).append("\n");
                int i = 1;
                for (Map.Entry<String, String> entry : perms.entrySet()) {
                    response.append(i++).append(". ").append(entry.getKey());
                    if (!entry.getValue().equals("навсегда")) {
                        response.append(" (лимит: ").append(entry.getValue()).append(")");
                    }
                    response.append("\n");
                }
                sendResponse(chatId, response.toString());
            }
            return;
        }

        // --- !rcon rank checkperm ---
        if (command.startsWith("rank checkperm ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Доступ запрещён.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon rank checkperm <название> <команда>");
                return;
            }
            String name = parts[1];
            String cmd = parts[2];
            
            if (rankManager.hasPermissionToCommand(name, cmd)) {
                String limit = rankManager.getRankCommandLimit(name, cmd);
                String limitStr = limit != null ? " (лимит: " + limit + ")" : "";
                sendResponse(chatId, "✅ Ранг \"" + name + "\" имеет право на \"" + cmd + "\"" + limitStr);
            } else {
                sendMessage(chatId, "❌ Ранг \"" + name + "\" НЕ имеет права на \"" + cmd + "\"");
            }
            return;
        }

        // --- !rcon rank adduser ---
        if (command.startsWith("rank adduser ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может добавлять в ранги.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon rank adduser <название> <айди> [причина]");
                return;
            }
            String name = parts[1];
            long targetId;
            try {
                targetId = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Неверный ID!");
                return;
            }
            String reason = parts.length > 3 ? String.join(" ", Arrays.copyOfRange(parts, 3, parts.length)) : null;
            
            if (rankManager.addUserToRank(name, targetId, reason)) {
                sendResponse(chatId, "✅ Пользователь " + targetId + " добавлен в ранг \"" + name + "\"");
            } else {
                sendMessage(chatId, "❌ Ранг \"" + name + "\" не найден.");
            }
            return;
        }

        // --- !rcon rank removeuser ---
        if (command.startsWith("rank removeuser ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может удалять из рангов.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon rank removeuser <айди> [причина]");
                return;
            }
            long targetId;
            try {
                targetId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Неверный ID!");
                return;
            }
            String reason = parts.length > 2 ? String.join(" ", Arrays.copyOfRange(parts, 2, parts.length)) : null;
            
            if (rankManager.removeUserFromRank(targetId, reason)) {
                sendResponse(chatId, "✅ Пользователь " + targetId + " удалён из ранга");
            } else {
                sendMessage(chatId, "❌ Пользователь " + targetId + " не найден в рангах.");
            }
            return;
        }

        // --- !rcon rank users ---
        if (command.startsWith("rank users ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Доступ запрещён.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon rank users <название>");
                return;
            }
            String name = parts[1];
            RankManager.Rank rank = rankManager.getRank(name);
            if (rank == null) {
                sendMessage(chatId, "❌ Ранг \"" + name + "\" не найден.");
                return;
            }
            
            List<Long> users = rank.getUsers();
            if (users.isEmpty()) {
                sendResponse(chatId, "👤 В ранге \"" + name + "\" нет пользователей.");
            } else {
                StringBuilder response = new StringBuilder();
                response.append("👤 ПОЛЬЗОВАТЕЛИ РАНГА \"").append(name).append("\"\n");
                response.append(SEPARATOR).append("\n");
                int i = 1;
                for (long id : users) {
                    response.append(i++).append(". ").append(id);
                    if (id == plugin.getOwnerId()) {
                        response.append(" 👑 Владелец");
                    }
                    response.append("\n");
                }
                response.append(SEPARATOR).append("\n");
                response.append("Всего: ").append(users.size()).append(" пользователей");
                sendResponse(chatId, response.toString());
            }
            return;
        }

        // --- !rcon rank promote ---
        if (command.startsWith("rank promote ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может повышать.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon rank promote <айди> [причина]");
                return;
            }
            long targetId;
            try {
                targetId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Неверный ID!");
                return;
            }
            String reason = parts.length > 2 ? String.join(" ", Arrays.copyOfRange(parts, 2, parts.length)) : null;
            
            if (rankManager.promoteUser(targetId, reason)) {
                sendResponse(chatId, "✅ Пользователь " + targetId + " повышен!");
            } else {
                sendMessage(chatId, "❌ Не удалось повысить пользователя.");
            }
            return;
        }

        // --- !rcon rank demote ---
        if (command.startsWith("rank demote ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может понижать.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon rank demote <айди> [причина]");
                return;
            }
            long targetId;
            try {
                targetId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Неверный ID!");
                return;
            }
            String reason = parts.length > 2 ? String.join(" ", Arrays.copyOfRange(parts, 2, parts.length)) : null;
            
            if (rankManager.demoteUser(targetId, reason)) {
                sendResponse(chatId, "✅ Пользователь " + targetId + " понижен!");
            } else {
                sendMessage(chatId, "❌ Не удалось понизить пользователя.");
            }
            return;
        }

        // --- !rcon rank setprefix ---
        if (command.startsWith("rank setprefix ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может менять префикс.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon rank setprefix <название> <префикс>");
                return;
            }
            String name = parts[1];
            String prefix = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
            
            if (rankManager.setRankPrefix(name, prefix)) {
                sendResponse(chatId, "✅ Рангу \"" + name + "\" установлен префикс: " + prefix);
            } else {
                sendMessage(chatId, "❌ Ранг \"" + name + "\" не найден.");
            }
            return;
        }

        // --- !rcon rank setemoji ---
        if (command.startsWith("rank setemoji ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может менять эмодзи.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon rank setemoji <название> <эмодзи>");
                return;
            }
            String name = parts[1];
            String emoji = parts[2];
            
            if (rankManager.setRankEmoji(name, emoji)) {
                sendResponse(chatId, "✅ Рангу \"" + name + "\" установлен эмодзи: " + emoji);
            } else {
                sendMessage(chatId, "❌ Ранг \"" + name + "\" не найден.");
            }
            return;
        }

        // --- !rcon rank setcolor ---
        if (command.startsWith("rank setcolor ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может менять цвет.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon rank setcolor <название> <цвет>");
                return;
            }
            String name = parts[1];
            String color = parts[2];
            
            if (rankManager.setRankColor(name, color)) {
                sendResponse(chatId, "✅ Рангу \"" + name + "\" установлен цвет: " + color);
            } else {
                sendMessage(chatId, "❌ Ранг \"" + name + "\" не найден.");
            }
            return;
        }

        // --- !rcon rank setpriority ---
        if (command.startsWith("rank setpriority ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может менять приоритет.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon rank setpriority <название> <приоритет>");
                return;
            }
            String name = parts[1];
            int priority;
            try {
                priority = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Приоритет должен быть числом!");
                return;
            }
            
            if (rankManager.setRankPriority(name, priority)) {
                sendResponse(chatId, "✅ Рангу \"" + name + "\" установлен приоритет: " + priority);
            } else {
                sendMessage(chatId, "❌ Ранг \"" + name + "\" не найден.");
            }
            return;
        }

        // --- !rcon rank myrank ---
        if (command.equalsIgnoreCase("rank myrank")) {
            String rankName = rankManager.getUserRank(userId);
            if (rankName == null) {
                sendResponse(chatId, "📋 У вас нет ранга.");
            } else {
                RankManager.Rank rank = rankManager.getRank(rankName);
                if (rank == null) {
                    sendResponse(chatId, "📋 У вас нет ранга.");
                    return;
                }
                String display = rankManager.getFullRankDisplay(userId);
                sendResponse(chatId, "📋 ВАШ РАНГ\n" +
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "🔰 " + display + "\n" +
                        "📌 Название: " + rankName + "\n" +
                        "👤 Пользователей в ранге: " + rank.getUsers().size() + "\n" +
                        "📌 Прав: " + rank.getPermissions().size());
            }
            return;
        }

        // --- !rcon rank hierarchy ---
        if (command.equalsIgnoreCase("rank hierarchy")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Доступ запрещён.");
                return;
            }
            List<String> rankNames = rankManager.getSortedRankNames();
            if (rankNames.isEmpty()) {
                sendResponse(chatId, "📋 Рангов нет.");
            } else {
                StringBuilder response = new StringBuilder();
                response.append("📊 ИЕРАРХИЯ РАНГОВ\n");
                response.append(SEPARATOR).append("\n");
                int i = 1;
                for (String name : rankNames) {
                    RankManager.Rank rank = rankManager.getRank(name);
                    String display = rank.getEmoji() + " " + rank.getPrefix();
                    response.append(i++).append(". ").append(display).append(" (👤 ").append(rank.getUsers().size()).append(")\n");
                }
                sendResponse(chatId, response.toString());
            }
            return;
        }

        // --- !rcon rank stats ---
        if (command.equalsIgnoreCase("rank stats")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Доступ запрещён.");
                return;
            }
            sendResponse(chatId, "📊 СТАТИСТИКА РАНГОВ\n" +
                    "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                    "📌 Всего рангов: " + rankManager.getTotalRanks() + "\n" +
                    "👤 Всего пользователей: " + rankManager.getTotalUsers() + "\n" +
                    "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                    rankManager.getRankStatsFormatted());
            return;
        }

        // --- !rcon rank clear ---
        if (command.startsWith("rank clear ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может очищать ранги.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon rank clear <название>");
                return;
            }
            String name = parts[1];
            if (rankManager.clearRank(name)) {
                sendResponse(chatId, "✅ Ранг \"" + name + "\" очищен!");
            } else {
                sendMessage(chatId, "❌ Ранг \"" + name + "\" не найден.");
            }
            return;
        }

        // --- !rcon rank reset ---
        if (command.startsWith("rank reset ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может сбрасывать права.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon rank reset <название>");
                return;
            }
            String name = parts[1];
            if (rankManager.resetRank(name)) {
                sendResponse(chatId, "✅ Права ранга \"" + name + "\" сброшены!");
            } else {
                sendMessage(chatId, "❌ Ранг \"" + name + "\" не найден.");
            }
            return;
        }

        // ============================================
        // ==== ОПАСНЫЕ КОМАНДЫ (С ПРОВЕРКОЙ ПРАВ) =====
        // ============================================
        String[] dangerous = {"ban ", "mute ", "kick ", "unban ", "unmute "};
        for (String d : dangerous) {
            if (command.startsWith(d)) {
                if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                    String rankName = rankManager.getUserRank(userId);
                    if (rankName == null) {
                        sendMessage(chatId, "⛔ У вас нет доступа к этой команде!");
                        return;
                    }
                    String fullCmd = "!rcon " + d.trim();
                    if (!rankManager.hasPermission(userId, fullCmd)) {
                        sendMessage(chatId, "⛔ У вашего ранга \"" + rankName + "\" нет доступа к команде " + fullCmd);
                        return;
                    }
                    String limit = rankManager.getCommandLimit(userId, fullCmd);
                    if (limit != null && !limit.equals("навсегда")) {
                        String[] cmdParts = command.split(" ");
                        if (cmdParts.length > 2 && cmdParts[2].matches("\\d+[smhdwMy]")) {
                            String requestedTime = cmdParts[2];
                            if (!isTimeAllowed(requestedTime, limit)) {
                                sendMessage(chatId, "⛔ Ваш лимит: не более " + limit + ". Вы запросили " + requestedTime);
                                return;
                            }
                        }
                    }
                }
                sendConfirmationRequest(chatId, command, userId);
                return;
            }
        }

        // --- Обычные команды ---
        executeNormalCommand(chatId, command, userId);
    }

    // ============================================
    // ==== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    // ============================================

    private boolean isTimeAllowed(String requested, String limit) {
        if (limit.equals("навсегда")) return true;
        long requestedMs = parseTimeToMillis(requested);
        long limitMs = parseTimeToMillis(limit);
        return requestedMs <= limitMs;
    }

    private long parseTimeToMillis(String time) {
        if (time == null) return Long.MAX_VALUE;
        char unit = time.charAt(time.length() - 1);
        long value = Long.parseLong(time.substring(0, time.length() - 1));
        switch (unit) {
            case 's': return value * 1000;
            case 'm': return value * 60 * 1000;
            case 'h': return value * 60 * 60 * 1000;
            case 'd': return value * 24 * 60 * 60 * 1000;
            case 'w': return value * 7 * 24 * 60 * 60 * 1000;
            case 'M': return value * 30L * 24 * 60 * 60 * 1000;
            case 'y': return value * 365L * 24 * 60 * 60 * 1000;
            default: return Long.MAX_VALUE;
        }
    }

    private void sendConfirmationRequest(long chatId, String command, long userId) {
        String[] parts = command.split(" ");
        String action = parts[0];
        String playerName = parts[1];
        String duration = "навсегда";
        String reason = "";
        int start = 2;

        if (parts.length > 2 && punishmentManager.isValidTime(parts[2])) {
            duration = parts[2];
            start = 3;
        }

        if (parts.length > start) {
            reason = String.join(" ", Arrays.copyOfRange(parts, start, parts.length));
        } else {
            reason = "Без причины";
        }

        String actionName = "";
        switch (action) {
            case "ban": actionName = "забанить"; break;
            case "mute": actionName = "замутить"; break;
            case "kick": actionName = "кикнуть"; break;
            case "unban": actionName = "разбанить"; break;
            case "unmute": actionName = "размутить"; break;
        }

        String message = "⚠️ Подтвердить " + actionName + " игрока " + playerName + "?\n" +
                "📝 Причина: " + reason + "\n" +
                "⏱ Срок: " + duration;

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton confirmBtn = new InlineKeyboardButton();
        confirmBtn.setText("✅ Да");
        confirmBtn.setCallbackData("confirm_" + action + "_" + playerName + "_" + duration + "_" + reason);

        InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
        cancelBtn.setText("❌ Отмена");
        cancelBtn.setCallbackData("cancel_" + System.currentTimeMillis());

        row.add(confirmBtn);
        row.add(cancelBtn);
        rows.add(row);
        markup.setKeyboard(rows);

        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText("[БОТ] ⚠️ " + message);
        msg.setReplyMarkup(markup);

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void executeNormalCommand(long chatId, String command, long userId) {
        String issuer = plugin.getCustomSender(userId);
        if (issuer == null && userId == plugin.getOwnerId()) {
            issuer = "RCON@Grif_Mo";
        }

        final long finalChatId = chatId;
        final String finalCommand = command;
        final String finalIssuer = issuer;

        final int[] tempMsgId = {0};
        try {
            SendMessage temp = new SendMessage();
            temp.setChatId(String.valueOf(chatId));
            temp.setText("[БОТ] ⏳ Выполняю команду...");
            var sent = execute(temp);
            tempMsgId[0] = sent.getMessageId();
        } catch (Exception e) {}

        final int finalTempMsgId = tempMsgId[0];

        Bukkit.getScheduler().runTask(plugin, () -> {
            String response = commandExecutor.executeCommand(finalCommand, finalIssuer);

            if (finalTempMsgId != 0) {
                deleteMessage(String.valueOf(finalChatId), finalTempMsgId);
            }

            sendResponse(finalChatId, "✅ " + response);
        });
    }

    private void handlePagination(long chatId, String type, String playerName, int page, int messageId) {
        int pageSize = 10;
        List<String> items = new ArrayList<>();

        switch (type) {
            case "banlist":
                items = punishmentManager.getBanList(1, Integer.MAX_VALUE);
                break;
            case "mutelist":
                items = punishmentManager.getMuteList(1, Integer.MAX_VALUE);
                break;
            case "shist":
                List<PunishmentManager.HistoryEntry> history = punishmentManager.getHistory(playerName);
                for (PunishmentManager.HistoryEntry entry : history) {
                    String timeAgo = punishmentManager.getTimeAgo(entry.timestamp);
                    String status = entry.type.equals("ban") ?
                        (punishmentManager.isBanned(playerName) ? "[Активен]" : "[Истек]") :
                        (punishmentManager.isMuted(playerName) ? "[Активен]" : "[Истек]");
                    items.add("- " + timeAgo + " -\n   " + playerName + " был " + entry.getActionName() +
                            " на " + entry.duration + " " + entry.issuer + ": " + entry.reason + " " + status);
                }
                break;
            default:
                return;
        }

        int totalPages = (int) Math.ceil((double) items.size() / pageSize);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, items.size());
        List<String> pageItems = items.subList(start, end);

        StringBuilder response = new StringBuilder();
        String title = type.equals("banlist") ? "Список банов" :
                       type.equals("mutelist") ? "Список мутов" :
                       "История наказаний для " + playerName;
        response.append("📋 ").append(title).append(" (Страница ").append(page).append("/").append(totalPages).append(")");

        for (String item : pageItems) {
            response.append("\n\n").append(item);
        }

        response.append("\n\n📊 Всего записей: ").append(items.size());

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        if (page > 1) {
            InlineKeyboardButton prevBtn = new InlineKeyboardButton();
            prevBtn.setText("⬅️ Назад");
            prevBtn.setCallbackData("page_" + type + "_" + playerName + "_" + (page - 1));
            row.add(prevBtn);
        }

        if (page < totalPages) {
            InlineKeyboardButton nextBtn = new InlineKeyboardButton();
            nextBtn.setText("Вперёд ➡️");
            nextBtn.setCallbackData("page_" + type + "_" + playerName + "_" + (page + 1));
            row.add(nextBtn);
        }

        if (!row.isEmpty()) {
            rows.add(row);
            markup.setKeyboard(rows);
        }

        deleteMessage(String.valueOf(chatId), messageId);

        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText("[БОТ] " + response.toString());
        if (!rows.isEmpty()) {
            msg.setReplyMarkup(markup);
        }
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // ============================================
    // ==== ОТПРАВКА КРАСИВЫХ ОТВЕТОВ =====
    // ============================================

    private void sendResponse(long chatId, String text) {
        sendMessage(chatId, "[БОТ] Ответ сервера:\n" + text);
    }

    public void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void deleteMessage(String chatId, int messageId) {
        try {
            DeleteMessage delete = new DeleteMessage();
            delete.setChatId(chatId);
            delete.setMessageId(messageId);
            execute(delete);
        } catch (TelegramApiException e) {
            // Игнорируем
        }
    }

    // ============================================
    // ==== ИГРОВЫЕ КОМАНДЫ =====
    // ============================================

    private void sendOnline(long chatId) {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        StringBuilder players = new StringBuilder();
        for (Player p : Bukkit.getOnlinePlayers()) {
            players.append("• ").append(p.getName()).append("\n");
        }
        String msg = "👥 Онлайн: " + online + "/" + max + "\n\n" + players.toString();
        sendResponse(chatId, msg);
    }

    private void sendTps(long chatId) {
        double tps = Bukkit.getTPS()[0];
        String status = tps >= 20 ? "🟢 Отлично" : tps >= 18 ? "🟡 Хорошо" : tps >= 15 ? "🟠 Средне" : "🔴 Плохо";
        String msg = "⚡ TPS: " + String.format("%.2f", tps) + "\n📊 Статус: " + status;
        sendResponse(chatId, msg);
    }

    private void sendHelp(long chatId) {
        String help = "🤖 ДОСТУПНЫЕ КОМАНДЫ\n" +
                SEPARATOR + "\n\n" +
                "📌 ОБЩИЕ КОМАНДЫ:\n" +
                "!online — список игроков\n" +
                "!tps — производительность\n" +
                "!me — информация о вас\n" +
                "!help — эта справка\n\n" +
                "📌 НАКАЗАНИЯ:\n" +
                "!rcon ban <ник> [время] <причина>\n" +
                "!rcon unban <ник> <причина>\n" +
                "!rcon mute <ник> [время] <причина>\n" +
                "!rcon unmute <ник> <причина>\n" +
                "!rcon kick <ник> <причина>\n" +
                "!rcon banlist — список банов\n" +
                "!rcon mutelist — список мутов\n" +
                "!rcon shist <ник> — история\n" +
                "!rcon bc <сообщение> — объявление\n\n" +
                "📌 БАН В БОТЕ:\n" +
                "!rcon botban <айди> [время] <причина>\n" +
                "!rcon botunban <айди> <причина>\n" +
                "!rcon botbanlist\n" +
                "!rcon botbaninfo <айди>\n\n" +
                "📌 СИСТЕМА РАНГОВ (владелец):\n" +
                "!rcon rank create <название> [префикс] [эмодзи] [цвет]\n" +
                "!rcon rank delete <название>\n" +
                "!rcon rank rename <старое> <новое>\n" +
                "!rcon rank clone <исходный> <новый>\n" +
                "!rcon rank list — список рангов\n" +
                "!rcon rank info <название>\n" +
                "!rcon rank addperm <название> <команда> [лимит]\n" +
                "!rcon rank removeperm <название> <команда>\n" +
                "!rcon rank perms <название>\n" +
                "!rcon rank adduser <название> <айди> [причина]\n" +
                "!rcon rank removeuser <айди> [причина]\n" +
                "!rcon rank users <название>\n" +
                "!rcon rank promote <айди> [причина]\n" +
                "!rcon rank demote <айди> [причина]\n" +
                "!rcon rank setprefix <название> <префикс>\n" +
                "!rcon rank setemoji <название> <эмодзи>\n" +
                "!rcon rank setcolor <название> <цвет>\n" +
                "!rcon rank setpriority <название> <приоритет>\n" +
                "!rcon rank myrank — мой ранг\n" +
                "!rcon rank hierarchy — иерархия\n" +
                "!rcon rank stats — статистика\n" +
                "!rcon rank clear <название>\n" +
                "!rcon rank reset <название>\n\n" +
                "📌 АДМИН:\n" +
                "!rcon messageall <сообщение> — рассылка\n" +
                "!rcon tex on/off — техработы";
        sendMessage(chatId, help);
    }

    private void sendInfo(long chatId) {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        String version = Bukkit.getVersion();
        String msg = "🖥️ ИНФОРМАЦИЯ О СЕРВЕРЕ\n" +
                SEPARATOR + "\n" +
                "📌 Версия: " + version + "\n" +
                "👥 Игроки: " + online + "/" + max + "\n" +
                "👑 Владелец: RCON@Grif_Mo";
        sendResponse(chatId, msg);
    }

    private void sendWelcome(long chatId) {
        String welcome = "🎮 ДОБРО ПОЖАЛОВАТЬ!\n" +
                SEPARATOR + "\n" +
                "💡 Используй !help для списка команд\n" +
                "📌 !online — кто онлайн\n" +
                "⚡ !tps — производительность";
        sendMessage(chatId, welcome);
    }

    private void sendMe(long chatId, long userId) {
        String rankName = rankManager.getUserRank(userId);
        String rankDisplay = rankManager.getFullRankDisplay(userId);
        String isAdmin = plugin.isAdmin(userId) ? "✅ Да" : "❌ Нет";
        boolean isBanned = botBanManager.isBanned(userId);

        String response = "📋 ИНФОРМАЦИЯ О ВАС\n" +
                SEPARATOR + "\n" +
                "🆔 ID: " + userId + "\n" +
                "👑 Админ: " + isAdmin + "\n" +
                "🔰 Ранг: " + (rankName != null ? rankDisplay : "Без ранга") + "\n" +
                "⛔ Бан в боте: " + (isBanned ? "❌ ДА" : "✅ НЕТ");

        if (isBanned) {
            BotBanManager.BanData ban = botBanManager.getBanData(userId);
            response += "\n\n📝 Причина: " + ban.reason + "\n" +
                    "⏱ Осталось: " + ban.getTimeLeft(botBanManager);
        }

        sendResponse(chatId, response);
    }
}
