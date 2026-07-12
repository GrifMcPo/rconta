package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
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
    private final Map<String, Long> muteCache = new HashMap<>();
    private final Map<String, Long> banCache = new HashMap<>();

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

    // ========================================
    // ==== БАНЫ ====
    // ========================================

    public boolean isBanned(String playerName) {
        return punishmentConfig.getBoolean(playerName + ".bans.active", false);
    }

    public String getBanReason(String playerName) {
        return punishmentConfig.getString(playerName + ".bans.reason", "");
    }

    public String getBanIssuer(String playerName) {
        return punishmentConfig.getString(playerName + ".bans.issuer", "Unknown");
    }

    public String getBanDuration(String playerName) {
        return punishmentConfig.getString(playerName + ".bans.duration", "навсегда");
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
        banCache.put(playerName, System.currentTimeMillis() + getTimeInMillis(duration));

        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null && player.isOnline()) {
            String timeStr = duration.equals("навсегда") ? "навсегда" : duration;
            player.kickPlayer("§cВы забанены!\n§fПричина: §e" + reason + "\n§fСрок: §e" + timeStr + "\n§fВыдал: §e" + issuer);
        }

        // Отправляем сообщение в чат
        broadcastMessage("ban", playerName, issuer, reason, duration);
        return true;
    }

    public boolean unbanPlayer(String playerName, String issuer) {
        if (!isBanned(playerName)) return false;

        String now = dateFormat.format(new Date());
        punishmentConfig.set(playerName + ".bans.active", false);
        addHistory(playerName, "unban", now, issuer, "Снятие бана", "навсегда");
        savePunishments();
        banCache.remove(playerName);

        broadcastUnbanMessage("ban", playerName, issuer);
        return true;
    }

    // ========================================
    // ==== МУТЫ ====
    // ========================================

    public boolean isMuted(String playerName) {
        return punishmentConfig.getBoolean(playerName + ".mutes.active", false);
    }

    public String getMuteReason(String playerName) {
        return punishmentConfig.getString(playerName + ".mutes.reason", "");
    }

    public String getMuteIssuer(String playerName) {
        return punishmentConfig.getString(playerName + ".mutes.issuer", "Unknown");
    }

    public String getMuteDuration(String playerName) {
        return punishmentConfig.getString(playerName + ".mutes.duration", "навсегда");
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
        muteCache.put(playerName, System.currentTimeMillis() + getTimeInMillis(duration));

        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null && player.isOnline()) {
            String timeStr = duration.equals("навсегда") ? "навсегда" : duration;
            player.sendMessage("§cВы замучены!");
            player.sendMessage("§fПричина: §e" + reason);
            player.sendMessage("§fСрок: §e" + timeStr);
            player.sendMessage("§fВыдал: §e" + issuer);
        }

        broadcastMessage("mute", playerName, issuer, reason, duration);
        return true;
    }

    public boolean unmutePlayer(String playerName, String issuer) {
        if (!isMuted(playerName)) return false;

        String now = dateFormat.format(new Date());
        punishmentConfig.set(playerName + ".mutes.active", false);
        addHistory(playerName, "unmute", now, issuer, "Снятие мута", "навсегда");
        savePunishments();
        muteCache.remove(playerName);

        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null && player.isOnline()) {
            player.sendMessage("§aС вас снят мут!");
        }

        broadcastUnbanMessage("mute", playerName, issuer);
        return true;
    }

    // ========================================
    // ==== КИК ====
    // ========================================

    public boolean kickPlayer(String playerName, String issuer, String reason) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null || !player.isOnline()) return false;

        String now = dateFormat.format(new Date());
        addHistory(playerName, "kick", now, issuer, reason, "навсегда");
        savePunishments();

        player.kickPlayer("§cВы кикнуты!\n§fПричина: §e" + reason + "\n§fВыдал: §e" + issuer);

        broadcastMessage("kick", playerName, issuer, reason, "навсегда");
        return true;
    }

    // ========================================
    // ==== ИСТОРИЯ ====
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
                String shortDate = date;
                if (date != null && date.length() >= 16) {
                    shortDate = date.substring(8, 10) + "." + date.substring(5, 7) + "." + date.substring(0, 4) + " " + date.substring(11, 16);
                }
                history.add("• " + shortDate + " → " + issuer + " " + action + " " + playerName + " (" + reason + ") [" + duration + "]");
            }
        }
        return history;
    }

    // ========================================
    // ==== СПИСКИ ====
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
    // ==== КРАСИВЫЕ СООБЩЕНИЯ В ЧАТ ====
    // ========================================

    private void broadcastMessage(String type, String playerName, String issuer, String reason, String duration) {
        String actionName = "";
        String color = "";
        switch (type) {
            case "ban": actionName = "забанил"; color = "§c"; break;
            case "mute": actionName = "замутил"; color = "§e"; break;
            case "kick": actionName = "выгнал"; color = "§6"; break;
            default: return;
        }

        String timeStr = duration.equals("навсегда") ? "навсегда" : duration;
        String message = color + "✦ " + issuer + " §f" + actionName + " игрока §a" + playerName;
        if (!type.equals("kick")) message += " §fна срок §b" + timeStr;
        if (!reason.isEmpty()) message += " §fпо причине: §6" + reason;

        message = "§8§m----------------------------§r\n" + message + "\n§8§m----------------------------§r";
        Bukkit.broadcastMessage(message);
    }

    private void broadcastUnbanMessage(String type, String playerName, String issuer) {
        String actionName = type.equals("ban") ? "разбанен" : "размучен";
        String message = "§a✦ " + issuer + " §f" + actionName + " игрока §a" + playerName;
        message = "§8§m----------------------------§r\n" + message + "\n§8§m----------------------------§r";
        Bukkit.broadcastMessage(message);
    }

    // ========================================
    // ==== АВТОСНЯТИЕ ====
    // ========================================

    private void startAutoUnmuteTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (String key : punishmentConfig.getKeys(false)) {
                if (punishmentConfig.getBoolean(key + ".mutes.active", false)) {
                    String date = punishmentConfig.getString(key + ".mutes.date");
                    String duration = punishmentConfig.getString(key + ".mutes.duration", "навсегда");
                    if (!duration.equals("навсегда") && isTimeExpired(date, duration)) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            unmutePlayer(key, "Система (авто)");
                            Player player = Bukkit.getPlayerExact(key);
                            if (player != null && player.isOnline()) {
                                player.sendMessage("§aВаш мут истёк!");
                            }
                        });
                    }
                }
            }
        }, 0L, 20L * 60); // каждую минуту
    }

    private void startAutoUnbanTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (String key : punishmentConfig.getKeys(false)) {
                if (punishmentConfig.getBoolean(key + ".bans.active", false)) {
                    String date = punishmentConfig.getString(key + ".bans.date");
                    String duration = punishmentConfig.getString(key + ".bans.duration", "навсегда");
                    if (!duration.equals("навсегда") && isTimeExpired(date, duration)) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            unbanPlayer(key, "Система (авто)");
                        });
                    }
                }
            }
        }, 0L, 20L * 60); // каждую минуту
    }

    // ========================================
    // ==== ПРОВЕРКИ ====
    // ========================================

    public void checkOnJoin(Player player) {
        String name = player.getName();

        if (isBanned(name)) {
            String reason = punishmentConfig.getString(name + ".bans.reason", "Без причины");
            String issuer = punishmentConfig.getString(name + ".bans.issuer", "Unknown");
            String duration = punishmentConfig.getString(name + ".bans.duration", "навсегда");
            player.kickPlayer("§cВы забанены!\n§fПричина: §e" + reason + "\n§fСрок: §e" + duration + "\n§fВыдал: §e" + issuer);
            return;
        }

        if (isMuted(name)) {
            String date = punishmentConfig.getString(name + ".mutes.date");
            String duration = punishmentConfig.getString(name + ".mutes.duration", "навсегда");
            if (!duration.equals("навсегда") && isTimeExpired(date, duration)) {
                unmutePlayer(name, "Система (авто)");
                player.sendMessage("§aВаш мут истёк!");
                return;
            }
            String reason = punishmentConfig.getString(name + ".mutes.reason", "Без причины");
            String issuer = punishmentConfig.getString(name + ".mutes.issuer", "Unknown");
            player.sendMessage("§cВы замучены!");
            player.sendMessage("§fПричина: §e" + reason);
            player.sendMessage("§fВыдал: §e" + issuer);
        }
    }

    public boolean canPlayerChat(Player player) {
        if (isMuted(player.getName())) {
            String date = punishmentConfig.getString(player.getName() + ".mutes.date");
            String duration = punishmentConfig.getString(player.getName() + ".mutes.duration", "навсегда");
            if (!duration.equals("навсегда") && isTimeExpired(date, duration)) {
                unmutePlayer(player.getName(), "Система (авто)");
                player.sendMessage("§aВаш мут истёк!");
                return true;
            }
            return false;
        }
        return true;
    }
}
