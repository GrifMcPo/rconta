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

    private static final String SEPARATOR = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    public TelegramBotHandler(String token, TelegramConsoleBot plugin, PlayerManager playerManager,
                              CommandLogger commandLogger, LogsCommand logsCommand,
                              CommandExecutor commandExecutor, PunishmentManager punishmentManager,
                              RankManager rankManager) {
        this.botToken = token;
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.commandLogger = commandLogger;
        this.logsCommand = logsCommand;
        this.commandExecutor = commandExecutor;
        this.punishmentManager = punishmentManager;
        this.rankManager = rankManager;
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
                sendFormattedResponse(Long.parseLong(chatId), result);
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

        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String messageText = update.getMessage().getText();
        long userId = update.getMessage().getFrom().getId();
        long chatId = update.getMessage().getChatId();

        plugin.getLogger().info("📩 Получено: " + messageText + " от " + userId);

        if (rankManager.isTechWork() && !plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
            sendMessage(chatId, "🔧 На сервере ведутся технические работы. Попробуйте позже.");
            return;
        }

        if (messageText.equalsIgnoreCase("/start")) {
            if (!rankManager.getAllUsers().contains(userId)) {
                rankManager.getAllUsers().add(userId);
            }
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

        if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
            String rankName = rankManager.getUserRank(userId);
            if (rankName == null) {
                sendMessage(chatId, "⛔ Доступ запрещён. У вас нет прав.");
                return;
            }
        }

        String command = messageText.substring(6).trim();
        if (command.isEmpty()) {
            sendMessage(chatId, "ℹ️ Введи команду после !rcon");
            return;
        }

        handleRconCommand(chatId, command, userId);
    }

    private void handleRconCommand(long chatId, String command, long userId) {
        if (command.startsWith("logs ")) {
            String[] args = command.split(" ");
            SendMessage response = logsCommand.handleLogs(chatId, args);
            try { execute(response); } catch (TelegramApiException e) { e.printStackTrace(); }
            return;
        }

        // ===== СИСТЕМА РАНГОВ =====
        if (command.startsWith("rang create ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может создавать ранги.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon rang create <название>");
                return;
            }
            String rankName = parts[2];
            if (rankManager.createRank(rankName)) {
                sendFormattedResponse(chatId, "✅ Ранг " + rankName + " создан!");
            } else {
                sendMessage(chatId, "❌ Ранг " + rankName + " уже существует.");
            }
            return;
        }

        if (command.startsWith("rang delete ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может удалять ранги.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon rang delete <название>");
                return;
            }
            String rankName = parts[2];
            if (rankManager.deleteRank(rankName)) {
                sendFormattedResponse(chatId, "✅ Ранг " + rankName + " удалён!");
            } else {
                sendMessage(chatId, "❌ Ранг " + rankName + " не найден.");
            }
            return;
        }

        if (command.startsWith("rang add ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может выдавать права.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 4) {
                sendMessage(chatId, "❌ Используй: !rcon rang <название> add <команда> [лимит]");
                return;
            }
            String rankName = parts[1];
            String cmd = parts[3];
            String limit = parts.length > 4 ? parts[4] : "навсегда";

            if (rankManager.addPermission(rankName, cmd, limit)) {
                sendFormattedResponse(chatId, "✅ Рангу " + rankName + " выдана команда " + cmd + " (лимит: " + limit + ")");
            } else {
                sendMessage(chatId, "❌ Ранг " + rankName + " не найден.");
            }
            return;
        }

        if (command.startsWith("rang remove ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может удалять права.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 4) {
                sendMessage(chatId, "❌ Используй: !rcon rang <название> remove <команда>");
                return;
            }
            String rankName = parts[1];
            String cmd = parts[3];

            if (rankManager.removePermission(rankName, cmd)) {
                sendFormattedResponse(chatId, "✅ У ранга " + rankName + " удалена команда " + cmd);
            } else {
                sendMessage(chatId, "❌ Ранг " + rankName + " не найден.");
            }
            return;
        }

        if (command.startsWith("rang addid ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может добавлять в ранги.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 4) {
                sendMessage(chatId, "❌ Используй: !rcon rang <название> addid <айди>");
                return;
            }
            String rankName = parts[1];
            long targetId;
            try { targetId = Long.parseLong(parts[3]); } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Неверный ID.");
                return;
            }

            if (rankManager.addUserToRank(rankName, targetId)) {
                sendFormattedResponse(chatId, "✅ Пользователь " + targetId + " добавлен в ранг " + rankName);
                sendMessage(targetId, "🔰 Вас добавили в ранг " + rankName + "!");
            } else {
                sendMessage(chatId, "❌ Ранг " + rankName + " не найден.");
            }
            return;
        }

        if (command.startsWith("rang remid ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может снимать ранги.");
                return;
            }
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon rang remid <айди> <причина>");
                return;
            }
            long targetId;
            try { targetId = Long.parseLong(parts[1]); } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Неверный ID.");
                return;
            }
            String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));

            String oldRank = rankManager.getUserRank(targetId);
            if (rankManager.removeUserFromRank(targetId, reason)) {
                sendFormattedResponse(chatId, "✅ Пользователь " + targetId + " удалён из ранга " + oldRank);
                sendMessage(targetId, "🔰 Вас сняли с ранга " + oldRank + "!\n📝 Причина: " + reason);
            } else {
                sendMessage(chatId, "❌ Пользователь не найден в рангах.");
            }
            return;
        }

        if (command.startsWith("rang list")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Доступ запрещён.");
                return;
            }
            List<String> rankNames = rankManager.getRankNames();
            if (rankNames.isEmpty()) {
                sendFormattedResponse(chatId, "📋 Рангов нет.");
            } else {
                StringBuilder response = new StringBuilder();
                response.append("📋 Список рангов:\n");
                for (String name : rankNames) {
                    RankManager.Rank rank = rankManager.getRank(name);
                    response.append("• ").append(name).append(" (Пользователей: ").append(rank.getUsers().size()).append(")\n");
                    if (!rank.getPermissions().isEmpty()) {
                        response.append("  Права:\n");
                        for (Map.Entry<String, String> perm : rank.getPermissions().entrySet()) {
                            response.append("    - ").append(perm.getKey()).append(" (лимит: ").append(perm.getValue()).append("\n");
                        }
                    }
                }
                sendFormattedResponse(chatId, response.toString());
            }
            return;
        }

        if (command.equalsIgnoreCase("listid")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Доступ запрещён.");
                return;
            }
            List<Long> allUsers = rankManager.getAllUsers();
            if (allUsers.isEmpty()) {
                sendFormattedResponse(chatId, "📋 Нет пользователей.");
            } else {
                StringBuilder response = new StringBuilder();
                response.append("📋 Все пользователи (").append(allUsers.size()).append("):\n");
                for (long id : allUsers) {
                    String rank = rankManager.getUserRank(id);
                    response.append("• ").append(id).append(" → ").append(rank != null ? rank : "Без ранга").append("\n");
                }
                sendFormattedResponse(chatId, response.toString());
            }
            return;
        }

        if (command.startsWith("messageall ")) {
            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Только владелец может рассылать сообщения.");
                return;
            }
            String message = command.substring(11);
            List<Long> allUsers = rankManager.getAllUsers();
            int count = 0;
            for (long id : allUsers) {
                try { sendMessage(id, "📢 Рассылка от администрации:\n" + message); count++; } catch (Exception e) {}
            }
            sendFormattedResponse(chatId, "✅ Сообщение отправлено " + count + " пользователям.");
            return;
        }

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
            sendFormattedResponse(chatId, state ? "🔧 Техработы ВКЛЮЧЕНЫ" : "✅ Техработы ВЫКЛЮЧЕНЫ");
            return;
        }

        // ===== Опасные команды =====
        String[] dangerous = {"ban ", "mute ", "kick ", "unban ", "unmute "};
        for (String d : dangerous) {
            if (command.startsWith(d)) {
                if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                    String rankName = rankManager.getUserRank(userId);
                    if (rankName == null) {
                        sendMessage(chatId, "⛔ У вас нет прав для этой команды.");
                        return;
                    }
                    String fullCmd = "!rcon " + d.trim();
                    if (!rankManager.hasPermission(userId, fullCmd)) {
                        sendMessage(chatId, "⛔ У вашего ранга нет прав на эту команду.");
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

        executeNormalCommand(chatId, command, userId);
    }

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
        msg.setText("[БОТ] " + message);
        msg.setReplyMarkup(markup);

        try { execute(msg); } catch (TelegramApiException e) { e.printStackTrace(); }
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
            temp.setText("[БОТ] Выполняю команду..");
            var sent = execute(temp);
            tempMsgId[0] = sent.getMessageId();
        } catch (Exception e) {}

        final int finalTempMsgId = tempMsgId[0];

        Bukkit.getScheduler().runTask(plugin, () -> {
            String response = commandExecutor.executeCommand(finalCommand, finalIssuer);
            String formatted = "[БОТ] Ответ сервера:\n" + SEPARATOR + "\n" + response + "\n" + SEPARATOR;

            if (finalTempMsgId != 0) {
                deleteMessage(String.valueOf(finalChatId), finalTempMsgId);
            }

            sendMessage(finalChatId, formatted);
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
                    items.add(" - " + timeAgo + " -\n   " + playerName + " был " + entry.getActionName() +
                            " на " + entry.duration + " " + entry.issuer + ": " + entry.reason + " " + status);
                }
                break;
            default: return;
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
        response.append("📋 ").append(title).append(" (Страница ").append(page).append("/").append(totalPages).append(")\n");
        response.append(SEPARATOR).append("\n");
        for (String item : pageItems) {
            response.append(item).append("\n");
        }
        response.append(SEPARATOR).append("\n");
        response.append("📊 Всего записей: ").append(items.size());

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
        msg.setText("[БОТ] Ответ сервера:\n" + response.toString());
        if (!rows.isEmpty()) {
            msg.setReplyMarkup(markup);
        }
        try { execute(msg); } catch (TelegramApiException e) { e.printStackTrace(); }
    }

    public void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }

    private void deleteMessage(String chatId, int messageId) {
        try {
            DeleteMessage delete = new DeleteMessage();
            delete.setChatId(chatId);
            delete.setMessageId(messageId);
            execute(delete);
        } catch (TelegramApiException e) {}
    }

    private void sendFormattedResponse(long chatId, String text) {
        String message = "[БОТ] Ответ сервера:\n" + SEPARATOR + "\n" + text + "\n" + SEPARATOR;
        sendMessage(chatId, message);
    }

    private void sendOnline(long chatId) {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        StringBuilder players = new StringBuilder();
        for (Player p : Bukkit.getOnlinePlayers()) {
            players.append("• ").append(p.getName()).append("\n");
        }
        String msg = "👥 Онлайн: " + online + "/" + max + "\n\n" + players.toString();
        sendFormattedResponse(chatId, msg);
    }

    private void sendTps(long chatId) {
        double tps = Bukkit.getTPS()[0];
        String status = tps >= 20 ? "🟢 Отлично" : tps >= 18 ? "🟡 Хорошо" : tps >= 15 ? "🟠 Средне" : "🔴 Плохо";
        String msg = "⚡ TPS: " + String.format("%.2f", tps) + "\n📊 Статус: " + status;
        sendFormattedResponse(chatId, msg);
    }

    private void sendHelp(long chatId) {
        String help = "🤖 Доступные команды:\n\n" +
                "👥 !online — список игроков онлайн\n" +
                "⚡ !tps — производительность сервера\n" +
                "ℹ️ !info — информация о сервере\n" +
                "📋 !me — информация о вас\n\n" +
                "🔹 Команды наказаний (админы):\n" +
                "!rcon ban <ник> [время] <причина>\n" +
                "!rcon unban <ник> <причина>\n" +
                "!rcon mute <ник> [время] <причина>\n" +
                "!rcon unmute <ник> <причина>\n" +
                "!rcon kick <ник> <причина>\n" +
                "!rcon banlist [страница]\n" +
                "!rcon mutelist [страница]\n" +
                "!rcon shist <ник> [страница]\n" +
                "!rcon logs <ник> [дней]";
        sendFormattedResponse(chatId, help);
    }

    private void sendInfo(long chatId) {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        String version = Bukkit.getVersion();
        String msg = "🖥️ Информация о сервере:\n\n" +
                "📌 Версия: " + version + "\n" +
                "👥 Игроки: " + online + "/" + max;
        sendFormattedResponse(chatId, msg);
    }

    private void sendWelcome(long chatId) {
        String welcome = "🎮 Добро пожаловать!\n" +
                SEPARATOR + "\n" +
                "💡 Команды: !online, !tps, !me, !help";
        sendMessage(chatId, welcome);
    }

    private void sendMe(long chatId, long userId) {
        String telegramId = String.valueOf(userId);
        String isAdmin = plugin.isAdmin(userId) ? "✅ Да" : "❌ Нет";
        String rank = rankManager.getUserRank(userId);
        String rankStr = rank != null ? rank : "Без ранга";

        String response = "📋 Информация о вас:\n" +
                SEPARATOR + "\n" +
                "🆔 Telegram ID: " + telegramId + "\n" +
                "👑 Админ: " + isAdmin + "\n" +
                "🔰 Ранг: " + rankStr + "\n" +
                SEPARATOR;

        sendFormattedResponse(chatId, response);
    }
}
