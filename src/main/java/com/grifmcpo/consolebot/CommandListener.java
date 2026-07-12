package com.grifmcpo.consolebot;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Arrays;
import java.util.List;

public class CommandListener implements Listener {

    private final CommandLogger commandLogger;
    private final PunishmentManager punishmentManager;

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
        // ==== КОМАНДЫ ДЛЯ ИГРОКОВ (РАБОТАЮТ) =====
        // ============================================

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
            if (parts.length < 2) {
                player.sendMessage("§cИспользуй: /unban <ник>");
                return;
            }
            String target = parts[1];
            if (punishmentManager.unbanPlayer(target, player.getName())) {
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
            if (parts.length < 2) {
                player.sendMessage("§cИспользуй: /unmute <ник>");
                return;
            }
            String target = parts[1];
            if (punishmentManager.unmutePlayer(target, player.getName())) {
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
        if (command.equalsIgnoreCase("/banlist")) {
            event.setCancelled(true);
            List<String> bans = punishmentManager.getBanList();
            if (bans.isEmpty()) {
                player.sendMessage("§eБанов нет.");
            } else {
                player.sendMessage("§6=== Список банов (" + bans.size() + ") ===");
                for (String ban : bans) {
                    player.sendMessage("§f" + ban);
                }
            }
            return;
        }

        // --- /mutelist ---
        if (command.equalsIgnoreCase("/mutelist")) {
            event.setCancelled(true);
            List<String> mutes = punishmentManager.getMuteList();
            if (mutes.isEmpty()) {
                player.sendMessage("§eМутов нет.");
            } else {
                player.sendMessage("§6=== Список мутов (" + mutes.size() + ") ===");
                for (String mute : mutes) {
                    player.sendMessage("§f" + mute);
                }
            }
            return;
        }

        // --- /shist /hist ---
        if (command.startsWith("/shist ") || command.startsWith("/hist ")) {
            event.setCancelled(true);
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                player.sendMessage("§cИспользуй: /shist <ник>");
                return;
            }
            String target = parts[1];
            List<String> history = punishmentManager.getHistory(target);

            if (history.isEmpty()) {
                player.sendMessage("§eИстория для " + target + " пуста.");
            } else {
                player.sendMessage("§6=== История наказаний для " + target + " (" + history.size() + ") ===");
                for (String entry : history) {
                    player.sendMessage("§f" + entry);
                }
            }
            return;
        }

        // ============================================
        // ==== БЛОКИРУЕМ КОМАНДЫ FLECTONEPULSE =====
        // ============================================

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

    // ========================================
    // ==== БЛОКИРОВКА ЧАТА ПРИ МУТЕ =====
    // ========================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!punishmentManager.canPlayerChat(player)) {
            event.setCancelled(true);
            String issuer = punishmentManager.getMuteIssuer(player.getName());
            String reason = punishmentManager.getMuteReason(player.getName());
            player.sendMessage("§cВы замучены!");
            player.sendMessage("§fПричина: §e" + reason);
            player.sendMessage("§fВыдал: §e" + issuer);
        }
    }

    // ========================================
    // ==== ПРОВЕРКА ПРИ ВХОДЕ =====
    // ========================================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        punishmentManager.checkOnJoin(player);
    }
}
