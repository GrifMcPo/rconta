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
    private File punishmentFile;
    private FileConfiguration punishmentConfig;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final Pattern TIME_PATTERN = Pattern.compile("^\\d+[smhdwMy]$");

    public PunishmentManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadPunishments();
        startAutoUnmuteTask();
        startAutoUnbanTask();
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

    public boolean isTimeExpired(String date, String duration) {
        try {
            long time = dateFormat.parse(date).getTime();
            long durationMs = getTimeInMillis(duration);
            if (durationMs == -1) return false;
            return System.currentTimeMillis() - time > durationMs;
        } catch (Exception e) {
            return false;
        }
    }

    public String formatDate(String date) {
        if (date == null) return "Неизвестно";
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            SimpleDateFormat output = new SimpleDateFormat("dd.MM.yyyy HH:mm");
            return output.format(input.parse(date));
        } catch (Exception e) {
            return date;
        }
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

    public String getBanDate(String playerName) {
        return punishmentConfig.getString(playerName + ".bans.date", "");
    }

    public boolean banPlayer(String playerName, String issuer, String reason, String duration) {
        if (isBanned(playerName)) return false;

        String now = dateFormat.format(new Date());

        punishmentConfig.set(playerName + ".bans.active", true);
        punishmentConfig.set(playerName + ".bans.date", now);
        punishmentConfig.set(playerName + ".bans.issuer", issuer);
        punishmentConfig.set(playerName + ".bans.reason", reason);
        punishmentConfig.set(playerName + ".bans.duration", duration);

        addHistory(playerName, "ban", now, issuer, reason, duration);
        savePunishments();

        // ---- СООБЩЕНИЕ В ЧАТ ----
        String timeStr = duration.equals("навсегда") ? "навсегда" : duration;
        String chatMessage = ChatColor.WHITE + "Игрок " + ChatColor.BLUE + issuer + ChatColor.WHITE + " забанил " + ChatColor.RED + playerName + ChatColor.WHITE + " на " + ChatColor.AQUA + timeStr + ChatColor.WHITE + " по причине: " + ChatColor.GRAY + reason;
        Bukkit.broadcastMessage(chatMessage);

        // ---- КИКАЕМ ИГРОКА ----
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null && player.isOnline()) {
            String kickMessage = ChatColor.RED + "" + ChatColor.BOLD + "У вас имеется активный бан!\n" +
                    ChatColor.RED + "Причина: " + ChatColor.WHITE + reason + "\n" +
                    ChatColor.RED + "Дата выдачи: " + ChatColor.WHITE + formatDate(now) + "\n" +
                    ChatColor.RED + "Дата снятия: " + ChatColor.WHITE + (duration.equals("навсегда") ? "Никогда" : "Через " + duration) + "\n" +
                    ChatColor.RED + "Выдал: " + ChatColor.WHITE + issuer;
            player.kickPlayer(kickMessage);
        }

        return true;
    }

    public boolean unbanPlayer(String playerName, String issuer, String reason) {
        if (!isBanned(playerName)) return false;

        String now = dateFormat.format(new Date());
        punishmentConfig.set(playerName + ".bans.active", false);
        addHistory(playerName, "unban", now, issuer, reason, "навсегда");
        savePunishments();

        String chatMessage = ChatColor.WHITE + "Игрок " + ChatColor.BLUE + issuer + ChatColor.WHITE + " разбанил " + ChatColor.GREEN + playerName + ChatColor.WHITE + " по причине: " + ChatColor.GRAY + reason;
        Bukkit.broadcastMessage(chatMessage);

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

    public String getMuteDate(String playerName) {
        return punishmentConfig.getString(playerName + ".mutes.date", "");
    }

    public boolean mutePlayer(String playerName, String issuer, String reason, String duration) {
        if (isMuted(playerName)) return false;

        String now = dateFormat.format(new Date());

        punishmentConfig.set(playerName + ".mutes.active", true);
        punishmentConfig.set(playerName + ".mutes.date", now);
        punishmentConfig.set(playerName + ".mutes.issuer", issuer);
        punishmentConfig.set(playerName + ".mutes.reason", reason);
        punishmentConfig.set(playerName + ".mutes.duration", duration);

        addHistory(playerName, "mute", now, issuer, reason, duration);
        savePunishments();

        // ---- СООБЩЕНИЕ ИГРОКУ ----
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null && player.isOnline()) {
            String timeStr = duration.equals("навсегда") ? "навсегда" : duration;
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "У вас есть активный мут!");
            player.sendMessage(ChatColor.RED + "Причина: " + ChatColor.WHITE + reason);
            player.sendMessage(ChatColor.RED + "Дата выдачи: " + ChatColor.WHITE + formatDate(now));
            player.sendMessage(ChatColor.RED + "Дата снятия: " + ChatColor.WHITE + (duration.equals("навсегда") ? "Никогда" : "Через " + duration));
            player.sendMessage(ChatColor.RED + "Выдал: " + ChatColor.WHITE + issuer);
        }

        // ---- СООБЩЕНИЕ В ЧАТ ----
        String timeStr2 = duration.equals("навсегда") ? "навсегда" : duration;
        String chatMessage = ChatColor.WHITE + "Игрок " + ChatColor.BLUE + issuer + ChatColor.WHITE + " замутил " + ChatColor.RED + playerName + ChatColor.WHITE + " на " + ChatColor.AQUA + timeStr2 + ChatColor.WHITE + " по причине: " + ChatColor.GRAY + reason;
        Bukkit.broadcastMessage(chatMessage);

        return true;
    }

    public boolean unmutePlayer(String playerName, String issuer, String reason) {
        if (!isMuted(playerName)) return false;

        String now = dateFormat.format(new Date());
        punishmentConfig.set(playerName + ".mutes.active", false);
        addHistory(playerName, "unmute", now, issuer, reason, "навсегда");
        savePunishments();

        String chatMessage = ChatColor.WHITE + "Игрок " + ChatColor.BLUE + issuer + ChatColor.WHITE + " размутил " + ChatColor.GREEN + playerName + ChatColor.WHITE + " по причине: " + ChatColor.GRAY + reason;
        Bukkit.broadcastMessage(chatMessage);

        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.GREEN + "С вас снят мут! Причина: " + reason);
        }

        return true;
    }

    // ========================================
    // ===== КИК =====
    // ========================================

    public boolean kickPlayer(String playerName, String issuer, String reason) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null || !player.isOnline()) return false;

        String now = dateFormat.format(new Date());
        addHistory(playerName, "kick", now, issuer, reason, "навсегда");
        savePunishments();

        player.kickPlayer(ChatColor.RED + "Вы кикнуты!\n" +
                ChatColor.RED + "Причина: " + ChatColor.WHITE + reason + "\n" +
                ChatColor.RED + "Выдал: " + ChatColor.WHITE + issuer);

        String chatMessage = ChatColor.WHITE + "Игрок " + ChatColor.BLUE + issuer + ChatColor.WHITE + " выгнал " + ChatColor.RED + playerName + ChatColor.WHITE + " по причине: " + ChatColor.GRAY + reason;
        Bukkit.broadcastMessage(chatMessage);

        return true;
    }

    // ========================================
    // ===== ИСТОРИЯ =====
    // ========================================

    private void addHistory(String playerName, String type, String date, String issuer, String reason, String duration) {
        int historyId = punishmentConfig.getInt(playerName + ".history_count", 0) + 1;
        punishmentConfig.set(playerName + ".history_" + historyId + ".type", type);
        punishmentConfig.set(playerName + ".history_" + historyId + ".date", date);
        punishmentConfig.set(playerName + ".history_" + historyId + ".issuer", issuer);
        punishmentConfig.set(playerName + ".history_" + historyId + ".reason", reason);
        punishmentConfig.set(playerName + ".history_" + historyId + ".duration", duration);
        punishmentConfig.set(playerName + ".history_count", historyId);
    }

    public List<String> getHistory(String playerName) {
        List<String> history = new ArrayList<>();
        int count = punishmentConfig.getInt(playerName + ".history_count", 0);

        for (int i = count; i >= 1; i--) {
            String type = punishmentConfig.getString(playerName + ".history_" + i + ".type");
            String date = punishmentConfig.getString(playerName + ".history_" + i + ".date");
            String issuer = punishmentConfig.getString(playerName + ".history_" + i + ".issuer");
            String reason = punishmentConfig.getString(playerName + ".history_" + i + ".reason");
            String duration = punishmentConfig.getString(playerName + ".history_" + i + ".duration", "навсегда");

            if (type != null) {
                String action = "";
                switch (type) {
                    case "ban": action = "забанен"; break;
                    case "unban": action = "разбанен"; break;
                    case "mute": action = "замучен"; break;
                    case "unmute": action = "размучен"; break;
                    case "kick": action = "кикнут"; break;
                    default: action = type;
                }
                history.add("• " + formatDate(date) + " → " + issuer + " " + action + " " + playerName + " (" + reason + ") [" + duration + "]");
            }
        }
        return history;
    }

    // ========================================
    // ===== СПИСКИ =====
    // ========================================

    public List<String> getBanList() {
        List<String> bans = new ArrayList<>();
        for (String key : punishmentConfig.getKeys(false)) {
            if (punishmentConfig.getBoolean(key + ".bans.active", false)) {
                String issuer = punishmentConfig.getString(key + ".bans.issuer", "Unknown");
                String reason = punishmentConfig.getString(key + ".bans.reason", "Без причины");
                bans.add("• " + key + " → " + reason + " (выдал: " + issuer + ")");
            }
        }
        return bans;
    }

    public List<String> getMuteList() {
        List<String> mutes = new ArrayList<>();
        for (String key : punishmentConfig.getKeys(false)) {
            if (punishmentConfig.getBoolean(key + ".mutes.active", false)) {
                String issuer = punishmentConfig.getString(key + ".mutes.issuer", "Unknown");
                String reason = punishmentConfig.getString(key + ".mutes.reason", "Без причины");
                mutes.add("• " + key + " → " + reason + " (выдал: " + issuer + ")");
            }
        }
        return mutes;
    }

    // ========================================
    // ===== АВТОСНЯТИЕ (БЕЗ СООБЩЕНИЙ) =====
    // ========================================

    private void startAutoUnmuteTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (String key : punishmentConfig.getKeys(false)) {
                if (punishmentConfig.getBoolean(key + ".mutes.active", false)) {
                    String date = punishmentConfig.getString(key + ".mutes.date");
                    String duration = punishmentConfig.getString(key + ".mutes.duration", "навсегда");
                    if (!duration.equals("навсегда") && isTimeExpired(date, duration)) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            punishmentConfig.set(key + ".mutes.active", false);
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
                    String date = punishmentConfig.getString(key + ".bans.date");
                    String duration = punishmentConfig.getString(key + ".bans.duration", "навсегда");
                    if (!duration.equals("навсегда") && isTimeExpired(date, duration)) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            punishmentConfig.set(key + ".bans.active", false);
                            savePunishments();
                        });
                    }
                }
            }
        }, 0L, 20L * 60);
    }

    // ========================================
    // ===== ПРОВЕРКИ =====
    // ========================================

    public void checkOnJoin(Player player) {
        String name = player.getName();

        if (isBanned(name)) {
            String reason = getBanReason(name);
            String issuer = getBanIssuer(name);
            String duration = getBanDuration(name);
            String date = getBanDate(name);
            String kickMessage = ChatColor.RED + "" + ChatColor.BOLD + "У вас имеется активный бан!\n" +
                    ChatColor.RED + "Причина: " + ChatColor.WHITE + reason + "\n" +
                    ChatColor.RED + "Дата выдачи: " + ChatColor.WHITE + formatDate(date) + "\n" +
                    ChatColor.RED + "Дата снятия: " + ChatColor.WHITE + (duration.equals("навсегда") ? "Никогда" : "Через " + duration) + "\n" +
                    ChatColor.RED + "Выдал: " + ChatColor.WHITE + issuer;
            player.kickPlayer(kickMessage);
            return;
        }

        if (isMuted(name)) {
            String date = punishmentConfig.getString(name + ".mutes.date");
            String duration = punishmentConfig.getString(name + ".mutes.duration", "навсегда");
            if (!duration.equals("навсегда") && isTimeExpired(date, duration)) {
                punishmentConfig.set(name + ".mutes.active", false);
                savePunishments();
                player.sendMessage(ChatColor.GREEN + "Ваш мут истёк!");
                return;
            }
            String reason = getMuteReason(name);
            String issuer = getMuteIssuer(name);
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "У вас есть активный мут!");
            player.sendMessage(ChatColor.RED + "Причина: " + ChatColor.WHITE + reason);
            player.sendMessage(ChatColor.RED + "Дата выдачи: " + ChatColor.WHITE + formatDate(date));
            player.sendMessage(ChatColor.RED + "Дата снятия: " + ChatColor.WHITE + (duration.equals("навсегда") ? "Никогда" : "Через " + duration));
            player.sendMessage(ChatColor.RED + "Выдал: " + ChatColor.WHITE + issuer);
        }
    }

    public boolean canPlayerChat(Player player) {
        if (isMuted(player.getName())) {
            String date = punishmentConfig.getString(player.getName() + ".mutes.date");
            String duration = punishmentConfig.getString(player.getName() + ".mutes.duration", "навсегда");
            if (!duration.equals("навсегда") && isTimeExpired(date, duration)) {
                punishmentConfig.set(player.getName() + ".mutes.active", false);
                savePunishments();
                player.sendMessage(ChatColor.GREEN + "Ваш мут истёк!");
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
