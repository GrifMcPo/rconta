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

    public CommandListener(CommandLogger commandLogger, PunishmentManager punishmentManager, 
                           AuthManager authManager) {
        this.commandLogger = commandLogger;
        this.punishmentManager = punishmentManager;
        this.authManager = authManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        commandLogger.logCommand(player.getName(), event.getMessage());

        if (authManager.isFrozen(player)) {
            if (!command.startsWith("/code ")) {
                event.setCancelled(true);
                player.sendMessage("§e🔐 Сначала подтвердите вход!");
                player.sendMessage("§e📝 Введите /code <код> или подтвердите в Telegram");
                return;
            }
        }

        // ===== /code =====
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
                    // Игрок не зарегистрирован — регистрируем
                    String telegramId = "0"; // временно, потом через бота привяжет
                    if (authManager.registerPlayer(playerName, telegramId, ip)) {
                        authManager.activateSession(playerName, ip);
                        authManager.unfreezePlayer(player);
                        player.sendMessage("§a✅ Аккаунт зарегистрирован и вход подтверждён!");
                        player.sendMessage("§7Теперь привяжите Telegram: /reg " + playerName + " <код>");
                    } else {
                        player.sendMessage("§c❌ Ошибка регистрации!");
                    }
                } else {
                    authManager.activateSession(playerName, ip);
                    authManager.unfreezePlayer(player);
                    player.sendMessage("§a✅ Вход подтверждён!");
                }
            } else {
                player.sendMessage("§c❌ Неверный код или код истёк.");
            }
            return;
        }

        // ===== ОСТАЛЬНЫЕ КОМАНДЫ (баны, муты и т.д.) =====
        // (весь остальной код остаётся без изменений)
        // ...
    }

    // ===== ОБРАБОТКА ВХОДА ИГРОКА =====
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
            // Нет Telegram ID — даём код для привязки
            String code = authManager.generateAuthCode(playerName);
            player.sendMessage("§e🔐 Привяжите аккаунт к Telegram:");
            player.sendMessage("§e📝 Код: §b" + code);
            player.sendMessage("§7Напишите боту в Telegram: /reg " + playerName + " " + code);
            authManager.freezePlayer(player);
            return;
        }

        if (!authManager.validateSession(playerName, ip)) {
            player.sendMessage("§e🔐 Требуется подтверждение входа!");
            player.sendMessage("§7Проверьте Telegram-бота для подтверждения.");

            authManager.freezePlayer(player);

            // Отправляем кнопки в Telegram
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                authManager.sendAuthButtons(playerName, ip);
                player.sendMessage("§a📱 Запрос подтверждения отправлен в Telegram!");
            });
        } else {
            player.sendMessage("§a✅ Добро пожаловать на сервер!");
            authManager.updateIP(playerName, ip);
        }
    }

    // ===== БЛОКИРОВКА ЧАТА ПРИ МУТЕ =====
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (authManager.isFrozen(player)) {
            event.setCancelled(true);
            player.sendMessage("§e🔐 Сначала подтвердите вход!");
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

    // ===== ВСПОМОГАТЕЛЬНЫЙ МЕТОД =====
    private List<String> paginate(List<String> items, int page, int pageSize) {
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, items.size());
        if (start >= items.size() || start < 0) return new ArrayList<>();
        return items.subList(start, end);
    }
}
