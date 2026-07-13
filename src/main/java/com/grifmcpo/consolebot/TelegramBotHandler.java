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

public class TelegramBotHandler extends TelegramLongPollingBot {

    private final String botToken;
    private final TelegramConsoleBot plugin;
    private final PlayerManager playerManager;
    private final CommandLogger commandLogger;
    private final LogsCommand logsCommand;
    private final CommandExecutor commandExecutor;
    private final PunishmentManager punishmentManager;
    private final BotBanManager botBanManager;

    // ============================================
    // ==== КТО ВИДИТ СКРЫТЫЕ НАКАЗАНИЯ (-s) =====
    // ============================================
    private final List<Long> hiddenViewers = new ArrayList<>();

    private static final String SEPARATOR = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    public TelegramBotHandler(String token, TelegramConsoleBot plugin, PlayerManager playerManager,
                              CommandLogger commandLogger, LogsCommand logsCommand,
                              CommandExecutor commandExecutor, PunishmentManager punishmentManager,
                              BotBanManager botBanManager) {
        this.botToken = token;
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.commandLogger = commandLogger;
        this.logsCommand = logsCommand;
        this.commandExecutor = commandExecutor;
        this.punishmentManager = punishmentManager;
        this.botBanManager = botBanManager;
        
        loadHiddenViewers();
    }

    private void loadHiddenViewers() {
        hiddenViewers.clear();
        
        // Владелец всегда видит
        hiddenViewers.add(plugin.getOwnerId());
        
        // Все админы из admins.yml видят
        for (String id : plugin.getAdmins().keySet()) {
            try {
                hiddenViewers.add(Long.parseLong(id));
            } catch (NumberFormatException e) {}
        }
        
        // ============================================
        // ==== ТУТ ДОБАВЛЯЕМ ID КТО ВИДИТ -s =====
        // ==== (staff, leader, sponsor, OP) =====
        // ============================================
        // hiddenViewers.add(123456789L); // staff
        // hiddenViewers.add(987654321L); // leader
        // hiddenViewers.add(555555555L); // sponsor
        // hiddenViewers.add(111111111L); // OP
        
        plugin.getLogger().info("✅ Загружено зрителей скрытых наказаний: " + hiddenViewers.size());
    }

    public boolean canSeeHidden(long userId) {
        return hiddenViewers.contains(userId);
    }

    private void notifyStaffOnly(String message) {
        int count = 0;
        for (long id : hiddenViewers) {
            try {
                sendMessage(id, "[СТАФФ] " + message);
                count++;
            } catch (Exception e) {}
        }
        plugin.getLogger().info("🔒 Скрытое уведомление отправлено " + count + " пользователям");
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

        // ============================================
        // ==== ПРОВЕРКА БАНА В БОТЕ =====
        // ============================================
        if (botBanManager.isBanned(userId)) {
            if (userId != plugin.getOwnerId() && !plugin.isAdmin(userId)) {
                sendMessage(chatId, botBanManager.getBanMessage(userId));
                return;
            }
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
        // ==== ПРОВЕРКА ПРАВ (ТОЛЬКО АДМИНЫ) =====
        // ============================================
        if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
            sendMessage(chatId, "⛔ У вас нет доступа к RCON командам!");
            return;
        }

        // --- Обработка команд ---
        handleRconCommand(chatId, command, userId);
    }

    // ============================================
    // ==== ОБРАБОТКА RCON КОМАНД =====
    // ============================================
    private void handleRconCommand(long chatId, String command, long userId) {

        // ============================================
        // ==== КАСТОМНЫЕ КОМАНДЫ (ОБРАБАТЫВАЕМ В БОТЕ) =====
        // ============================================

        // --- !rcon messageall ---
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
            
            String senderName = plugin.getCustomSender(userId);
            if (senderName == null && userId == plugin.getOwnerId()) {
                senderName = "Владелец";
            }
            
            String formattedMessage = "📢 " + message;
            
            int count = 0;
            for (long id : hiddenViewers) {
                try {
                    sendMessage(id, formattedMessage);
                    count++;
                } catch (Exception e) {}
            }
            
            sendResponse(chatId, "✅ Сообщение отправлено " + count + " пользователям!\n" +
                    "👤 Отправитель: " + senderName);
            return;
        }

        // --- !rcon ban (с -s) ---
        if (command.startsWith("ban ")) {
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon ban <ник> [время] <причина> [-s]");
                return;
            }
            
            boolean hidden = false;
            String lastArg = parts[parts.length - 1];
            if (lastArg.equals("-s") || lastArg.equals("-S")) {
                hidden = true;
                if (!canSeeHidden(userId)) {
                    sendMessage(chatId, "⛔ У вас нет прав на скрытые наказания!\n" +
                            "🔒 Скрытые видят только: staff, leader, sponsor, OP");
                    return;
                }
            }
            
