package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
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

public class CommandListener implements Listener {

    private final CommandLogger commandLogger;
    private final PunishmentManager punishmentManager;

    // Список команд Essentials, которые мы блокируем
    private final String[] ESSENTIALS_COMMANDS = {
        "/balance", "/bal", "/money", "/pay", "/baltop",
        "/kit", "/kits", "/warp", "/warps", "/spawn",
        "/sethome", "/home", "/delhome", "/homes",
        "/msg", "/tell", "/w", "/reply", "/r",
        "/tpa", "/tpaccept", "/tpdeny", "/tphere",
        "/god", "/fly", "/heal", "/feed", "/repair",
        "/vanish", "/v", "/nick", "/realname",
        "/back", "/tp", "/teleport", "/tppos",
        "/mute", "/unmute", "/ban", "/unban", "/kick"
    };

    public CommandListener(CommandLogger commandLogger, PunishmentManager punishmentManager) {
        this.commandLogger = commandLogger;
        this.punishmentManager = punishmentManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        commandLogger.logCommand(player.getName(), event.getMessage());

        // ============================================
        // ==== БЛОКИРУЕМ ВСЕ КОМАНДЫ ESSENTIALS =====
        // ============================================
        for (String cmd : ESSENTIALS_COMMANDS) {
            if (command.startsWith(cmd)) {
                event.setCancelled(true);
                // Наши команды отработают ниже
                return;
            }
        }

        // ============================================
        // ==== /bc / /bcast (НАШИ ОБЪЯВЛЕНИЯ) =====
        // ============================================
        if (command.startsWith("/bc ") || command.startsWith("/bcast ")) {
            event.setCancelled(true);
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                player.sendMessage("§cИспользуй: /bc <сообщение>");
                return;
            }
            String message = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
            if (punishmentManager.isMuted(player.getName())) {
                player.sendMessage("§cВы замучены!");
                return;
            }
            String format = "§6[Объявление] §f" + message + " §7(Пишет: " + player.getName() + "§7)";
            Bukkit.broadcastMessage(format);
            player.sendMessage("§a✅ Объявление отправлено!");
            return;
        }

        // ============================================
        // ==== /report =====
        // ============================================
        if (command.startsWith("/report ")) {
            event.setCancelled(true);
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                player.sendMessage("§cИспользуй: /report <ник> <причина>");
                return;
            }
            String target = parts[1];
            String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));

            if (Bukkit.getPlayerExact(target) == null) {
                player.sendMessage("§cИгрок " + target + " не найден!");
                return;
            }
            if (player.getName().equalsIgnoreCase(target)) {
                player.sendMessage("§cНельзя жаловаться на себя!");
                return;
            }
            if (punishmentManager.isMuted(player.getName())) {
                player.sendMessage("§cВы замучены!");
                return;
            }

            player.sendMessage("§a✅ Жалоба на " + target + " отправлена!");
            player.sendMessage("§7Причина: " + reason);

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("telegramconsolebot.admin") || p.isOp()) {
                    p.sendMessage("§6[Жалоба] §f" + player.getName() + " → §c" + target + " §7: §e" + reason);
                }
            }
            return;
        }

        // ============================================
        // ==== /ban =====
        // ============================================
        if (command.startsWith("/ban ")) {
            event.setCancelled(true);
            String[] parts = command.split(" ");
            if (parts.length < 3) {
                player.sendMessage("§cИспользуй: /ban <ник> [время] <причина>");
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

            if (Bukkit.getPlayerExact(target) == null) {
                player.sendMessage("§cИгрок " + target + " не найден!");
                return;
            }

            if (punishmentManager.banPlayer(target, player.getName(), reason, duration)) {
                player.sendMessage("§a✅ Игрок " + target + " забанен на " + duration);
            } else {
                player.sendMessage("§c❌ Игрок " + target + " уже забанен!");
            }
            return;
        }

        // ... ВСЕ ОСТАЛЬНЫЕ КОМАНДЫ (unban, mute, unmute, kick, banlist, mutelist, shist)
        // ОСТАЮТСЯ БЕЗ ИЗМЕНЕНИЙ
    }

    // ... ОСТАЛЬНЫЕ МЕТОДЫ (onPlayerChat, onPlayerJoin, paginate)
}
