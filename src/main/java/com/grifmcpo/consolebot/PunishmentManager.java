package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class PunishmentManager {

    private final JavaPlugin plugin;
    private final AdminLogger adminLogger;
    private File punishmentFile;
    private FileConfiguration punishmentConfig;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    private final Pattern TIME_PATTERN = Pattern.compile("^\\d+[smhdwMy]$");
    private final Pattern CLEAN_PATTERN = Pattern.compile("[^\\p{L}\\p{N}\\s\\p{Punct}]");
    private final String TIMEZONE = "Europe/Moscow";

    public PunishmentManager(JavaPlugin plugin, AdminLogger adminLogger) {
        this.plugin = plugin;
        this.adminLogger = adminLogger;
        dateFormat.setTimeZone(TimeZone.getTimeZone(TIMEZONE));
        loadPunishments();
        startAutoUnmuteTask();
        startAutoUnbanTask();
    }

    private String cleanString(String input) {
        if (input == null) return "Без причины";
        String cleaned = CLEAN_PATTERN.matcher(input).replaceAll("");
        if (cleaned.length() > 200) cleaned = cleaned.substring(0, 200);
        return cleaned.isEmpty() ? "Без причины" : cleaned;
    }

    private void loadPunishments() {
        punishmentFile = new File(plugin.getDataFolder(), "punishments.yml");
        if (!punishmentFile.exists()) {
            try { punishmentFile.createNewFile(); } catch (Exception e) {}
        }
        punishmentConfig = YamlConfiguration.loadConfiguration(punishmentFile);
    }

    public void savePunishments() {
        try { punishmentConfig.save(punishmentFile); } catch (Exception e) {}
    }

    public boolean isValidTime(String time) {
        return TIME_PATTERN.matcher(time).matches();
    }

    public long getTimeInMillis(String time) {
        if (time == null || time.isEmpty()) return -1;
        char unit = time.charAt(time.length() - 1);
        long value = Long.parseLong(time.substring(0, time.length() - 1));
        switch (unit) {
            case 's': return value * 1000;
            case 'm': return value * 60 * 1000;
            case 'h': return value * 60 * 60 * 1000;
            case 'd': return value * 24 * 60 * 60 * 1000;
            case 'w': return value * 7 * 24 * 60 * 60 * 1000;
            case 'M': return value * 30L * 24 * 60 * 60 * 1000;
            case 'y': return value * 365L * 24 * 60 * 60 * 1000;
            default: return -1;
        }
    }

    public boolean isTimeExpired(long timestamp, String duration) {
        if (duration.equals("навсегда")) return false;
        long durationMs = getTimeInMillis(duration);
        if (durationMs == -1) return false;
        return System.currentTimeMillis() - timestamp > durationMs;
    }

    public String formatDate(long timestamp) {
        return dateFormat.format(new Date(timestamp));
    }

    public String getTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long days = diff / (24 * 60 * 60 * 1000);
        long hours = (diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (diff % (60 * 60 * 1000)) / (60 * 1000);
        if (days > 0) return days + " дн. " + hours + " ч. " + minutes + " мин.";
        if (hours > 0) return hours + " ч. " + minutes + " мин.";
        return minutes + " мин.";
    }

    // ===== БАНЫ =====
    public boolean isBanned(String playerName) {
        return punishmentConfig.getBoolean(playerName + ".bans.active", false);
    }

    public String getBanReason(String playerName) {
        return punishmentConfig.getString(playerName + ".bans.reason", "Без причины");
    }

    public String getBanIssuer(String playerName) {
        return punishmentConfig.getString(playerName + ".bans.issuer", "Unknown");
    }

    public String getBanDuration(String playerName) {
        return punishmentConfig.getString(playerName + ".bans.duration", "навсегда");
    }

    public long getBanTimestamp(String playerName) {
        return punishmentConfig.getLong(playerName + ".bans.timestamp", 0);
    }

    public boolean banPlayer(String playerName, String issuer, String reason, String duration) {
        if (isBanned(playerName)) return false;

        String cleanReason = cleanString(reason);
        String cleanIssuer = cleanString(issuer);
        String cleanName = cleanString(playerName);
        long now = System.currentTimeMillis();

        punishmentConfig.set(cleanName + ".bans.active", true);
        punishmentConfig.set(cleanName + ".bans.timestamp", now);
        punishmentConfig.set(cleanName + ".bans.issuer", cleanIssuer);
        punishmentConfig.set(cleanName + ".bans.reason", cleanReason);
        punishmentConfig.set(cleanName + ".bans.duration", duration);
        punishmentConfig.set(cleanName + ".bans.unpunished", false);

        addHistory(cleanName, "ban", now, cleanIssuer, cleanReason, duration);
        savePunishments();

        String timeStr = duration.equals("навсегда") ? "навсегда" : duration;
        String chatMessage = ChatColor.WHITE + "Игрок " + ChatColor.BLUE + cleanIssuer + ChatColor.WHITE + " забанил " + ChatColor.RED + cleanName + ChatColor.WHITE + " на " + ChatColor.AQUA + timeStr + ChatColor.WHITE + " по причине: " + ChatColor.GRAY + cleanReason;
        Bukkit.broadcastMessage(chatMessage);

        Player player = Bukkit.getPlayerExact(cleanName);
        if (player != null && player.isOnline()) {
            String kickMessage = ChatColor.RED + "" + ChatColor.BOLD + "У вас имеется активный бан!\n" +
                    ChatColor.RED + "Причина: " + ChatColor.WHITE + cleanReason + "\n" +
                    ChatColor.RED + "Дата выдачи: " + ChatColor.WHITE + formatDate(now) + "\n" +
                    ChatColor.RED + "Дата снятия: " + ChatColor.WHITE + (duration.equals("навсегда") ? "Никогда" : "Через " + duration) + "\n" +
                    ChatColor.RED + "Выдал: " + ChatColor.WHITE + cleanIssuer;
            Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer(kickMessage));
        }

        adminLogger.logSuccess("SYSTEM", "ban", cleanName, cleanReason);
        return true;
    }

    public boolean unbanPlayer(String playerName, String issuer, String reason) {
        if (!isBanned(playerName)) return false;

        String cleanReason = cleanString(reason);
        String cleanIssuer = cleanString(issuer);
        String cleanName = cleanString(playerName);
        long now = System.currentTimeMillis();

        punishmentConfig.set(cleanName + ".bans.active", false);
        punishmentConfig.set(cleanName + ".bans.unpunished", true);
        addHistory(cleanName, "unban", now, cleanIssuer, cleanReason, "навсегда");
        savePunishments();

        String chatMessage = ChatColor.WHITE + "Игрок " + ChatColor.BLUE + cleanIssuer + ChatColor.WHITE + " разбанил " + ChatColor.GREEN + cleanName + ChatColor.WHITE + " по причине: " + ChatColor.GRAY + cleanReason;
        Bukkit.broadcastMessage(chatMessage);

        adminLogger.logSuccess("SYSTEM", "unban", cleanName, cleanReason);
        return true;
    }

    // ===== МУТЫ =====
    public boolean isMuted(String player