            String target = parts[1];
            String duration = "навсегда";
            String reason = "";
            int start = 2;
            int end = hidden ? parts.length - 1 : parts.length;
            
            if (end > start + 1 && punishmentManager.isValidTime(parts[2])) {
                duration = parts[2];
                start = 3;
            }
            
            if (end > start) {
                reason = String.join(" ", Arrays.copyOfRange(parts, start, end));
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
                
                if (hidden) {
                    response += "\n🔒 СКРЫТОЕ НАКАЗАНИЕ (видят только staff, leader, sponsor, OP)";
                    notifyStaffOnly("🔒 СКРЫТЫЙ БАН\n" +
                            "👤 Игрок: " + target + "\n" +
                            "📝 Причина: " + reason + "\n" +
                            "⏱ Срок: " + duration + "\n" +
                            "👤 Выдал: " + issuer);
                }
                
                sendResponse(chatId, response);
            } else {
                sendMessage(chatId, "❌ Игрок " + target + " уже забанен!");
            }
            return;
        }

        // --- !rcon mute (с -s) ---
        if (command.startsWith("mute ")) {
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon mute <ник> [время] <причина> [-s]");
                return;
            }
            
            boolean hidden = false;
            String lastArg = parts[parts.length - 1];
            if (lastArg.equals("-s") || lastArg.equals("-S")) {
                hidden = true;
                if (!canSeeHidden(userId)) {
                    sendMessage(chatId, "⛔ У вас нет прав на скрытые наказания!");
                    return;
                }
            }
            
            String target = parts[1];
            String duration = "навсегда";
            String reason = "";
            int start = 2;
            int end = hidden ? parts.length - 1 : parts.length;
            
            if (end > start + 1 && punishmentManager.isValidTime(parts[2])) {
                duration = parts[2];
                start = 3;
            }
            
            if (end > start) {
                reason = String.join(" ", Arrays.copyOfRange(parts, start, end));
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
                
                if (hidden) {
                    response += "\n🔒 СКРЫТОЕ НАКАЗАНИЕ (видят только staff, leader, sponsor, OP)";
                    notifyStaffOnly("🔒 СКРЫТЫЙ МУТ\n" +
                            "👤 Игрок: " + target + "\n" +
                            "📝 Причина: " + reason + "\n" +
                            "⏱ Срок: " + duration + "\n" +
                            "👤 Выдал: " + issuer);
                }
                
                sendResponse(chatId, response);
            } else {
                sendMessage(chatId, "❌ Игрок " + target + " уже замучен!");
            }
            return;
        }

        // --- !rcon kick (с -s) ---
        if (command.startsWith("kick ")) {
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon kick <ник> <причина> [-s]");
                return;
            }
            
            boolean hidden = false;
            String lastArg = parts[parts.length - 1];
            if (lastArg.equals("-s") || lastArg.equals("-S")) {
                hidden = true;
                if (!canSeeHidden(userId)) {
                    sendMessage(chatId, "⛔ У вас нет прав на скрытые наказания!");
                    return;
                }
            }
            
            String target = parts[1];
            int end = hidden ? parts.length - 1 : parts.length;
            String reason = String.join(" ", Arrays.copyOfRange(parts, 2, end));

            String issuer = plugin.getCustomSender(userId);
            if (issuer == null && userId == plugin.getOwnerId()) {
                issuer = "RCON@Grif_Mo";
            }

            sendMessage(chatId, "[БОТ] ⏳ Выполняю команду...");

            if (punishmentManager.kickPlayer(target, issuer, reason)) {
                String response = "✅ Игрок " + target + " кикнут!\n" +
                        "👤 Выдал: " + issuer + "\n" +
                        "📝 Причина: " + reason;
                
                if (hidden) {
                    response += "\n🔒 СКРЫТОЕ НАКАЗАНИЕ (видят только staff, leader, sponsor, OP)";
                    notifyStaffOnly("🔒 СКРЫТЫЙ КИК\n" +
                            "👤 Игрок: " + target + "\n" +
                            "📝 Причина: " + reason + "\n" +
                            "👤 Выдал: " + issuer);
                }
                
                sendResponse(chatId, response);
            } else {
                sendMessage(chatId, "❌ Игрок " + target + " не найден!");
            }
            return;
        }

        // --- !rcon unban ---
        if (command.startsWith("unban ")) {
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
                sendResponse(chatId, "✅ Игрок " + target + " разбанен!\n" +
                        "👤 Выдал: " + issuer + "\n" +
                        "📝 Причина: " + reason);
            } else {
                sendMessage(chatId, "❌ Игрок " + target + " не забанен!");
            }
            return;
        }

        // --- !rcon unmute ---
        if (command.startsWith("unmute ")) {
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
                sendResponse(chatId, "✅ Игрок " + target + " размучен!\n" +
                        "👤 Выдал: " + issuer + "\n" +
                        "📝 Причина: " + reason);
            } else {
                sendMessage(chatId, "❌ Игрок " + target + " не замучен!");
            }
            return;
        }

        // --- !rcon banlist ---
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

        // --- !rcon mutelist ---
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

        // --- !rcon shist / hist ---
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

        // --- !rcon bc / bcast ---
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

        // --- !rcon tex ---
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

        // --- !rcon botban ---
        if (command.startsWith("botban ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только админы могут банить в боте.");
                return;
            }

            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon botban <айди> [время] <причина>");
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

        // --- !rcon botunban ---
        if (command.startsWith("botunban ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только админы могут разбанивать в боте.");
                return;
            }

            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon botunban <айди> <причина>");
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

        // --- !rcon botbanlist ---
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

        // --- !rcon botbaninfo ---
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

        // --- !rcon botbancheck (публичная) ---
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
        // ==== ВСЕ ОСТАЛЬНЫЕ КОМАНДЫ (ОТПРАВЛЯЕМ НА СЕРВЕР) =====
        // ============================================
        // Теперь ЛЮБАЯ команда, которая не обработалась выше,
        // отправляется на сервер через RCON!
        executeServerCommand(chatId, command, userId);
    }

    // ============================================
    // ==== ОТПРАВКА КОМАНДЫ НА СЕРВЕР (RCON) =====
    // ============================================
    private void executeServerCommand(long chatId, String command, long userId) {
        String issuer = plugin.getCustomSender(userId);
        if (issuer == null && userId == plugin.getOwnerId()) {
            issuer = "RCON@Grif_Mo";
        }

        final long finalChatId = chatId;
        final String finalCommand = command;
        final String finalIssuer = issuer;

        // Показываем "Выполняю..."
        sendMessage(chatId, "[БОТ] ⏳ Выполняю команду на сервере...");

        // Выполняем в синхронном потоке
        Bukkit.getScheduler().runTask(plugin, () -> {
            String response = commandExecutor.executeCommand(finalCommand, finalIssuer);
            sendResponse(finalChatId, "✅ " + response);
        });
    }

    // ============================================
    // ==== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    // ============================================

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
    // ==== ОТПРАВКА СООБЩЕНИЙ =====
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
                "📌 КАСТОМНЫЕ КОМАНДЫ (бот):\n" +
                "!rcon ban <ник> [время] <причина> [-s]\n" +
                "!rcon unban <ник> <причина>\n" +
                "!rcon mute <ник> [время] <причина> [-s]\n" +
                "!rcon unmute <ник> <причина>\n" +
                "!rcon kick <ник> <причина> [-s]\n" +
                "!rcon banlist — список банов\n" +
                "!rcon mutelist — список мутов\n" +
                "!rcon shist <ник> — история\n" +
                "!rcon bc <сообщение> — объявление\n" +
                "!rcon messageall <сообщение> — рассылка\n" +
                "!rcon tex on/off — техработы\n\n" +
                "📌 БАН В БОТЕ:\n" +
                "!rcon botban <айди> [время] <причина>\n" +
                "!rcon botunban <айди> <причина>\n" +
                "!rcon botbanlist\n" +
                "!rcon botbaninfo <айди>\n\n" +
                "📌 ЛЮБАЯ ДРУГАЯ КОМАНДА:\n" +
                "!rcon <любая команда> — отправится на сервер\n" +
                "Пример: !rcon op pley1657\n" +
                "Пример: !rcon deop pley1657\n\n" +
                "🔒 СКРЫТЫЕ НАКАЗАНИЯ (-s):\n" +
                "Видят только: staff, leader, sponsor, OP";
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
        String isAdmin = plugin.isAdmin(userId) ? "✅ Да" : "❌ Нет";
        boolean isBanned = botBanManager.isBanned(userId);
        boolean canSee = canSeeHidden(userId);

        String response = "📋 ИНФОРМАЦИЯ О ВАС\n" +
                SEPARATOR + "\n" +
                "🆔 ID: " + userId + "\n" +
                "👑 Админ: " + isAdmin + "\n" +
                "⛔ Бан в боте: " + (isBanned ? "❌ ДА" : "✅ НЕТ") + "\n" +
                "🔒 Видит скрытые (-s): " + (canSee ? "✅ ДА" : "❌ НЕТ");

        if (isBanned) {
            BotBanManager.BanData ban = botBanManager.getBanData(userId);
            response += "\n\n📝 Причина: " + ban.reason + "\n" +
                    "⏱ Осталось: " + ban.getTimeLeft(botBanManager);
        }

        sendResponse(chatId, response);
    }
}
