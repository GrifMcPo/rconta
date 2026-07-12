package com.grifmcpo.consolebot;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        commandLogger.logCommand(player.getName(), event.getMessage());

        // ===== ПРОВЕРКА: ЗАМОРОЖЕН ЛИ ИГРОК =====
        if (authManager.isFrozen(player)) {
            if (!command.startsWith("/code ")) {
                event.setCancelled(true);
                player.sendMessage("§e🔐 Сначала подтвердите вход!");
                player.sendMessage("§e📝 Введите /code <код> или подтвердите в Telegram");
                return;
            }
        }

        // ===== КОМАНДА /code (ввод кода в чате) =====
        if (command.startsWith("/code ")) {
            event.setCancelled(true);
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                player.sendMessage("§cИспользуй: /code <код>");
                return;
            }
            String code = parts[1];
            String playerName = player.getName();
            String ip = player.getAddress().getHostString();

            if (authManager.verifyAuthCode(playerName, code)) {
                if (!authManager.isRegistered(playerName)) {
                    if (authManager.registerPlayer(playerName, "0", ip)) {
                        authManager.activateSession(playerName, ip);
                        authManager.unfreezePlayer(player);
                        player.sendMessage("§a✅ Аккаунт зарегистрирован и вход подтверждён!");
                    } else {
                        player.sendMessage("§c❌ Ошибка регистрации!");
                    }
                } else {
                    authManager.activateSession(playerName, ip);
                    authManager.unfreezePlayer(player);
                    player.sendMessage("§a✅ Вход подтверждён!");
                }
            } else {
                player.sendMessage("§c❌ Неверный код или код истёк. Попробуйте зайти заново.");
            }
            return;
        }

        // ===== ОСТАЛЬНЫЕ КОМАНДЫ =====
        // --- /ban ---
        if (command.startsWith("/ban ")) {
            event.setCancelled(true);
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                player.sendMessage("§cИспользуй: /ban <ник> [время] <причина>");
                player.sendMessage("§7Пример: /ban pley1657 1d читы");
                return;
            }
            String target = parts[1];
            String duration = "навсегда";
            String reason = "";
            int start = 2;

            if (parts.length > 2 && punishmentManager.isValidTime(parts[2])) {
                duration = parts[2];
                start = 3;
            }

            if (parts.length > start) {
                reason = String.join(" ", Arrays.copyOfRange(parts, start, parts.length));
            } else {
                reason = "Без причины";
            }

            if (punishmentManager.banPlayer(target, player.getName(), reason, duration)) {
                player.sendMessage("§aИгрок " + target + " забанен на " + duration);
            } else {
                player.sendMessage("§cИгрок " + target + " уже забанен!");
            }
            return;
        }

        // --- /unban ---
        if (command.startsWith("/unban ")) {
            event.setCancelled(true);
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                player.sendMessage("§cИспользуй: /unban <ник> <причина>");
                return;
            }
            String target = parts[1];
            String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
            if (punishmentManager.unbanPlayer(target, player.getName(), reason)) {
                player.sendMessage("§aИгрок " + target + " разбанен!");
            } else {
                player.sendMessage("§cИгрок " + target + " не забанен!");
            }
            return;
        }

        // --- /mute ---
        if (command.startsWith("/mute ")) {
            event.setCancelled(true);
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                player.sendMessage("§cИспользуй: /mute <ник> [время] <причина>");
                player.sendMessage("§7Пример: /mute pley1657 1m спам");
                return;
            }
            String target = parts[1];
            String duration = "навсегда";
            String reason = "";
            int start = 2;

            if (parts.length > 2 && punishmentManager.isValidTime(parts[2])) {
                duration = parts[2];
                start = 3;
            }

            if (parts.length > start) {
                reason = String.join(" ", Arrays.copyOfRange(parts, start, parts.length));
            } else {
                reason = "Без причины";
            }

            if (punishmentManager.mutePlayer(target, player.getName(), reason, duration)) {
                player.sendMessage("§aИгрок " + target + " замучен на " + duration);
            } else {
                player.sendMessage("§cИгрок " + target + " уже замучен!");
            }
            return;
        }

        // --- /unmute ---
        if (command.startsWith("/unmute ")) {
            event.setCancelled(true);
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                player.sendMessage("§cИспользуй: /unmute <ник> <причина>");
                return;
            }
            String target = parts[1];
            String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
            if (punishmentManager.unmutePlayer(target, player.getName(), reason)) {
                player.sendMessage("§aИгрок " + target + " размучен!");
            } else {
                player.sendMessage("§cИгрок " + target + " не замучен!");
            }
            return;
        }

        // --- /kick ---
        if (command.startsWith("/kick ")) {
            event.setCancelled(true);
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                player.sendMessage("§cИспользуй: /kick <ник> <причина>");
                return;
            }
            String target = parts[1];
            String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));

            if (punishmentManager.kickPlayer(target, player.getName(), reason)) {
                player.sendMessage("§aИгрок " + target + " кикнут!");
            } else {
                player.sendMessage("§cИгрок " + target + " не найден на сервере!");
            }
            return;
        }

        // --- /banlist ---
        if (command.equalsIgnoreCase("/banlist") || command.startsWith("/banlist ")) {
            event.setCancelled(true);
            int page = 1;
            String[] parts = command.split(" ");
            if (parts.length > 1) {
                try { page = Integer.parseInt(parts[1]); if (page < 1) page = 1; } catch (NumberFormatException e) {}
            }
            int pageSize = 10;

            List<String> allBans = punishmentManager.getBanList(1, Integer.MAX_VALUE);
            List<String> bans = punishmentManager.getBanList(page, pageSize);
            int totalPages = punishmentManager.getTotalPages(allBans.size(), pageSize);

            if (bans.isEmpty()) {
                player.sendMessage("§eБанов нет.");
            } else {
                player.sendMessage("§6=== Список банов (Страница " + page + "/" + totalPages + ") ===");
                for (String ban : bans) {
                    player.sendMessage("§f" + ban);
                }
                if (totalPages > 1) {
                    player.sendMessage("§7Используй: /banlist " + (page + 1) + " для следующей страницы");
                }
            }
            return;
        }

        // --- /mutelist ---
        if (command.equalsIgnoreCase("/mutelist") || command.startsWith("/mutelist ")) {
            event.setCancelled(true);
            int page = 1;
            String[] parts = command.split(" ");
            if (parts.length > 1) {
                try { page = Integer.parseInt(parts[1]); if (page < 1) page = 1; } catch (NumberFormatException e) {}
            }
            int pageSize = 10;

            List<String> allMutes = punishmentManager.getMuteList(1, Integer.MAX_VALUE);
            List<String> mutes = punishmentManager.getMuteList(page, pageSize);
            int totalPages = punishmentManager.getTotalPages(allMutes.size(), pageSize);

            if (mutes.isEmpty()) {
                player.sendMessage("§eМутов нет.");
            } else {
                player.sendMessage("§6=== Список мутов (Страница " + page + "/" + totalPages + ") ===");
                for (String mute : mutes) {
                    player.sendMessage("§f" + mute);
                }
                if (totalPages > 1) {
                    player.sendMessage("§7Используй: /mutelist " + (page + 1) + " для следующей страницы");
                }
            }
            return;
        }

        // --- /shist /hist ---
        if (command.startsWith("/shist ") || command.startsWith("/hist ")) {
            event.setCancelled(true);
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                player.sendMessage("§cИспользуй: /shist <ник> [страница]");
                return;
            }
            String target = parts[1];
            int page = 1;
            if (parts.length > 2) {
                try { page = Integer.parseInt(parts[2]); if (page < 1) page = 1; } catch (NumberFormatException e) {}
            }
            int pageSize = 10;

            List<PunishmentManager.HistoryEntry> allHistory = punishmentManager.getHistory(target);
            List<String> formattedHistory = new ArrayList<>();

            for (PunishmentManager.HistoryEntry entry : allHistory) {
                String timeAgo = punishmentManager.getTimeAgo(entry.timestamp);
                String status;
                if (entry.type.equals("ban")) {
                    status = punishmentManager.isBanned(target) ? "[Активен]" : "[Истек]";
                } else if (entry.type.equals("mute")) {
                    status = punishmentManager.isMuted(target) ? "[Активен]" : "[Истек]";
                } else {
                    status = "[Истек]";
                }
                formattedHistory.add(" - " + timeAgo + " -\n   " + target + " был " + entry.getActionName() +
                        " на " + entry.duration + " " +
                        entry.issuer + ": " + entry.reason + " " + status);
            }

            List<String> pageItems = paginate(formattedHistory, page, pageSize);
            int totalPages = (int) Math.ceil((double) formattedHistory.size() / pageSize);

            if (pageItems.isEmpty()) {
                player.sendMessage("§eИстория для " + target + " пуста.");
            } else {
                player.sendMessage("§6=== История наказаний для " + target + " (Страница " + page + "/" + totalPages + ") ===");
                for (String entry : pageItems) {
                    player.sendMessage("§f" + entry);
                }
                if (totalPages > 1) {
                    player.sendMessage("§7Используй: /shist " + target + " " + (page + 1) + " для следующей страницы");
                }
                player.sendMessage("§7Всего записей: " + formattedHistory.size());
            }
            return;
        }

        // ===== БЛОКИРУЕМ КОМАНДЫ FLECTONEPULSE =====
        String[] blocked = {"/ban", "/tempban", "/unban", "/mute", "/tempmute", "/unmute", "/kick", "/warn", "/unwarn", "/jail", "/unjail"};
        for (String b : blocked) {
            if (command.startsWith(b) || command.startsWith("flectonepulse:" + b)) {
                event.setCancelled(true);
                player.sendMessage("§c⛔ Эта команда отключена!");
                player.sendMessage("§7Используй: /ban, /mute, /kick, /unban, /unmute");
                return;
            }
        }
    }

    // ===== ОБРАБОТКА ВХОДА ИГРОКА (С ОТПРАВКОЙ КНОПОК В TELEGRAM) =====
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        String ip = player.getAddress().getHostString();

        punishmentManager.checkOnJoin(player);
        if (!player.isOnline()) return;

        if (!authManager.isRegistered(playerName)) {
            String code = authManager.generateAuthCode(playerName);

            player.sendMessage("§e🔐 Для регистрации на сервере введите код:");
            player.sendMessage("§e📝 Код: §b" + code);
            player.sendMessage("§7Напишите боту в Telegram: /reg " + playerName + " " + code);
            player.sendMessage("§7Или введите в чате: /code " + code);

            authManager.freezePlayer(player);
            return;
        }

        String telegramId = authManager.getTelegramId(playerName);
        if (telegramId == null || telegramId.isEmpty() || telegramId.equals("0")) {
            player.kickPlayer("§cОшибка: Telegram ID не найден. Обратитесь к администратору.");
            return;
        }

        if (!authManager.validateSession(playerName, ip)) {
            player.sendMessage("§e🔐 Требуется подтверждение входа!");
            player.sendMessage("§7Проверьте Telegram-бота для подтверждения.");

            authManager.freezePlayer(player);

            // Отправляем кнопки в Telegram
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    sendTelegramAuthButtons(telegramId, playerName, ip);
                    player.sendMessage("§a📱 Запрос подтверждения отправлен в Telegram!");
                } catch (Exception e) {
                    player.sendMessage("§c❌ Ошибка отправки запроса в Telegram.");
                }
            });
        } else {
            player.sendMessage("§a✅ Добро пожаловать на сервер!");
            authManager.updateIP(playerName, ip);
        }
    }

    // ===== ОТПРАВКА КНОПОК В TELEGRAM =====
    private void sendTelegramAuthButtons(String telegramId, String playerName, String ip) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(telegramId);
            message.setText("🔐 Подтверди вход на сервер:\n" +
                    "👤 Игрок: " + playerName + "\n" +
                    "🌐 IP: " + ip + "\n\n" +
                    "Разрешить вход?");

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
            message.setReplyMarkup(markup);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    // Отправляем через зарегистрированного бота
                    // Используем TelegramBotHandler, который уже зарегистрирован
                    // Временно используем заглушку
                    // В реальном коде нужно передать экземпляр бота
                } catch (Exception e) {
                    plugin.getLogger().warning("❌ Ошибка отправки кнопок в Telegram: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("❌ Ошибка создания кнопок: " + e.getMessage());
        }
    }

    // ===== БЛОКИРОВКА ЧАТА ПРИ МУТЕ =====
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (authManager.isFrozen(player)) {
            event.setCancelled(true);
            player.sendMessage("§e🔐 Сначала подтвердите вход! Используйте /code <код>");
            return;
        }

        if (!punishmentManager.canPlayerChat(player)) {
            event.setCancelled(true);
            String issuer = punishmentManager.getMuteIssuer(player.getName());
            String reason = punishmentManager.getMuteReason(player.getName());
            player.sendMessage("§cВы замучены!");
            player.sendMessage("§fПричина: §e" + reason);
            player.sendMessage("§fВыдал: §e" + issuer);
        }
    }

    // ===== ВСПОМОГАТЕЛЬНЫЙ МЕТОД ПАГИНАЦИИ =====
    private List<String> paginate(List<String> items, int page, int pageSize) {
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, items.size());
        if (start >= items.size() || start < 0) return new ArrayList<>();
        return items.subList(start, end);
    }
}
