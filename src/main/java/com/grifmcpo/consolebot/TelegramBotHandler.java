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
    private final PunishmentManager punishmentManager;

    public TelegramBotHandler(String token, TelegramConsoleBot plugin, PlayerManager playerManager,
                              CommandLogger commandLogger, LogsCommand logsCommand,
                              CommandExecutor commandExecutor, PunishmentManager punishmentManager) {
        this.botToken = token;
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.commandLogger = commandLogger;
        this.logsCommand = logsCommand;
        this.commandExecutor = commandExecutor;
        this.punishmentManager = punishmentManager;
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

        // --- КОМАНДА /reg ---
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

            if (punishmentManager.banPlayer(playerName, issuer, reason, duration)) {
                sendMessage(chatId, "✅ " + playerName + " забанен на " + duration);
            } else {
                sendMessage(chatId, "❌ " + playerName + " уже забанен!");
            }
            return;
        }

        // ==== !rcon unban (С ПРИЧИНОЙ) ====
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

            if (punishmentManager.unbanPlayer(playerName, issuer, reason)) {
                sendMessage(chatId, "✅ " + playerName + " разбанен! Причина: " + reason);
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

            if (punishmentManager.mutePlayer(playerName, issuer, reason, duration)) {
                sendMessage(chatId, "✅ " + playerName + " замучен на " + duration);
            } else {
                sendMessage(chatId, "❌ " + playerName + " уже замучен!");
            }
            return;
        }

        // ==== !rcon unmute (С ПРИЧИНОЙ) ====
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

            if (punishmentManager.unmutePlayer(playerName, issuer, reason)) {
                sendMessage(chatId, "✅ " + playerName + " размучен! Причина: " + reason);
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

            if (punishmentManager.kickPlayer(playerName, issuer, reason)) {
                sendMessage(chatId, "✅ " + playerName + " кикнут!");
            } else {
                sendMessage(chatId, "❌ " + playerName + " не найден на сервере!");
            }
            return;
        }

        // --- !rcon banlist ---
        if (command.equalsIgnoreCase("banlist")) {
            List<String> bans = punishmentManager.getBanList();
            if (bans.isEmpty()) {
                sendMessage(chatId, "📋 Список банов:\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n✅ Банов нет.");
            } else {
                StringBuilder response = new StringBuilder();
                response.append("📋 Список банов (Всего: ").append(bans.size()).append(")\n");
                response.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                for (String entry : bans) {
                    response.append(entry).append("\n");
                }
                sendMessage(chatId, response.toString());
            }
            return;
        }

        // --- !rcon mutelist ---
        if (command.equalsIgnoreCase("mutelist")) {
            List<String> mutes = punishmentManager.getMuteList();
            if (mutes.isEmpty()) {
                sendMessage(chatId, "📋 Список мутов:\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n✅ Мутов нет.");
            } else {
                StringBuilder response = new StringBuilder();
                response.append("📋 Список мутов (Всего: ").append(mutes.size()).append(")\n");
                response.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                for (String entry : mutes) {
                    response.append(entry).append("\n");
                }
                sendMessage(chatId, response.toString());
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
            String playerName = parts[1];
            List<String> history = punishmentManager.getHistory(playerName);

            if (history.isEmpty()) {
                sendMessage(chatId, "📋 История наказаний для " + playerName + ":\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n✅ Наказаний не найдено.");
            } else {
                StringBuilder response = new StringBuilder();
                response.append("📋 История наказаний для ").append(playerName).append(" (Записей: ").append(history.size()).append(")\n");
                response.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                for (String entry : history) {
                    response.append(entry).append("\n");
                }
                sendMessage(chatId, response.toString());
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
            StringBuilder list = new StringBuilder("📋 Список администраторов:\n━━━━━━━━━━━━━━━━━━━━\n");
            for (String id : plugin.getAdmins().keySet()) {
                list.append("• ").append(id).append(" → ").append(plugin.getAdmins().get(id)).append("\n");
            }
            sendMessage(chatId, list.toString());
            return;
        }

        // --- ВЫПОЛНЕНИЕ ДРУГИХ КОМАНД ---
        final String finalCommand = command;

        sendMessage(chatId, "⏳ Выполняю: " + command);

        Bukkit.getScheduler().runTask(plugin, () -> {
            String response = commandExecutor.executeCommand(finalCommand, "RCON");
            sendMessage(chatId, "📋 Ответ сервера:\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" + response + "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        });
    }

    // --- ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ---
    private void sendOnline(long chatId) {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        StringBuilder players = new StringBuilder();
        for (Player p : Bukkit.getOnlinePlayers()) {
            players.append("• ").append(p.getName()).append("\n");
        }
        String msg = "👥 Онлайн: " + online + "/" + max + "\n\n" + players.toString();
        sendMessage(chatId, msg);
    }

    private void sendTps(long chatId) {
        double tps = Bukkit.getTPS()[0];
        String status = tps >= 20 ? "🟢 Отлично" : tps >= 18 ? "🟡 Хорошо" : tps >= 15 ? "🟠 Средне" : "🔴 Плохо";
        String msg = "⚡ TPS: " + String.format("%.2f", tps) + "\n📊 Статус: " + status;
        sendMessage(chatId, msg);
    }

    private void sendHelp(long chatId) {
        String help = "🤖 Доступные команды:\n\n" +
                "📝 /reg <ник> <пароль> — привязать аккаунт\n" +
                "👥 !online — список игроков онлайн\n" +
                "⚡ !tps — производительность сервера\n" +
                "ℹ️ !info — информация о сервере\n\n" +
                "🔹 Команды наказаний (админы):\n" +
                "!rcon ban <ник> [время] <причина>\n" +
                "!rcon unban <ник> <причина>\n" +
                "!rcon mute <ник> [время] <причина>\n" +
                "!rcon unmute <ник> <причина>\n" +
                "!rcon kick <ник> <причина>\n" +
                "!rcon banlist\n" +
                "!rcon mutelist\n" +
                "!rcon shist <ник>";
        sendMessage(chatId, help);
    }

    private void sendInfo(long chatId) {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        String version = Bukkit.getVersion();
        String msg = "🖥️ Информация о сервере:\n\n" +
                "📌 Версия: " + version + "\n" +
                "👥 Игроки: " + online + "/" + max;
        sendMessage(chatId, msg);
    }

    private void sendWelcome(long chatId) {
        String welcome = "🎮 Добро пожаловать!\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📝 Регистрация:\n" +
                "/reg <ник> <пароль> — привязать аккаунт\n" +
                "Пример: /reg pley1657 mypass123\n\n" +
                "🔐 После регистрации просто заходи на сервер.\n" +
                "Бот запросит подтверждение входа.\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
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
