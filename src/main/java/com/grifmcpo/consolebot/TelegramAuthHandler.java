package com.grifmcpo.consolebot;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class TelegramAuthHandler {

    private final PlayerManager playerManager;

    public TelegramAuthHandler(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    /**
     * Создаёт сообщение с кнопками для подтверждения входа
     * @param playerName Имя игрока, который пытается войти
     * @return SendMessage с кнопками "Разрешить" и "Запроетить"
     */
    public SendMessage getAuthButtons(String playerName) {
        String telegramId = playerManager.getTelegramId(playerName);
        if (telegramId == null) {
            return null;
        }

        SendMessage message = new SendMessage();
        message.setChatId(telegramId);
        message.setText("🔐 **Подтверждение входа**\n\n" +
                "Игрок **" + playerName + "** пытается войти на сервер.\n\n" +
                "Разрешить вход?");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Кнопка "Разрешить"
        InlineKeyboardButton allowBtn = new InlineKeyboardButton();
        allowBtn.setText("✅ Разрешить");
        allowBtn.setCallbackData("auth_allow_" + playerName);

        // Кнопка "Запроетить"
        InlineKeyboardButton denyBtn = new InlineKeyboardButton();
        denyBtn.setText("❌ Запроетить");
        denyBtn.setCallbackData("auth_deny_" + playerName);

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(allowBtn);
        row.add(denyBtn);
        rows.add(row);

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);

        return message;
    }

    /**
     * Создаёт сообщение для подтверждения регистрации
     * @param playerName Имя игрока
     * @param password Пароль игрока
     * @return SendMessage с подтверждением
     */
    public SendMessage getRegistrationConfirm(String playerName, String password) {
        String telegramId = playerManager.getTelegramId(playerName);
        if (telegramId == null) {
            return null;
        }

        SendMessage message = new SendMessage();
        message.setChatId(telegramId);
        message.setText("✅ **Аккаунт зарегистрирован!**\n\n" +
                "Игрок: **" + playerName + "**\n" +
                "Пароль: `" + password + "`\n\n" +
                "Теперь вы можете заходить на сервер.\n" +
                "При входе бот запросит подтверждение.");

        return message;
    }

    /**
     * Создаёт сообщение о кике аккаунта
     * @param playerName Имя игрока
     * @return SendMessage с уведомлением
     */
    public SendMessage getKickMessage(String playerName) {
        String telegramId = playerManager.getTelegramId(playerName);
        if (telegramId == null) {
            return null;
        }

        SendMessage message = new SendMessage();
        message.setChatId(telegramId);
        message.setText("⚠️ **Ваш аккаунт был исключен с сервера!**\n\n" +
                "Игрок **" + playerName + "** был кикнут с сервера.\n" +
                "Причина: Аккаунт был исключен через Telegram");

        return message;
    }

    /**
     * Создаёт сообщение об отвязке аккаунта
     * @param playerName Имя игрока
     * @return SendMessage с уведомлением
     */
    public SendMessage getUnregisterMessage(String playerName) {
        String telegramId = playerManager.getTelegramId(playerName);
        if (telegramId == null) {
            return null;
        }

        SendMessage message = new SendMessage();
        message.setChatId(telegramId);
        message.setText("🔓 **Аккаунт отвязан!**\n\n" +
                "Аккаунт **" + playerName + "** успешно отвязан от Telegram.\n\n" +
                "Чтобы привязать новый аккаунт, используйте:\n" +
                "`/reg <ник> <пароль>`");

        return message;
    }

    /**
     * Создаёт сообщение об ошибке входа
     * @param playerName Имя игрока
     * @param reason Причина ошибки
     * @return SendMessage с ошибкой
     */
    public SendMessage getAuthError(String playerName, String reason) {
        String telegramId = playerManager.getTelegramId(playerName);
        if (telegramId == null) {
            return null;
        }

        SendMessage message = new SendMessage();
        message.setChatId(telegramId);
        message.setText("❌ **Ошибка входа!**\n\n" +
                "Игрок **" + playerName + "** попытался войти на сервер.\n" +
                "Причина: " + reason);

        return message;
    }
}
