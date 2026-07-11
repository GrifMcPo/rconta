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

    public SendMessage getAuthButtons(String playerName, String code) {
        SendMessage message = new SendMessage();
        message.setChatId(playerManager.getTelegramId(playerName));
        message.setText("🔐 **Подтверждение входа**\n\n" +
                "Игрок **" + playerName + "** пытается войти на сервер.\n" +
                "Разрешить вход?");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton allowBtn = new InlineKeyboardButton();
        allowBtn.setText("✅ Разрешить");
        allowBtn.setCallbackData("auth_allow_" + playerName);

        InlineKeyboardButton denyBtn = new InlineKeyboardButton();
        denyBtn.setText("❌ Запроетить");
        denyBtn.setCallbackData("auth_deny_" + playerName);

        rows.add(List.of(allowBtn, denyBtn));
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
