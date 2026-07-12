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
import java.util.UUID;

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

    // Временные сообщения для автоудаления
    private final List<Integer> tempMessages = new ArrayList<>();

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
        // ===== ОБРАБОТКА КНОПОК =====
        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            String chatId = update.getCallbackQuery().getMessage().getChatId().toString();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();

            // --- Кнопки подтверждения входа ---
            if (data.startsWith("auth_allow_")) {
                String playerName = data.substring(11);
                String ip = "0.0.0.0";
                authManager.activateSession(playerName, ip);
                authManager.unfreezePlayer(Bukkit.getPlayerExact(playerName));
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

            // --- Кнопки подтверждения команд (ban/kick/unban) ---
            if (data.startsWith("confirm_")) {
                String[] parts = data.split("_");
                String action = parts[1];
                String playerName = parts[2];
                String duration = parts.length > 3 ? parts[3] : "навсегда";
                String reason = parts.length > 4 ? parts[4] : "Без причины";
                String issuer = update.getCallbackQuery().getFrom().getId().toString();

                // Выполняем команду
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

            // --- Пагинация через кнопки ---
            if (data.startsWith("page_")) {
                String[] parts = data.split("_");
                String type = parts[1];
                String playerName = parts[2];
                int page = Integer.parseInt(parts[3]);
                int chatIdLong = Integer.parseInt(chatId);

                handlePagination(chatIdLong, type, playerName, page, messageId);
                return;
            }

            return;
        }

        // ===== ОБРАБОТКА СООБЩЕНИЙ =====
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

        // --- КОМАНДА !me ---
        if (messageText.equalsIgnoreCase("!me")) {
            sendMe(chatId, userId);
            return;
        }

        // --- /reg в Telegram (регистрация) ---
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

        // --- ОСТАЛЬНЫЕ КОМАНДЫ ---
        // (здесь весь остальной код из предыдущей версии)
        // !online, !tps, !help, !info, !rcon и т.д.

        // --- !rcon с кнопками подтверждения ---
        if (messageText.startsWith("!rcon")) {
            String command = messageText.substring(6).trim();
            if (command.isEmpty()) {
                sendMessage(chatId, "ℹ️ Введи команду после !rcon");
                return;
            }

            if (!plugin.isAdmin(userId) && userId != plugin.getOwnerId()) {
                sendMessage(chatId, "⛔ Доступ запрещён.");
                return;
            }

            // Опасные команды — запрос подтверждения
            String[] dangerous = {"ban ", "mute ", "kick ", "unban ", "unmute "};
            for (String d : dangerous) {
                if (command.startsWith(d)) {
                    sendConfirmationRequest(chatId, command, userId);
                    return;
                }
            }

            // Остальные команды выполняем как обычно
            executeNormalCommand(chatId, command, userId);
        }
    }

    // ===== ОТПРАВКА ЗАПРОСА ПОДТВЕРЖДЕНИЯ =====
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

        InlineKeyboardButton confirmBtn = new InlineKeyboardButton();
        confirmBtn.setText("✅ Да");
        confirmBtn.setCallbackData("confirm_" + action + "_" + playerName + "_" + duration + "_" + reason);

        InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
        cancelBtn.setText("❌ Отмена");
        cancelBtn.setCallbackData("cancel_" + System.currentTimeMillis());

        rows.add(List.of(confirmBtn, cancelBtn));
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

    // ===== ВЫПОЛНЕНИЕ ОБЫЧНОЙ КОМАНДЫ =====
    private void executeNormalCommand(long chatId, String command, long userId) {
        // (тут код выполнения обычных команд)
        // sendMessage(chatId, "[БОТ] Выполняю команду..");
        // ...
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

    // ===== ПАГИНАЦИЯ ЧЕРЕЗ КНОПКИ =====
    private void handlePagination(int chatId, String type, String playerName, int page, int messageId) {
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

        // Обновляем сообщение
        try {
            SendMessage msg = new SendMessage();
            msg.setChatId(String.valueOf(chatId));
            msg.setText("[БОТ] Ответ сервера:\n" + response.toString());
            if (!rows.isEmpty()) {
                msg.setReplyMarkup(markup);
            }
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        // Удаляем старое сообщение
        deleteMessage(String.valueOf(chatId), messageId);
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    private void deleteMessage(String chatId, int messageId) {
        try {
            DeleteMessage delete = new DeleteMessage();
            delete.setChatId(chatId);
            delete.setMessageId(messageId);
            execute(delete);
        } catch (TelegramApiException e) {
            // Игнорируем, если сообщение уже удалено
        }
    }

    private void sendFormattedResponse(long chatId, String text) {
        String message = "[БОТ] Ответ сервера:\n" + SEPARATOR + "\n" + text + "\n" + SEPARATOR;
        sendMessage(chatId, message);
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
