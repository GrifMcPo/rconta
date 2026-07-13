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
        // ==== БЛОКИРУЕМ КОМАНДЫ FLECTONEPULSE =====
        // ============================================
        String[] blocked = {"/ban", "/tempban", "/unban", "/mute", "/tempmute", "/unmute", "/kick", "/warn", "/unwarn", "/jail", "/unjail"};
        for (String b : blocked) {
            if (command.startsWith(b) || command.startsWith("flectonepulse:" + b)) {
                event.setCancelled(true);
                // БЛОКИРУЕМ, НАШИ КОМАНДЫ ОТРАБОТАЮТ НИЖЕ
                return;
            }
        }

        // ============================================
        // ==== /report <ник> <причина> =====
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
        // ==== /bc / /bcast =====
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
        // ==== КАСТОМНЫЕ БАНЫ (НАШИ!) =====
        // ============================================

        // --- /ban ---
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
                player.sendMessage("§cИгрок " + target + " не найден!");
            }
            return;
        }

        // --- /banlist ---
        if (command.equalsIgnoreCase("/banlist") || command.startsWith("/banlist ")) {
            event.setCancelled(true);
            int page = 1;
            String[] parts = command.split(" ");
            if (parts.length > 1) {
                try { page = Integer.parseInt(parts[1]); } catch (NumberFormatException e) {}
            }
            int pageSize = 10;
            List<String> allBans = punishmentManager.getBanList(1, Integer.MAX_VALUE);
            List<String> bans = punishmentManager.getBanList(page, pageSize);
            int totalPages = (int) Math.ceil((double) allBans.size() / pageSize);

            if (bans.isEmpty()) {
                player.sendMessage("§eБанов нет.");
            } else {
                player.sendMessage("§6=== Список банов (Страница " + page + "/" + totalPages + ") ===");
                for (String ban : bans) {
                    player.sendMessage("§f" + ban);
                }
                if (totalPages > 1) {
                    player.sendMessage("§7Используй: /banlist " + (page + 1));
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
                try { page = Integer.parseInt(parts[1]); } catch (NumberFormatException e) {}
            }
            int pageSize = 10;
            List<String> allMutes = punishmentManager.getMuteList(1, Integer.MAX_VALUE);
            List<String> mutes = punishmentManager.getMuteList(page, pageSize);
            int totalPages = (int) Math.ceil((double) allMutes.size() / pageSize);

            if (mutes.isEmpty()) {
                player.sendMessage("§eМутов нет.");
            } else {
                player.sendMessage("§6=== Список мутов (Страница " + page + "/" + totalPages + ") ===");
                for (String mute : mutes) {
                    player.sendMessage("§f" + mute);
                }
                if (totalPages > 1) {
                    player.sendMessage("§7Используй: /mutelist " + (page + 1));
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
                try { page = Integer.parseInt(parts[2]); } catch (NumberFormatException e) {}
            }
            int pageSize = 10;

            List<PunishmentManager.HistoryEntry> allHistory = punishmentManager.getHistory(target);
            List<String> formattedHistory = new ArrayList<>();
            for (PunishmentManager.HistoryEntry entry : allHistory) {
                String timeAgo = punishmentManager.getTimeAgo(entry.timestamp);
                String status = entry.type.equals("ban") ? (punishmentManager.isBanned(target) ? "[Активен]" : "[Истек]") :
                               (punishmentManager.isMuted(target) ? "[Активен]" : "[Истек]");
                formattedHistory.add(" - " + timeAgo + " -\n   " + target + " был " + entry.getActionName() +
                        " на " + entry.duration + " " + entry.issuer + ": " + entry.reason + " " + status);
            }

            List<String> pageItems = paginate(formattedHistory, page, pageSize);
            int totalPages = (int) Math.ceil((double) formattedHistory.size() / pageSize);

            if (pageItems.isEmpty()) {
                player.sendMessage("§eИстория для " + target + " пуста.");
            } else {
                player.sendMessage("§6=== История для " + target + " (Стр. " + page + "/" + totalPages + ") ===");
                for (String entry : pageItems) {
                    player.sendMessage("§f" + entry);
                }
                if (totalPages > 1) {
                    player.sendMessage("§7/shist " + target + " " + (page + 1));
                }
                player.sendMessage("§7Всего: " + formattedHistory.size());
            }
            return;
        }
    }

    // ============================================
    // ==== БЛОКИРОВКА ЧАТА ПРИ МУТЕ =====
    // ============================================
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

    // ============================================
    // ==== ПРОВЕРКА ПРИ ВХОДЕ =====
    // ============================================
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        punishmentManager.checkOnJoin(player);
    }

    // ============================================
    // ==== ПАГИНАЦИЯ =====
    // ============================================
    private List<String> paginate(List<String> items, int page, int pageSize) {
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, items.size());
        if (start >= items.size() || start < 0) return new ArrayList<>();
        return items.subList(start, end);
    }
}
