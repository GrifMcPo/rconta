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

        InlineKeyboardButton allowBtn = new InlineKeyboardButton();
        allowBtn.setText("✅ Разрешить");
        allowBtn.setCallbackData("auth_allow_" + playerName);

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

    public String getRegisterMessage(String code) {
        return "🔑 **Код для привязки аккаунта:**\n" +
                "`" + code + "`\n\n" +
                "Отправьте этот код боту командой:\n" +
                "`/register " + code + "`";
    }
}
