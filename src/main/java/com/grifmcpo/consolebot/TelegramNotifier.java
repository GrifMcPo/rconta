package com.grifmcpo.consolebot;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TelegramNotifier {

    private final JavaPlugin plugin;
    private String botToken;
    private String chatId;

    public TelegramNotifier(JavaPlugin plugin) {
        this.plugin = plugin;
        this.botToken = plugin.getConfig().getString("telegram.bot_token");
        this.chatId = plugin.getConfig().getString("telegram.chat_id");
    }

    public void notifyNewReport(int id, String reporter, String target, String reason) {
        if (botToken == null || botToken.isEmpty() || chatId == null || chatId.isEmpty()) {
            plugin.getLogger().warning("❌ Telegram не настроен!");
            return;
        }

        String timestamp = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
        String message = "📩 **Новая жалоба #" + id + "**\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👤 **Игрок:** " + reporter + "\n" +
                "🎯 **Нарушитель:** " + target + "\n" +
                "📝 **Причина:** " + reason + "\n" +
                "🕐 **Время:** " + timestamp + "\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📋 Используй: `/reports` в игре";

        sendTelegramMessage(message);
    }

    public void notifyReportClosed(int id, String closer, String reason) {
        String message = "✅ **Жалоба #" + id + " закрыта**\n" +
                "👤 **Закрыл:** " + closer + "\n" +
                "📝 **Причина:** " + reason;

        sendTelegramMessage(message);
    }

    private void sendTelegramMessage(String message) {
        try {
            URL url = new URL("https://api.telegram.org/bot" + botToken + "/sendMessage");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String json = "{\"chat_id\":\"" + chatId + "\",\"text\":\"" + message + "\",\"parse_mode\":\"Markdown\"}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes());
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                plugin.getLogger().warning("❌ Ошибка отправки в Telegram: " + responseCode);
            }

            conn.disconnect();
        } catch (Exception e) {
            plugin.getLogger().warning("❌ Ошибка отправки в Telegram: " + e.getMessage());
        }
    }
}
