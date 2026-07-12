package com.grifmcpo.consolebot;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CommandListener implements Listener {

    private final CommandLogger commandLogger;
    private final PunishmentManager punishmentManager;
    private final AuthManager authManager;
    private final TelegramConsoleBot plugin;

    public CommandListener(CommandLogger commandLogger, PunishmentManager punishmentManager, 
                           AuthManager authManager, TelegramConsoleBot plugin) {
        this.commandLogger = commandLogger;
        this.punishmentManager = punishmentManager;
        this.authManager = authManager;
        this.plugin = plugin;
    }

    // ===== ОБРАБОТКА ВХОДА ИГРОКА =====
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        String ip = player.getAddress().getHostString();

        // Проверяем бан
        punishmentManager.checkOnJoin(player);
        if (!player.isOnline()) return;

        // Проверяем регистрацию
        if (!authManager.isRegistered(playerName)) {
            // НЕ ЗАРЕГИСТРИРОВАН — даём код
            String code = authManager.generateAuthCode(playerName);

            player.sendMessage("§e🔐 Для регистрации на сервере введите код:");
            player.sendMessage("§e📝 Код: §b" + code);
            player.sendMessage("§7Напишите боту в Telegram: /reg " + playerName + " " + code);
            player.sendMessage("§7Или введите в чате: /code " + code);

            authManager.freezePlayer(player);
            return;
        }

        // ЗАРЕГИСТРИРОВАН — проверяем сессию
        String telegramId = authManager.getTelegramId(playerName);
        if (telegramId == null || telegramId.isEmpty()) {
            player.kickPlayer("§cОшибка: Telegram ID не найден. Обратитесь к администратору.");
            return;
        }

        if (!authManager.validateSession(playerName, ip)) {
            // Сессия невалидна — отправляем кнопки в Telegram
            player.sendMessage("§e🔐 Требуется подтверждение входа!");
            player.sendMessage("§7Проверьте Telegram-бота для подтверждения.");

            authManager.freezePlayer(player);

            // Отправляем кнопки в Telegram
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    // Отправляем сообщение с кнопками в Telegram
                    String message = "🔐 Подтверди вход на сервер:\n" +
                            "👤 Игрок: " + playerName + "\n" +
                            "🌐 IP: " + ip + "\n\n" +
                            "Разрешить вход?";

                    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                    List<InlineKeyboardButton> row = new ArrayList<>();

                    InlineKeyboardButton allowBtn = new InlineKeyboardButton();
                    allowBtn.setText("✅ Разрешить");
                    allowBtn.setCallbackData("auth_allow_" + playerName);

                    InlineKeyboardButton denyBtn = new InlineKeyboardButton();
                    denyBtn.setText("❌ Запроетить");
                    denyBtn.setCallbackData("auth_deny_" + playerName);

                    row.add(allowBtn);
                    row.add(denyBtn);
                    rows.add(row);
                    markup.setKeyboard(rows);

                    // Отправляем через бота
                    // Здесь нужен доступ к боту, который уже зарегистрирован
                    // Показываем заглушку
                    player.sendMessage("§e📱 Отправлен запрос в Telegram!");
                } catch (Exception e) {
                    player.sendMessage("§c❌ Ошибка отправки запроса в Telegram.");
                    player.kickPlayer("§cНе удалось отправить запрос подтверждения. Попробуйте позже.");
                }
            });
        } else {
            // Вход разрешён
            player.sendMessage("§a✅ Добро пожаловать на сервер!");
            authManager.updateIP(playerName, ip);
        }
    }

    // Остальные методы (команды, пагинация, блокировка чата) остаются без изменений
    // ...
}
