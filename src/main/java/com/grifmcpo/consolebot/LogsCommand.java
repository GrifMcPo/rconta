package com.grifmcpo.consolebot;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

public class LogsCommand {

    private final TelegramConsoleBot plugin;

    public LogsCommand(TelegramConsoleBot plugin) {
        this.plugin = plugin;
    }

    public SendMessage handleLogs(long chatId, String[] args) {
        if (args.length < 2) {
            SendMessage msg = new SendMessage();
            msg.setChatId(String.valueOf(chatId));
            msg.setText("ℹ️ Используй: !rcon logs <ник> [дней]\nПример: !rcon logs pley1657 30");
            return msg;
        }

        String playerName = args[1];
        int days = 5;

        if (args.length >= 3) {
            try {
                days = Integer.parseInt(args[2]);
                if (days < 1) days = 1;
                if (days > 90) days = 90;
            } catch (NumberFormatException e) {
                // игнорируем
            }
        }

        List<CommandLogger.LogEntry> logs = plugin.getCommandLogger().getLogs(playerName, days);

        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));

        if (logs.isEmpty()) {
            msg.setText("📭 Нет логов для " + playerName + " за последние " + days + " дн.");
            return msg;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));

        StringBuilder response = new StringBuilder();
        response.append("📋 Логи команд для ").append(playerName).append("\n");
        response.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        int count = 0;
        for (CommandLogger.LogEntry entry : logs) {
            if (count >= 50) {
                response.append("\n... и ещё ").append(logs.size() - 50).append(" записей");
                break;
            }
            String time = entry.timestamp;
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                inputFormat.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
                java.util.Date date = inputFormat.parse(entry.timestamp);
                time = sdf.format(date);
            } catch (Exception e) {
                // оставляем как есть
            }
            response.append("• ").append(time).append("  →  ").append(entry.command).append("\n");
            count++;
        }

        response.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        response.append("📊 Всего записей: ").append(logs.size());

        msg.setText(response.toString());
        return msg;
    }
}
