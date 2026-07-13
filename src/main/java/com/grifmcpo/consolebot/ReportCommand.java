package com.grifmcpo.reports;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ReportCommand implements CommandExecutor {

    private final ReportsPlugin plugin;

    public ReportCommand(ReportsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        // ===== ОБЪЯВЛЕНИЯ (/bc, /bcast) =====
        if (cmd.equals("bc") || cmd.equals("bcast")) {
            return handleBroadcast(sender, args, false);
        }

        // ===== ЖАЛОБЫ =====
        if (cmd.equals("report")) {
            return handleReport(sender, args);
        } else if (cmd.equals("reports")) {
            return handleReports(sender, args);
        } else if (cmd.equals("reporttake")) {
            return handleReportTake(sender, args);
        } else if (cmd.equals("reportclose")) {
            return handleReportClose(sender, args);
        }
        return false;
    }

    // ============================================
    // ==== ОБЪЯВЛЕНИЯ =====
    // ============================================

    private boolean handleBroadcast(CommandSender sender, String[] args, boolean isRcon) {
        if (args.length < 1) {
            sender.sendMessage("§cИспользуй: /bc <сообщение>");
            return true;
        }

        String message = String.join(" ", args);
        String senderName = sender.getName();

        // Если это игрок — проверяем мут
        if (sender instanceof Player) {
            Player player = (Player) sender;
            // Здесь можно добавить проверку на мут
            // if (isMuted(player.getName())) { ... }
        }

        String displayName = isRcon ? "§6RCON@" + senderName : "§6" + senderName;
        String formatted = "§6[Объявление] §f" + message + " §7(Пишет: " + displayName + "§7)";
        Bukkit.broadcastMessage(formatted);

        sender.sendMessage("§a✅ Объявление отправлено!");
        return true;
    }

    // ============================================
    // ==== ЖАЛОБЫ =====
    // ============================================

    private boolean handleReport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда только для игроков!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cИспользуй: /report <ник> <причина>");
            return true;
        }

        Player player = (Player) sender;
        String target = args[0];
        String reason = String.join(" ", args).substring(target.length() + 1);

        if (Bukkit.getPlayerExact(target) == null) {
            sender.sendMessage("§cИгрок " + target + " не найден!");
            return true;
        }

        if (player.getName().equalsIgnoreCase(target)) {
            sender.sendMessage("§cНельзя жаловаться на самого себя!");
            return true;
        }

        int id = plugin.getReportManager().createReport(player.getName(), target, reason);
        sender.sendMessage("§a✅ Жалоба #" + id + " отправлена на игрока " + target + "!");
        sender.sendMessage("§7Причина: " + reason);

        plugin.getTelegramNotifier().notifyNewReport(id, player.getName(), target, reason);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("reports.admin") || p.isOp()) {
                p.sendMessage("§6[Жалобы] §fНовая жалоба #" + id + " от " + player.getName() + " на " + target);
                p.sendMessage("§7/reports для просмотра");
            }
        }

        return true;
    }

    private boolean handleReports(CommandSender sender, String[] args) {
        if (!sender.hasPermission("reports.admin")) {
            sender.sendMessage("§cУ вас нет прав!");
            return true;
        }

        List<ReportManager.Report> reports = plugin.getReportManager().getAllReports();

        if (reports.isEmpty()) {
            sender.sendMessage("§eЖалоб нет.");
            return true;
        }

        sender.sendMessage("§6=== ЖАЛОБЫ (" + reports.size() + ") ===");
        for (ReportManager.Report report : reports) {
            String statusColor = report.getStatusColor();
            String status = report.getStatusName();
            sender.sendMessage("§f#" + report.getId() + " §7" + report.getReporter() + " → " + report.getTarget() +
                    " §7| " + statusColor + status +
                    " §8| " + report.getReason());
        }

        sender.sendMessage("§7/report take <ID> — взять в работу");
        sender.sendMessage("§7/report close <ID> <причина> — закрыть");
        return true;
    }

    private boolean handleReportTake(CommandSender sender, String[] args) {
        if (!sender.hasPermission("reports.admin")) {
            sender.sendMessage("§cУ вас нет прав!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cИспользуй: /report take <ID>");
            return true;
        }

        int id;
        try {
            id = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверный ID!");
            return true;
        }

        if (plugin.getReportManager().takeReport(id, sender.getName())) {
            sender.sendMessage("§a✅ Жалоба #" + id + " взята в работу!");
        } else {
            sender.sendMessage("§c❌ Жалоба #" + id + " не найдена или уже в работе.");
        }
        return true;
    }

    private boolean handleReportClose(CommandSender sender, String[] args) {
        if (!sender.hasPermission("reports.admin")) {
            sender.sendMessage("§cУ вас нет прав!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cИспользуй: /report close <ID> <причина>");
            return true;
        }

        int id;
        try {
            id = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверный ID!");
            return true;
        }

        String reason = String.join(" ", args).substring(args[0].length() + 1);

        if (plugin.getReportManager().closeReport(id, sender.getName(), reason)) {
            sender.sendMessage("§a✅ Жалоба #" + id + " закрыта!");
            sender.sendMessage("§7Причина: " + reason);
            plugin.getTelegramNotifier().notifyReportClosed(id, sender.getName(), reason);
        } else {
            sender.sendMessage("§c❌ Жалоба #" + id + " не найдена или уже закрыта.");
        }
        return true;
    }
}
