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

public class TelegramBotHandler extends TelegramLongPollingBot {

    private final String botToken;
    private final TelegramConsoleBot plugin;
    private final PlayerManager playerManager;
    private final CommandLogger commandLogger;
    private final LogsCommand logsCommand;
    private final CommandExecutor commandExecutor;
    private final PunishmentManager punishmentManager;
    private final AuthManager authManager;

    private static final String SEPARATOR = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    public TelegramBotHandler(String token, TelegramConsoleBot plugin, PlayerManager playerManager,
                              CommandLogger commandLogger, LogsCommand logsCommand,
                              CommandExecutor commandExecutor, PunishmentManager punishmentManager,
                              AuthManager authManager) {
        this.botToken = token;
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.commandLogger = commandLogger;
        this.logsCommand = logsCommand;
        this.commandExecutor = commandExecutor;
        this.punishmentManager = punishmentManager;
        this.authManager = authManager;
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
        // ===== КНОПКИ =====
        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            String chatId = update.getCallbackQuery().getMessage().getChatId().toString();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();

            // --- Подтверждение входа ---
            if (data.startsWith("auth_allow_")) {
                String playerName = data.substring(11);
                String ip = "0.0.0.0";
                authManager.activateSession(playerName, ip);
                Player player = Bukkit.getPlayerExact(playerName);
                if (player != null && player.isOnline()) {
                    authManager.unfreezePlayer(player);
                    player.sendMessage("§a✅ Вход подтверждён через Telegram!");
                }
                sendMessage(Long.parseLong(chatId), "✅ Вход для " + playerName + " разрешён!");
                deleteMessage(chatId, messageId);
                return;
            }

            if (data.startsWith("auth_deny_")) {
                String playerName = data.substring(10);
                Player player = Bukkit.getPlayerExact(playerName);
                if (player != null && player.isOnline()) {
                    authManager.kickPlayer(player, "Вход запрещён через Telegram!");
                }
                sendMessage(Long.parseLong(chatId), "❌ Вход для " + playerName + " запрещён!");
                deleteMessage(chatId, messageId);
                return;
            }

            // --- Подтверждение команд ---
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

            // --- Пагинация ---
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

        // ===== СООБЩЕНИЯ =====
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

        // --- !me ---
        if (messageText.equalsIgnoreCase("!me")) {
            sendMe(chatId, userId);
            return;
        }

        // --- /reg ---
        if (messageText.startsWith("/reg ") || messageText.startsWith("/register ")) {
            String[] parts = messageText.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: /reg <ник> <код>\nПример: /reg pley1657 482719");
                return;
            }

            String playerName = parts[1];
            String code = parts[2];
            String ip = "0.0.0.0";

            if (authManager.verifyAuthCode(playerName, code)) {
                if (authManager.registerPlayer(playerName, String.valueOf(userId), ip)) {
                    authManager.activateSession(playerName, ip);
                    sendMessage(chatId, "✅ Аккаунт " + playerName + " зарегистрирован!\n" +
                            "🔐 Теперь при входе на сервер бот будет запрашивать подтверждение.");

                    Player player = Bukkit.getPlayerExact(playerName);
                    if (player != null && player.isOnline()) {
                        authManager.unfreezePlayer(player);
                        player.sendMessage("§a✅ Аккаунт зарегистрирован через Telegram!");
                    }
                } else {
                    sendMessage(chatId, "❌ Ошибка регистрации! Возможно ник уже занят.");
                }
            } else {
                sendMessage(chatId, "❌ Неверный код или код истёк. Попробуйте зайти на сервер заново.");
            }
            return;
        }

        // --- Игровые команды ---
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

        // --- logs ---
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

        // --- Опасные команды (с подтверждением) ---
        String[] dangerous = {"ban ", "mute ", "kick ", "unban ", "unmute "};
        for (String d : dangerous) {
            if (command.startsWith(d)) {
                sendConfirmationRequest(chatId, command, userId);
                return;
            }
        }

        // --- Обычные команды ---
        executeNormalCommand(chatId, command, userId);
    }

    // ===== ЗАПРОС ПОДТВЕРЖДЕНИЯ =====
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

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // ===== ВЫПОЛНЕНИЕ ОБЫЧНОЙ КОМАНДЫ (С АВТОУДАЛЕНИЕМ) =====
    private void executeNormalCommand(long chatId, String command, long userId) {
        String issuer = plugin.getCustomSender(userId);
        if (issuer == null && userId == plugin.getOwnerId()) {
            issuer = "RCON@Grif_Mo";
        }

        final long finalChatId = chatId;
        final String finalCommand = command;
        final String finalIssuer = issuer;

        // Временное сообщение
        final int[] tempMsgId = {0};
        try {
            SendMessage temp = new SendMessage();
            temp.setChatId(String.valueOf(chatId));
            temp.setText("[БОТ] Выполняю команду..");
            var sent = execute(temp);
            tempMsgId[0] = sent.getMessageId();
        } catch (Exception e) {
            // Игнорируем
        }

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

    // ===== !ME =====
    private void sendMe(long chatId, long userId) {
        String telegramId = String.valueOf(userId);
        String playerName = authManager.getPlayerNameByTelegram(telegramId);

        if (playerName == null) {
            sendMessage(chatId, "❌ Вы не зарегистрированы.\n" +
                    "🔐 Зайдите на сервер и используйте /reg <ник> <код>");
            return;
        }

        String isAdmin = plugin.isAdmin(userId) ? "✅ Да" : "❌ Нет";
        String registeredAt = authManager.getRegisteredAt(playerName);
        String sessionActive = authManager.isSessionActive(playerName) ? "✅ Активна" : "❌ Не активна";
        String sessionTime = authManager.getSessionTimeLeft(playerName);
        String ip = authManager.getIpLast(playerName);

        String response = "📋 Информация о вас:\n" +
                SEPARATOR + "\n" +
                "👤 Ник: " + playerName + "\n" +
                "🆔 Telegram ID: " + telegramId + "\n" +
                "👑 Админ: " + isAdmin + "\n" +
                "📅 Дата регистрации: " + registeredAt + "\n" +
                "🔐 Сессия: " + sessionActive + "\n" +
                "⏱ Осталось: " + sessionTime + "\n" +
                "🌐 IP: " + ip + "\n" +
                SEPARATOR;

        sendFormattedResponse(chatId, response);
    }

    // ===== ПАГИНАЦИЯ =====
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
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

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

    private void sendFormattedResponse(long chatId, String text) {
        String message = "[БОТ] Ответ сервера:\n" + SEPARATOR + "\n" + text + "\n" + SEPARATOR;
        sendMessage(chatId, message);
    }

    // ===== ИГРОВЫЕ КОМАНДЫ =====

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
                "📝 Регистрация:\n" +
                "/reg <ник> <код> — зарегистрировать аккаунт\n\n" +
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
                "📝 Регистрация:\n" +
                "1️⃣ Зайди на сервер — получишь код в чате\n" +
                "2️⃣ Отправь боту: /reg <ник> <код>\n" +
                "3️⃣ Готово! Теперь ты зарегистрирован.\n\n" +
                "🔐 При каждом входе бот пришлёт кнопки для подтверждения.\n" +
                SEPARATOR + "\n" +
                "💡 Команды: !online, !tps, !me, !help";
        sendMessage(chatId, welcome);
    }
}
