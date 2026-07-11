package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class TelegramBotHandler extends TelegramLongPollingBot {

    private final String botToken;
    private final JavaPlugin plugin;

    // Конструктор для передачи токена и ссылки на плагин
    public TelegramBotHandler(String token, JavaPlugin plugin) {
        this.botToken = token;
        this.plugin = plugin;
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
        // Проверяем, что это текстовое сообщение
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            // Обработка команды /rcon
            if (messageText.startsWith("/rcon ")) {
                String command = messageText.substring(6).trim();
                if (command.isEmpty()) {
                    sendMessage(chatId, "❌ Введите команду после /rcon");
                    return;
                }

                // Отправляем подтверждение в Telegram до выполнения команды
                sendMessage(chatId, "✅ Команда выполняется: " + command);

                // Выполняем команду в главном потоке сервера (синхронно)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    if (!success) {
                        sendMessage(chatId, "❌ Команда не выполнена или не найдена.");
                    }
                });

            } else {
                sendMessage(chatId, "ℹ️ Используйте /rcon <команда>");
            }
        }
    }

    // Метод для отправки сообщения в Telegram
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
