package com.grifmcpo.consolebot;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.text.SimpleDateFormat;
import java.util.List;

public class LogsCommand {

    private final TelegramConsoleBot plugin;

    public LogsCommand(TelegramConsoleBot plugin) {
        this.plugin = plugin;
    }

    public SendMessage handleLogs(long chatId, String[] args) {
        if (args.length < 2) {
            SendMessage msg = new SendMessage();
            msg.setChatId(String.valueOf(chatId));
            msg.setText("ℹ️ Используйте: !rcon logs <ник> [дней]\nПример: !rcon logs pley1657 30");
            return msg;
        }

        String playerName = args[1];
        int days = 5; // по умолчанию 5 дней

        if (args.length >= 3) {
            try {
                days = Integer.parseInt(args[2]);
                if (days < 1) days = 1;
                if (days > 90) days = 90; // максимум 90 дней
            } catch (NumberFormatException e) {
                // игнорируем
            }
        }

        List<CommandLogger.LogEntry> logs = plugin.getCommandLogger().getLogs(playerName, days);

        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));

        if (logs.isEmpty()) {
            msg.setText("📭 Нет логов для игрока **" + playerName + "** за последние " + days + " дней.");
            return msg;
        }

        StringBuilder response = new StringBuilder();
        response.append("📋 **Логи команд для ").append(playerName).append("**\n");
        response.append("📅 За последние ").append(days).append(" дней\n");
        response.append("━━━━━━━━━━━━━━━━━━━━\n");

        int count = 0;
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm");

        for (CommandLogger.LogEntry entry : logs) {
            if (count >= 50) {
                response.append("\n... и еще ").append(logs.size() - 50).append(" записей");
                break;
            }
            String time = entry.timestamp.length() > 16 ? entry.timestamp.substring(5, 16) : entry.timestamp;
            response.append("`").append(time).append("` → `").append(entry.command).append("`\n");
            count++;
        }

        response.append("━━━━━━━━━━━━━━━━━━━━\n");
        response.append("📊 Всего записей: ").append(logs.size());

        msg.setText(response.toString());
        return msg;
    }
}
