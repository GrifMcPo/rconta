package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

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

        // --- !rcon shist / hist ---
        if (command.startsWith("shist ") || command.startsWith("hist ")) {
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                sendMessage(chatId, "❌ Используй: !rcon shist <ник>");
                return;
            }
            String playerName = parts[1];
            handleHistory(chatId, playerName);
            return;
        }

        // --- !rcon banlist ---
        if (command.equalsIgnoreCase("banlist")) {
            handleBanList(chatId);
            return;
        }

        // --- !rcon mutelist ---
        if (command.equalsIgnoreCase("mutelist")) {
            handleMuteList(chatId);
            return;
        }

        // --- !rcon staff ---
        if (command.equalsIgnoreCase("staff")) {
            handleStaffList(chatId);
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

        // --- ВЫПОЛНЕНИЕ КОМАНДЫ С ОТВЕТОМ ---
        final String finalCommand = command;

        sendMessage(chatId, "[БОТ] Выполняю команду..");

        Bukkit.getScheduler().runTask(plugin, () -> {
            String response = commandExecutor.executeCommand(finalCommand, "RCON");
            String formatted = formatResponse(response);
            sendMessage(chatId, "[БОТ] Ответ сервера:\n" + formatted);
        });
    }

    // --- ФОРМАТИРОВАНИЕ ОТВЕТА ---
    private String formatResponse(String response) {
        if (response == null || response.isEmpty()) {
            return "✅ Команда выполнена (ответа нет)";
        }
        String clean = response.trim();
        if (clean.length() > 4000) {
            clean = clean.substring(0, 3900) + "\n... (сообщение обрезано)";
        }
        return clean;
    }

    // --- ИСТОРИЯ НАКАЗАНИЙ ---
    private void handleHistory(long chatId, String playerName) {
        if (!playerManager.isRegistered(playerName)) {
            sendMessage(chatId, "❌ Игрок " + playerName + " не зарегистрирован в системе.");
            return;
        }

        List<CommandLogger.LogEntry> logs = commandLogger.getLogs(playerName, 30);
        List<String> punishments = new ArrayList<>();
        String[] punishCommands = {"ban", "mute", "warn", "kick", "jail", "tempban", "tempmute"};
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));

        for (CommandLogger.LogEntry entry : logs) {
            String cmd = entry.command.toLowerCase();
            for (String pCmd : punishCommands) {
                if (cmd.startsWith("/" + pCmd) || cmd.startsWith(pCmd)) {
                    punishments.add("• " + sdf.format(new Date()) + " → " + entry.command);
                    break;
                }
            }
        }

        if (punishments.isEmpty()) {
            sendMessage(chatId, "📋 История наказаний для " + playerName + ":\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n✅ Наказаний не найдено.");
            return;
        }

        StringBuilder response = new StringBuilder();
        response.append("📋 История наказаний для ").append(playerName).append("\n");
        response.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        int count = 0;
        for (String p : punishments) {
            if (count >= 20) {
                response.append("\n... и ещё ").append(punishments.size() - 20).append(" записей");
                break;
            }
            response.append(p).append("\n");
            count++;
        }
        response.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        response.append("📊 Всего записей: ").append(punishments.size());

        sendMessage(chatId, response.toString());
    }

    // --- СПИСОК БАНОВ ---
    private void handleBanList(long chatId) {
        sendMessage(chatId, "[БОТ] Выполняю команду..");
        Bukkit.getScheduler().runTask(plugin, () -> {
            String response = commandExecutor.executeCommand("banlist", "RCON");
            sendMessage(chatId, "[БОТ] Ответ сервера:\n" + formatResponse(response));
        });
    }

    // --- СПИСОК МУТОВ ---
    private void handleMuteList(long chatId) {
        sendMessage(chatId, "[БОТ] Выполняю команду..");
        Bukkit.getScheduler().runTask(plugin, () -> {
            String response = commandExecutor.executeCommand("mutelist", "RCON");
            sendMessage(chatId, "[БОТ] Ответ сервера:\n" + formatResponse(response));
        });
    }

    // --- СПИСОК АДМИНИСТРАЦИИ ---
    private void handleStaffList(long chatId) {
        StringBuilder response = new StringBuilder();
        response.append("👑 Администрация онлайн:\n");
        response.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        boolean hasStaff = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("staff") || player.isOp()) {
                response.append("• ").append(player.getName()).append("\n");
                hasStaff = true;
            }
        }
        if (!hasStaff) {
            response.append("❌ Администрации онлайн нет.");
        }
        sendMessage(chatId, response.toString());
    }

    // --- ПРИВЕТСТВИЕ ---
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
