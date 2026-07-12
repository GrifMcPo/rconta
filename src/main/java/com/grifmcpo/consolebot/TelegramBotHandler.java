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

        // --- КОМАНДА /start ---
        if (messageText.equalsIgnoreCase("/start")) {
            sendWelcome(chatId);
            return;
        }

        // --- КОМАНДА /reg в Telegram ---
        if (messageText.startsWith("/reg ") || messageText.startsWith("/register ")) {
            String[] parts = messageText.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: /reg <ник> <код>\nПример: /reg pley1657 123456");
                return;
            }

            String playerName = parts[1];
            String code = parts[2];

            if (authManager.verifyAuthCode(playerName, code)) {
                String ip = "0.0.0.0";
                String tempPassword = UUID.randomUUID().toString().substring(0, 8);

                if (authManager.registerPlayer(playerName, tempPassword, String.valueOf(userId), ip)) {
                    authManager.activateSession(playerName, ip);
                    sendMessage(chatId, "✅ Аккаунт " + playerName + " зарегистрирован и подтверждён!\n" +
                            "🔐 Теперь вы можете заходить на сервер.");

                    Player player = Bukkit.getPlayerExact(playerName);
                    if (player != null && player.isOnline()) {
                        authManager.unfreezePlayer(player);
                        player.sendMessage("§a✅ Аккаунт подтверждён через Telegram!");
                    }
                } else {
                    sendMessage(chatId, "❌ Ошибка регистрации! Возможно ник уже занят.");
                }
            } else {
                sendMessage(chatId, "❌ Неверный код или код истёк. Попробуйте зайти на сервер заново.");
            }
            return;
        }

        // --- КОМАНДА /login в Telegram ---
        if (messageText.startsWith("/login ")) {
            String[] parts = messageText.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: /login <код>");
                return;
            }

            String code = parts[1];
            String playerName = authManager.getPlayerNameByTelegram(String.valueOf(userId));

            if (playerName == null) {
                sendMessage(chatId, "❌ Вы не зарегистрированы. Используйте /reg <ник> <код>");
                return;
            }

            if (authManager.verifyAuthCode(playerName, code)) {
                String ip = "0.0.0.0";
                authManager.activateSession(playerName, ip);
                sendMessage(chatId, "✅ Вход подтверждён для " + playerName + "!");

                Player player = Bukkit.getPlayerExact(playerName);
                if (player != null && player.isOnline()) {
                    authManager.unfreezePlayer(player);
                    player.sendMessage("§a✅ Вход подтверждён через Telegram!");
                }
            } else {
                sendMessage(chatId, "❌ Неверный код или код истёк.");
            }
            return;
        }

        // --- КОМАНДА /reg старый (для совместимости) ---
        if (messageText.startsWith("/reg ") && messageText.split(" ").length >= 3) {
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

        // --- ИГРОВЫЕ КОМАНДЫ (бот отвечает сам) ---
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

        // --- !rcon ban ---
        if (command.startsWith("ban ")) {
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon ban <ник> [время] <причина>\n" +
                        "Пример: !rcon ban pley1657 1d читы");
                return;
            }

            String playerName = parts[1];
            String duration = "навсегда";
            String reason = "";
            int startIndex = 2;

            if (parts.length > 2 && punishmentManager.isValidTime(parts[2])) {
                duration = parts[2];
                startIndex = 3;
            }

            if (parts.length > startIndex) {
                reason = String.join(" ", Arrays.copyOfRange(parts, startIndex, parts.length));
            } else {
                reason = "Без причины";
            }

            String issuer = plugin.getCustomSender(userId);
            if (issuer == null && userId == plugin.getOwnerId()) {
                issuer = "RCON@Grif_Mo";
            }

            sendMessage(chatId, "[БОТ] Выполняю команду..");

            if (punishmentManager.banPlayer(playerName, issuer, reason, duration)) {
                String response = "✅ " + playerName + " забанен на " + duration + "\n" +
                        "👤 Выдал: " + issuer + "\n" +
                        "📝 Причина: " + reason;
                sendFormattedResponse(chatId, response);
            } else {
                sendMessage(chatId, "❌ " + playerName + " уже забанен!");
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
            String playerName = parts[1];
            String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
            String issuer = plugin.getCustomSender(userId);
            if (issuer == null && userId == plugin.getOwnerId()) {
                issuer = "RCON@Grif_Mo";
            }

            sendMessage(chatId, "[БОТ] Выполняю команду..");

            if (punishmentManager.unbanPlayer(playerName, issuer, reason)) {
                String response = "✅ " + playerName + " разбанен!\n" +
                        "👤 Выдал: " + issuer + "\n" +
                        "📝 Причина: " + reason;
                sendFormattedResponse(chatId, response);
            } else {
                sendMessage(chatId, "❌ " + playerName + " не забанен!");
            }
            return;
        }

        // --- !rcon mute ---
        if (command.startsWith("mute ")) {
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon mute <ник> [время] <причина>\n" +
                        "Пример: !rcon mute pley1657 1m спам");
                return;
            }

            String playerName = parts[1];
            String duration = "навсегда";
            String reason = "";
            int startIndex = 2;

            if (parts.length > 2 && punishmentManager.isValidTime(parts[2])) {
                duration = parts[2];
                startIndex = 3;
            }

            if (parts.length > startIndex) {
                reason = String.join(" ", Arrays.copyOfRange(parts, startIndex, parts.length));
            } else {
                reason = "Без причины";
            }

            String issuer = plugin.getCustomSender(userId);
            if (issuer == null && userId == plugin.getOwnerId()) {
                issuer = "RCON@Grif_Mo";
            }

            sendMessage(chatId, "[БОТ] Выполняю команду..");

            if (punishmentManager.mutePlayer(playerName, issuer, reason, duration)) {
                String response = "✅ " + playerName + " замучен на " + duration + "\n" +
                        "👤 Выдал: " + issuer + "\n" +
                        "📝 Причина: " + reason;
                sendFormattedResponse(chatId, response);
            } else {
                sendMessage(chatId, "❌ " + playerName + " уже замучен!");
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
            String playerName = parts[1];
            String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
            String issuer = plugin.getCustomSender(userId);
            if (issuer == null && userId == plugin.getOwnerId()) {
                issuer = "RCON@Grif_Mo";
            }

            sendMessage(chatId, "[БОТ] Выполняю команду..");

            if (punishmentManager.unmutePlayer(playerName, issuer, reason)) {
                String response = "✅ " + playerName + " размучен!\n" +
                        "👤 Выдал: " + issuer + "\n" +
                        "📝 Причина: " + reason;
                sendFormattedResponse(chatId, response);
            } else {
                sendMessage(chatId, "❌ " + playerName + " не замучен!");
            }
            return;
        }

        // --- !rcon kick ---
        if (command.startsWith("kick ")) {
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                sendMessage(chatId, "❌ Используй: !rcon kick <ник> <причина>");
                return;
            }
            String playerName = parts[1];
            String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));

            String issuer = plugin.getCustomSender(userId);
            if (issuer == null && userId == plugin.getOwnerId()) {
                issuer = "RCON@Grif_Mo";
            }

            sendMessage(chatId, "[БОТ] Выполняю команду..");

            if (punishmentManager.kickPlayer(playerName, issuer, reason)) {
                String response = "✅ " + playerName + " кикнут!\n" +
                        "👤 Выдал: " + issuer + "\n" +
                        "📝 Причина: " + reason;
                sendFormattedResponse(chatId, response);
            } else {
                sendMessage(chatId, "❌ " + playerName + " не найден на сервере!");
            }
            return;
        }

        // --- !rcon banlist (с пагинацией) ---
        if (command.equalsIgnoreCase("banlist") || command.startsWith("banlist ")) {
            int page = 1;
            int pageSize = 10;
            String[] parts = command.split(" ");
            if (parts.length > 1) {
                try { page = Integer.parseInt(parts[1]); if (page < 1) page = 1; } catch (NumberFormatException e) {}
            }

            sendMessage(chatId, "[БОТ] Выполняю команду..");

            List<String> allBans = punishmentManager.getBanList(1, Integer.MAX_VALUE);
            List<String> bans = punishmentManager.getBanList(page, pageSize);
            int totalPages = punishmentManager.getTotalPages(allBans.size(), pageSize);

            if (bans.isEmpty()) {
                sendFormattedResponse(chatId, "📋 Список банов пуст.");
            } else {
                StringBuilder response = new StringBuilder();
                response.append("📋 Список банов (Страница ").append(page).append("/").append(totalPages).append(")\n");
                for (String entry : bans) {
                    response.append(entry).append("\n");
                }
                if (totalPages > 1) {
                    response.append("\n📌 Используй: !rcon banlist ").append(page + 1).append(" для следующей страницы");
                }
                sendFormattedResponse(chatId, response.toString());
            }
            return;
        }

        // --- !rcon mutelist (с пагинацией) ---
        if (command.equalsIgnoreCase("mutelist") || command.startsWith("mutelist ")) {
            int page = 1;
            int pageSize = 10;
            String[] parts = command.split(" ");
            if (parts.length > 1) {
                try { page = Integer.parseInt(parts[1]); if (page < 1) page = 1; } catch (NumberFormatException e) {}
            }

            sendMessage(chatId, "[БОТ] Выполняю команду..");

            List<String> allMutes = punishmentManager.getMuteList(1, Integer.MAX_VALUE);
            List<String> mutes = punishmentManager.getMuteList(page, pageSize);
            int totalPages = punishmentManager.getTotalPages(allMutes.size(), pageSize);

            if (mutes.isEmpty()) {
                sendFormattedResponse(chatId, "📋 Список мутов пуст.");
            } else {
                StringBuilder response = new StringBuilder();
                response.append("📋 Список мутов (Страница ").append(page).append("/").append(totalPages).append(")\n");
                for (String entry : mutes) {
                    response.append(entry).append("\n");
                }
                if (totalPages > 1) {
                    response.append("\n📌 Используй: !rcon mutelist ").append(page + 1).append(" для следующей страницы");
                }
                sendFormattedResponse(chatId, response.toString());
            }
            return;
        }

        // --- !rcon shist / hist (с пагинацией) ---
        if (command.startsWith("shist ") || command.startsWith("hist ")) {
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon shist <ник> [страница]");
                return;
            }
            String playerName = parts[1];
            int page = 1;
            int pageSize = 10;
            if (parts.length > 2) {
                try { page = Integer.parseInt(parts[2]); if (page < 1) page = 1; } catch (NumberFormatException e) {}
            }

            sendMessage(chatId, "[БОТ] Выполняю команду..");

            List<PunishmentManager.HistoryEntry> allHistory = punishmentManager.getHistory(playerName);
            List<String> formattedHistory = new ArrayList<>();
            for (PunishmentManager.HistoryEntry entry : allHistory) {
                String timeAgo = punishmentManager.getTimeAgo(entry.timestamp);
                String status;
                if (entry.type.equals("ban")) {
                    status = punishmentManager.isBanned(playerName) ? "[Активен]" : "[Истек]";
                } else if (entry.type.equals("mute")) {
                    status = punishmentManager.isMuted(playerName) ? "[Активен]" : "[Истек]";
                } else {
                    status = "[Истек]";
                }
                formattedHistory.add(" - " + timeAgo + " -\n   " + playerName + " был " + entry.getActionName() +
                        " на " + entry.duration + " " +
                        entry.issuer + ": " + entry.reason + " " + status);
            }

            List<String> pageItems = paginate(formattedHistory, page, pageSize);
            int totalPages = (int) Math.ceil((double) formattedHistory.size() / pageSize);

            if (pageItems.isEmpty()) {
                sendFormattedResponse(chatId, "📋 История наказаний для " + playerName + " пуста.");
            } else {
                StringBuilder response = new StringBuilder();
                response.append("📋 История наказаний для ").append(playerName).append(" (Страница ").append(page).append("/").append(totalPages).append(")\n");
                response.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                for (String entry : pageItems) {
                    response.append(entry).append("\n");
                }
                response.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                response.append("📊 Всего записей: ").append(formattedHistory.size());
                if (totalPages > 1) {
                    response.append("\n📌 Используй: !rcon shist ").append(playerName).append(" ").append(page + 1).append(" для следующей страницы");
                }
                sendFormattedResponse(chatId, response.toString());
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
                sendFormattedResponse(chatId, "✅ Админ с ID " + adminId + " добавлен\n📝 Текст: " + customText);
            } else if (action.equalsIgnoreCase("remove")) {
                if (!plugin.getAdmins().containsKey(adminId)) {
                    sendMessage(chatId, "❌ Админ с ID " + adminId + " не найден.");
                    return;
                }
                plugin.removeAdmin(adminId);
                sendFormattedResponse(chatId, "✅ Админ с ID " + adminId + " удалён.");
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
            sendFormattedResponse(chatId, list.toString());
            return;
        }

        // --- ВЫПОЛНЕНИЕ ДРУГИХ КОМАНД ---
        final String finalCommand = command;

        sendMessage(chatId, "[БОТ] Выполняю команду..");

        Bukkit.getScheduler().runTask(plugin, () -> {
            String response = commandExecutor.executeCommand(finalCommand, "RCON");
            String formatted = "[БОТ] Ответ сервера:\n" + SEPARATOR + "\n" + response + "\n" + SEPARATOR;
            sendMessage(chatId, formatted);
        });
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private void sendFormattedResponse(long chatId, String text) {
        String message = "[БОТ] Ответ сервера:\n" + SEPARATOR + "\n" + text + "\n" + SEPARATOR;
        sendMessage(chatId, message);
    }

    private List<String> paginate(List<String> items, int page, int pageSize) {
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, items.size());
        if (start >= items.size() || start < 0) return new ArrayList<>();
        return items.subList(start, end);
    }

    // --- ИГРОВЫЕ КОМАНДЫ ---
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
                "/reg <ник> <код> — зарегистрировать аккаунт\n" +
                "/login <код> — подтвердить вход\n\n" +
                "👥 !online — список игроков онлайн\n" +
                "⚡ !tps — производительность сервера\n" +
                "ℹ️ !info — информация о сервере\n\n" +
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
                "🔐 При каждом входе потребуется код для подтверждения.\n" +
                SEPARATOR + "\n" +
                "💡 Для помощи напиши /help";
        sendMessage(chatId, welcome);
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
