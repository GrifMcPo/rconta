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
            try {
                punishmentFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("❌ Не удалось создать punishments.yml");
            }
        }
        punishmentConfig = YamlConfiguration.loadConfiguration(punishmentFile);
    }

    public void savePunishments() {
        try {
            punishmentConfig.save(punishmentFile);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка сохранения punishments.yml");
        }
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

    // ========================================
    // ===== БАНЫ =====
    // ========================================

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

    // ========================================
    // ===== МУТЫ =====
    // ========================================

    public boolean isMuted(String playerName) {
        return punishmentConfig.getBoolean(playerName + ".mutes.active", false);
    }

    public String getMuteReason(String playerName) {
        return punishmentConfig.getString(playerName + ".mutes.reason", "Без причины");
    }

    public String getMuteIssuer(String playerName) {
        return punishmentConfig.getString(playerName + ".mutes.issuer", "Unknown");
    }

    public String getMuteDuration(String playerName) {
        return punishmentConfig.getString(playerName + ".mutes.duration", "навсегда");
    }

    public long getMuteTimestamp(String playerName) {
        return punishmentConfig.getLong(playerName + ".mutes.timestamp", 0);
    }

    public boolean mutePlayer(String playerName, String issuer, String reason, String duration) {
        if (isMuted(playerName)) return false;

        String cleanReason = cleanString(reason);
        String cleanIssuer = cleanString(issuer);
        String cleanName = cleanString(playerName);

        long now = System.currentTimeMillis();

        punishmentConfig.set(cleanName + ".mutes.active", true);
        punishmentConfig.set(cleanName + ".mutes.timestamp", now);
        punishmentConfig.set(cleanName + ".mutes.issuer", cleanIssuer);
        punishmentConfig.set(cleanName + ".mutes.reason", cleanReason);
        punishmentConfig.set(cleanName + ".mutes.duration", duration);
        punishmentConfig.set(cleanName + ".mutes.unpunished", false);

        addHistory(cleanName, "mute", now, cleanIssuer, cleanReason, duration);
        savePunishments();

        Player player = Bukkit.getPlayerExact(cleanName);
        if (player != null && player.isOnline()) {
            String timeStr = duration.equals("навсегда") ? "навсегда" : duration;
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "У вас есть активный мут!");
            player.sendMessage(ChatColor.RED + "Причина: " + ChatColor.WHITE + cleanReason);
            player.sendMessage(ChatColor.RED + "Дата выдачи: " + ChatColor.WHITE + formatDate(now));
            player.sendMessage(ChatColor.RED + "Дата снятия: " + ChatColor.WHITE + (duration.equals("навсегда") ? "Никогда" : "Через " + duration));
            player.sendMessage(ChatColor.RED + "Выдал: " + ChatColor.WHITE + cleanIssuer);
        }

        String timeStr2 = duration.equals("навсегда") ? "навсегда" : duration;
        String chatMessage = ChatColor.WHITE + "Игрок " + ChatColor.BLUE + cleanIssuer + ChatColor.WHITE + " замутил " + ChatColor.RED + cleanName + ChatColor.WHITE + " на " + ChatColor.AQUA + timeStr2 + ChatColor.WHITE + " по причине: " + ChatColor.GRAY + cleanReason;
        Bukkit.broadcastMessage(chatMessage);

        adminLogger.logSuccess("SYSTEM", "mute", cleanName, cleanReason);
        return true;
    }

    public boolean unmutePlayer(String playerName, String issuer, String reason) {
        if (!isMuted(playerName)) return false;

        String cleanReason = cleanString(reason);
        String cleanIssuer = cleanString(issuer);
        String cleanName = cleanString(playerName);

        long now = System.currentTimeMillis();
        punishmentConfig.set(cleanName + ".mutes.active", false);
        punishmentConfig.set(cleanName + ".mutes.unpunished", true);
        addHistory(cleanName, "unmute", now, cleanIssuer, cleanReason, "навсегда");
        savePunishments();

        String chatMessage = ChatColor.WHITE + "Игрок " + ChatColor.BLUE + cleanIssuer + ChatColor.WHITE + " размутил " + ChatColor.GREEN + cleanName + ChatColor.WHITE + " по причине: " + ChatColor.GRAY + cleanReason;
        Bukkit.broadcastMessage(chatMessage);

        Player player = Bukkit.getPlayerExact(cleanName);
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.GREEN + "С вас снят мут! Причина: " + cleanReason);
        }

        adminLogger.logSuccess("SYSTEM", "unmute", cleanName, cleanReason);
        return true;
    }

    // ========================================
    // ===== КИК =====
    // ========================================

    public boolean kickPlayer(String playerName, String issuer, String reason) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null || !player.isOnline()) return false;

        String cleanReason = cleanString(reason);
        String cleanIssuer = cleanString(issuer);
        String cleanName = cleanString(playerName);

        long now = System.currentTimeMillis();
        addHistory(cleanName, "kick", now, cleanIssuer, cleanReason, "навсегда");
        savePunishments();

        String kickMessage = ChatColor.RED + "Вы кикнуты!\n" +
                ChatColor.RED + "Причина: " + ChatColor.WHITE + cleanReason + "\n" +
                ChatColor.RED + "Выдал: " + ChatColor.WHITE + cleanIssuer;

        Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer(kickMessage));

        String chatMessage = ChatColor.WHITE + "Игрок " + ChatColor.BLUE + cleanIssuer + ChatColor.WHITE + " выгнал " + ChatColor.RED + cleanName + ChatColor.WHITE + " по причине: " + ChatColor.GRAY + cleanReason;
        Bukkit.broadcastMessage(chatMessage);

        adminLogger.logSuccess("SYSTEM", "kick", cleanName, cleanReason);
        return true;
    }

    // ========================================
    // ===== ИСТОРИЯ =====
    // ========================================

    private void addHistory(String playerName, String type, long timestamp, String issuer, String reason, String duration) {
        int historyId = punishmentConfig.getInt(playerName + ".history_count", 0) + 1;
        punishmentConfig.set(playerName + ".history_" + historyId + ".type", type);
        punishmentConfig.set(playerName + ".history_" + historyId + ".timestamp", timestamp);
        punishmentConfig.set(playerName + ".history_" + historyId + ".issuer", issuer);
        punishmentConfig.set(playerName + ".history_" + historyId + ".reason", reason);
        punishmentConfig.set(playerName + ".history_" + historyId + ".duration", duration);
        punishmentConfig.set(playerName + ".history_count", historyId);
    }

    public List<HistoryEntry> getHistory(String playerName) {
        List<HistoryEntry> history = new ArrayList<>();
        int count = punishmentConfig.getInt(playerName + ".history_count", 0);

        for (int i = count; i >= 1; i--) {
            String type = punishmentConfig.getString(playerName + ".history_" + i + ".type");
            long timestamp = punishmentConfig.getLong(playerName + ".history_" + i + ".timestamp", 0);
            String issuer = punishmentConfig.getString(playerName + ".history_" + i + ".issuer");
            String reason = punishmentConfig.getString(playerName + ".history_" + i + ".reason");
            String duration = punishmentConfig.getString(playerName + ".history_" + i + ".duration", "навсегда");

            if (type != null) {
                history.add(new HistoryEntry(type, timestamp, issuer, reason, duration));
            }
        }
        return history;
    }

    public static class HistoryEntry {
        public final String type;
        public final long timestamp;
        public final String issuer;
        public final String reason;
        public final String duration;

        public HistoryEntry(String type, long timestamp, String issuer, String reason, String duration) {
            this.type = type;
            this.timestamp = timestamp;
            this.issuer = issuer;
            this.reason = reason;
            this.duration = duration;
        }

        public String getActionName() {
            switch (type) {
                case "ban": return "забанен";
                case "unban": return "разбанен";
                case "mute": return "замучен";
                case "unmute": return "размучен";
                case "kick": return "кикнут";
                default: return type;
            }
        }
    }

    // ========================================
    // ===== СПИСКИ С ПАГИНАЦИЕЙ =====
    // ========================================

    public List<String> getBanList(int page, int pageSize) {
        List<String> bans = new ArrayList<>();
        for (String key : punishmentConfig.getKeys(false)) {
            if (punishmentConfig.getBoolean(key + ".bans.active", false)) {
                String issuer = punishmentConfig.getString(key + ".bans.issuer", "Unknown");
                String reason = punishmentConfig.getString(key + ".bans.reason", "Без причины");
                long timestamp = punishmentConfig.getLong(key + ".bans.timestamp", 0);
                bans.add("• " + key + " → " + reason + " (выдал: " + issuer + " | " + formatDate(timestamp) + ")");
            }
        }
        return paginate(bans, page, pageSize);
    }

    public List<String> getMuteList(int page, int pageSize) {
        List<String> mutes = new ArrayList<>();
        for (String key : punishmentConfig.getKeys(false)) {
            if (punishmentConfig.getBoolean(key + ".mutes.active", false)) {
                String issuer = punishmentConfig.getString(key + ".mutes.issuer", "Unknown");
                String reason = punishmentConfig.getString(key + ".mutes.reason", "Без причины");
                long timestamp = punishmentConfig.getLong(key + ".mutes.timestamp", 0);
                mutes.add("• " + key + " → " + reason + " (выдал: " + issuer + " | " + formatDate(timestamp) + ")");
            }
        }
        return paginate(mutes, page, pageSize);
    }

    private List<String> paginate(List<String> items, int page, int pageSize) {
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, items.size());
        if (start >= items.size() || start < 0) return new ArrayList<>();
        return items.subList(start, end);
    }

    public int getTotalPages(int totalItems, int pageSize) {
        return (int) Math.ceil((double) totalItems / pageSize);
    }

    // ========================================
    // ===== АВТОСНЯТИЕ =====
    // ========================================

    private void startAutoUnmuteTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (String key : punishmentConfig.getKeys(false)) {
                if (punishmentConfig.getBoolean(key + ".mutes.active", false)) {
                    if (punishmentConfig.getBoolean(key + ".mutes.unpunished", false)) {
                        punishmentConfig.set(key + ".mutes.active", false);
                        savePunishments();
                        continue;
                    }
                    long timestamp = punishmentConfig.getLong(key + ".mutes.timestamp", 0);
                    String duration = punishmentConfig.getString(key + ".mutes.duration", "навсегда");
                    if (!duration.equals("навсегда") && isTimeExpired(timestamp, duration)) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (punishmentConfig.getBoolean(key + ".mutes.unpunished", false)) return;
                            punishmentConfig.set(key + ".mutes.active", false);
                            punishmentConfig.set(key + ".mutes.unpunished", true);
                            savePunishments();
                            Player player = Bukkit.getPlayerExact(key);
                            if (player != null && player.isOnline()) {
                                player.sendMessage(ChatColor.GREEN + "Ваш мут истёк!");
                            }
                        });
                    }
                }
            }
        }, 0L, 20L * 60);
    }

    private void startAutoUnbanTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (String key : punishmentConfig.getKeys(false)) {
                if (punishmentConfig.getBoolean(key + ".bans.active", false)) {
                    if (punishmentConfig.getBoolean(key + ".bans.unpunished", false)) {
                        punishmentConfig.set(key + ".bans.active", false);
                        savePunishments();
                        continue;
                    }
                    long timestamp = punishmentConfig.getLong(key + ".bans.timestamp", 0);
                    String duration = punishmentConfig.getString(key + ".bans.duration", "навсегда");
                    if (!duration.equals("навсегда") && isTimeExpired(timestamp, duration)) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (punishmentConfig.getBoolean(key + ".bans.unpunished", false)) return;
                            punishmentConfig.set(key + ".bans.active", false);
                            punishmentConfig.set(key + ".bans.unpunished", true);
                            savePunishments();
                        });
                    }
                }
            }
        }, 0L, 20L * 60);
    }

    // ========================================
    // ===== ПРОВЕРКИ (БЕЗ ЗАМОРОЗКИ!) =====
    // ========================================

    public void checkOnJoin(Player player) {
        String name = player.getName();

        if (isBanned(name)) {
            String reason = getBanReason(name);
            String issuer = getBanIssuer(name);
            String duration = getBanDuration(name);
            long timestamp = getBanTimestamp(name);
            String kickMessage = ChatColor.RED + "" + ChatColor.BOLD + "У вас имеется активный бан!\n" +
                    ChatColor.RED + "Причина: " + ChatColor.WHITE + reason + "\n" +
                    ChatColor.RED + "Дата выдачи: " + ChatColor.WHITE + formatDate(timestamp) + "\n" +
                    ChatColor.RED + "Дата снятия: " + ChatColor.WHITE + (duration.equals("навсегда") ? "Никогда" : "Через " + duration) + "\n" +
                    ChatColor.RED + "Выдал: " + ChatColor.WHITE + issuer;
            player.kickPlayer(kickMessage);
            return;
        }

        if (isMuted(name)) {
            long timestamp = getMuteTimestamp(name);
            String duration = getMuteDuration(name);
            if (!duration.equals("навсегда") && isTimeExpired(timestamp, duration)) {
                if (!punishmentConfig.getBoolean(name + ".mutes.unpunished", false)) {
                    punishmentConfig.set(name + ".mutes.active", false);
                    punishmentConfig.set(name + ".mutes.unpunished", true);
                    savePunishments();
                    player.sendMessage(ChatColor.GREEN + "Ваш мут истёк!");
                }
                return;
            }
            String reason = getMuteReason(name);
            String issuer = getMuteIssuer(name);
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "У вас есть активный мут!");
            player.sendMessage(ChatColor.RED + "Причина: " + ChatColor.WHITE + reason);
            player.sendMessage(ChatColor.RED + "Дата выдачи: " + ChatColor.WHITE + formatDate(timestamp));
            player.sendMessage(ChatColor.RED + "Дата снятия: " + ChatColor.WHITE + (duration.equals("навсегда") ? "Никогда" : "Через " + duration));
            player.sendMessage(ChatColor.RED + "Выдал: " + ChatColor.WHITE + issuer);
        }
    }

    public boolean canPlayerChat(Player player) {
        if (isMuted(player.getName())) {
            long timestamp = getMuteTimestamp(player.getName());
            String duration = getMuteDuration(player.getName());
            if (!duration.equals("навсегда") && isTimeExpired(timestamp, duration)) {
                if (!punishmentConfig.getBoolean(player.getName() + ".mutes.unpunished", false)) {
                    punishmentConfig.set(player.getName() + ".mutes.active", false);
                    punishmentConfig.set(player.getName() + ".mutes.unpunished", true);
                    savePunishments();
                    player.sendMessage(ChatColor.GREEN + "Ваш мут истёк!");
                }
                return true;
            }
            String reason = getMuteReason(player.getName());
            String issuer = getMuteIssuer(player.getName());
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Вы замучены!");
            player.sendMessage(ChatColor.RED + "Причина: " + ChatColor.WHITE + reason);
            player.sendMessage(ChatColor.RED + "Выдал: " + ChatColor.WHITE + issuer);
            return false;
        }
        return true;
    }
}
