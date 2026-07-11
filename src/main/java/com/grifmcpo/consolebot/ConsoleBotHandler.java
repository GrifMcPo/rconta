package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class ConsoleBotHandler extends TelegramLongPollingBot {

    private final String botToken;

    public ConsoleBotHandler(String token) {
        this.botToken = token;
    }

    @Override
    public String getBotUsername() {
        return "ConsoleBot";
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Проверяем, что это сообщение и оно не пустое
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            // Проверяем, что команда начинается с "/rcon"
            if (messageText.startsWith("/rcon ")) {
                // Извлекаем команду (убираем "/rcon ")
                String command = messageText.substring(6).trim();

                if (command.isEmpty()) {
                    sendMessage(chatId, "❌ Введите команду после /rcon");
                    return;
                }

                // Выполняем команду в консоли сервера
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                sendMessage(chatId, "✅ Команда выполнена:\n" + command);
            } else {
                sendMessage(chatId, "ℹ️ Используйте /rcon <команда>");
            }
        }
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
