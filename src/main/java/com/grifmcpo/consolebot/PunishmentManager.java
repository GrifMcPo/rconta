package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PunishmentManager {

    private final JavaPlugin plugin;
    private final AdminLogger adminLogger;
    private File historyFile;
    private FileConfiguration historyConfig;
    private final Map<String, List<HistoryEntry>> history = new ConcurrentHashMap<>();
    private final Map<String, Long> bans = new ConcurrentHashMap<>();
    private final Map<String, Long> mutes = new ConcurrentHashMap<>();
    private final Map<String, String> muteIssuers = new ConcurrentHashMap<>();
    private final Map<String, String> muteReasons = new ConcurrentHashMap<>();

    public PunishmentManager(JavaPlugin plugin, AdminLogger adminLogger) {
        this.plugin = plugin;
        this.adminLogger = adminLogger;
        loadHistory();
        loadActivePunishments();
    }

    @SuppressWarnings("unchecked")
    private void loadHistory() {
        historyFile = new File(plugin.getDataFolder(), "history.yml");
        if (!historyFile.exists()) {
            try {
                historyFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("❌ Не удалось создать history.yml");
            }
        }
        historyConfig = YamlConfiguration.loadConfiguration(historyFile);

        history.clear();
        for (String playerName : historyConfig.getKeys(false)) {
            List<Map<?, ?>> entries = (List<Map<?, ?>>) historyConfig.getList(playerName);
            if (entries == null) continue;
            List<HistoryEntry> list = new ArrayList<>();
            for (Map<?, ?> entry : entries) {
                HistoryEntry he = new HistoryEntry();
                he.type = (String) entry.get("type");
                he.player = (String) entry.get("player");
                he.issuer = (String) entry.get("issuer");
                he.reason = (String) entry.get("reason");
                he.duration = (String) entry.get("duration");
                he.timestamp = ((Number) entry.get("timestamp")).longValue();
                Object hiddenObj = entry.get("hidden");
                he.hidden = hiddenObj != null && (boolean) hiddenObj;
                list.add(he);
            }
            history.put(playerName, list);
        }
        plugin.getLogger().info("✅ Загружена история наказаний");
    }

    private void loadActivePunishments() {
        bans.clear();
        mutes.clear();
        muteIssuers.clear();
        muteReasons.clear();
        for (Map.Entry<String, List<HistoryEntry>> entry : history.entrySet()) {
            String playerName = entry.getKey();
            List<HistoryEntry> list = entry.getValue();
            for (HistoryEntry he : list) {
                if (he.type.equals("ban") && !he.duration.equals("навсегда")) {
                    long expiry = he.timestamp + parseTimeToMillis(he.duration);
                    if (expiry > System.currentTimeMillis()) {
                        bans.put(playerName, expiry);
                    }
                }
                if (he.type.equals("mute") && !he.duration.equals("навсегда")) {
                    long expiry = he.timestamp + parseTimeToMillis(he.duration);
                    if (expiry > System.currentTimeMillis()) {
                        mutes.put(playerName, expiry);
                        muteIssuers.put(playerName, he.issuer);
                        muteReasons.put(playerName, he.reason);
                    }
                }
                if (he.type.equals("ban") && he.duration.equals("навсегда")) {
                    bans.put(playerName, -1L);
                }
                if (he.type.equals("mute") && he.duration.equals("навсегда")) {
                    mutes.put(playerName, -1L);
                    muteIssuers.put(playerName, he.issuer);
                    muteReasons.put(playerName, he.reason);
                }
            }
        }
        plugin.getLogger().info("✅ Загружено активных банов: " + bans.size() + ", мутов: " + mutes.size());
    }

    public void saveHistory() {
        for (Map.Entry<String, List<HistoryEntry>> entry : history.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (HistoryEntry he : entry.getValue()) {
                Map<String, Object> map = new HashMap<>();
                map.put("type", he.type);
                map.put("player", he.player);
                map.put("issuer", he.issuer);
                map.put("reason", he.reason);
                map.put("duration", he.duration);
                map.put("timestamp", he.timestamp);
                map.put("hidden", he.hidden);
                list.add(map);
            }
            historyConfig.set(entry.getKey(), list);
        }
        try {
            historyConfig.save(historyFile);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка сохранения history.yml: " + e.getMessage());
        }
    }

    // ============================================
    // ==== БАН =====
    // ============================================
    public boolean banPlayer(String playerName, String issuer, String reason, String duration) {
        return banPlayer(playerName, issuer, reason, duration, false);
    }

    public boolean banPlayer(String playerName, String issuer, String reason, String duration, boolean hidden) {
        if (isBanned(playerName)) {
            return false;
        }

        final String finalPlayerName = playerName;
        final String finalIssuer = issuer;
        final String finalReason = reason;
        final String finalDuration = duration;
        final boolean finalHidden = hidden;

        Bukkit.getScheduler().runTask(plugin, () -> {
            HistoryEntry entry = new HistoryEntry();
            entry.type = "ban";
            entry.player = finalPlayerName;
            entry.issuer = finalIssuer;
            entry.reason = finalReason;
            entry.duration = finalDuration;
            entry.timestamp = System.currentTimeMillis();
            entry.hidden = finalHidden;
            addHistorySync(finalPlayerName, entry);

            long expiry = finalDuration.equals("навсегда") ? -1 : System.currentTimeMillis() + parseTimeToMillis(finalDuration);
            bans.put(finalPlayerName, expiry);
            saveHistory();

            String command;
            if (finalDuration.equals("навсегда")) {
                command = "ban " + finalPlayerName + " " + finalReason;
            } else {
                command = "tempban " + finalPlayerName + " " + finalDuration + " " + finalReason;
            }

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

            if (!finalHidden) {
                Bukkit.broadcastMessage("§c" + finalPlayerName + " был забанен на " + finalDuration + "! Причина: " + finalReason);
            }

            if (adminLogger != null) {
                adminLogger.log("BAN", finalPlayerName, finalIssuer, finalReason, finalDuration, finalHidden ? "СКРЫТО" : "ПУБЛИЧНО");
            }
        });

        return true;
    }

    // ============================================
    // ==== РАЗБАН =====
    // ============================================
    public boolean unbanPlayer(String playerName, String issuer, String reason) {
        if (!isBanned(playerName)) {
            return false;
        }

        final String finalPlayerName = playerName;
        final String finalIssuer = issuer;
        final String finalReason = reason;

        Bukkit.getScheduler().runTask(plugin, () -> {
            bans.remove(finalPlayerName);
            saveHistory();

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pardon " + finalPlayerName);

            Bukkit.broadcastMessage("§a" + finalPlayerName + " был разбанен! Причина: " + finalReason);

            if (adminLogger != null) {
                adminLogger.log("UNBAN", finalPlayerName, finalIssuer, finalReason, "навсегда", "ПУБЛИЧНО");
            }
        });

        return true;
    }

    // ============================================
    // ==== МУТ =====
    // ============================================
    public boolean mutePlayer(String playerName, String issuer, String reason, String duration) {
        return mutePlayer(playerName, issuer, reason, duration, false);
    }

    public boolean mutePlayer(String playerName, String issuer, String reason, String duration, boolean hidden) {
        if (isMuted(playerName)) {
            return false;
        }

        final String finalPlayerName = playerName;
        final String finalIssuer = issuer;
        final String finalReason = reason;
        final String finalDuration = duration;
        final boolean finalHidden = hidden;

        Bukkit.getScheduler().runTask(plugin, () -> {
            HistoryEntry entry = new HistoryEntry();
            entry.type = "mute";
            entry.player = finalPlayerName;
            entry.issuer = finalIssuer;
            entry.reason = finalReason;
            entry.duration = finalDuration;
            entry.timestamp = System.currentTimeMillis();
            entry.hidden = finalHidden;
            addHistorySync(finalPlayerName, entry);

            long expiry = finalDuration.equals("навсегда") ? -1 : System.currentTimeMillis() + parseTimeToMillis(finalDuration);
            mutes.put(finalPlayerName, expiry);
            muteIssuers.put(finalPlayerName, finalIssuer);
            muteReasons.put(finalPlayerName, finalReason);
            saveHistory();

            String command;
            if (finalDuration.equals("навсегда")) {
                command = "mute " + finalPlayerName + " " + finalReason;
            } else {
                command = "tempmute " + finalPlayerName + " " + finalDuration + " " + finalReason;
            }

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

            if (!finalHidden) {
                Bukkit.broadcastMessage("§e" + finalPlayerName + " был замучен на " + finalDuration + "! Причина: " + finalReason);
            }

            if (adminLogger != null) {
                adminLogger.log("MUTE", finalPlayerName, finalIssuer, finalReason, finalDuration, finalHidden ? "СКРЫТО" : "ПУБЛИЧНО");
            }
        });

        return true;
    }

    // ============================================
    // ==== РАЗМУТ =====
    // ============================================
    public boolean unmutePlayer(String playerName, String issuer, String reason) {
        if (!isMuted(playerName)) {
            return false;
        }

        final String finalPlayerName = playerName;
        final String finalIssuer = issuer;
        final String finalReason = reason;

        Bukkit.getScheduler().runTask(plugin, () -> {
            mutes.remove(finalPlayerName);
            muteIssuers.remove(finalPlayerName);
            muteReasons.remove(finalPlayerName);
            saveHistory();

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "unmute " + finalPlayerName);

            Bukkit.broadcastMessage("§a" + finalPlayerName + " был размучен! Причина: " + finalReason);

            if (adminLogger != null) {
                adminLogger.log("UNMUTE", finalPlayerName, finalIssuer, finalReason, "навсегда", "ПУБЛИЧНО");
            }
        });

        return true;
    }

    // ============================================
    // ==== КИК =====
    // ============================================
    public boolean kickPlayer(String playerName, String issuer, String reason) {
        return kickPlayer(playerName, issuer, reason, false);
    }

    public boolean kickPlayer(String playerName, String issuer, String reason, boolean hidden) {
        final String finalPlayerName = playerName;
        final String finalIssuer = issuer;
        final String finalReason = reason;
        final boolean finalHidden = hidden;

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(finalPlayerName);
            if (player == null) {
                plugin.getLogger().warning("❌ Игрок " + finalPlayerName + " не найден для кика!");
                return;
            }

            HistoryEntry entry = new HistoryEntry();
            entry.type = "kick";
            entry.player = finalPlayerName;
            entry.issuer = finalIssuer;
            entry.reason = finalReason;
            entry.duration = "навсегда";
            entry.timestamp = System.currentTimeMillis();
            entry.hidden = finalHidden;
            addHistorySync(finalPlayerName, entry);
            saveHistory();

            player.kickPlayer("§cВы были кикнуты!\n§7Причина: " + finalReason);

            if (!finalHidden) {
                Bukkit.broadcastMessage("§e" + finalPlayerName + " был кикнут! Причина: " + finalReason);
            }

            if (adminLogger != null) {
                adminLogger.log("KICK", finalPlayerName, finalIssuer, finalReason, "навсегда", finalHidden ? "СКРЫТО" : "ПУБЛИЧНО");
            }
        });

        return true;
    }

    // ============================================
    // ==== GET LAST BAN =====
    // ============================================
    public HistoryEntry getLastBan(String playerName) {
        List<HistoryEntry> list = history.get(playerName);
        if (list == null || list.isEmpty()) return null;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).type.equals("ban")) {
                return list.get(i);
            }
        }
        return null;
    }

    // ============================================
    // ==== GET LAST MUTE =====
    // ============================================
    public HistoryEntry getLastMute(String playerName) {
        List<HistoryEntry> list = history.get(playerName);
        if (list == null || list.isEmpty()) return null;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).type.equals("mute")) {
                return list.get(i);
            }
        }
        return null;
    }

    // ============================================
    // ==== ПРОВЕРКИ ДЛЯ CommandListener =====
    // ============================================
    public boolean canPlayerChat(Player player) {
        return !isMuted(player.getName());
    }

    public String getMuteIssuer(String playerName) {
        return muteIssuers.get(playerName);
    }

    public String getMuteReason(String playerName) {
        return muteReasons.get(playerName);
    }

    public void checkOnJoin(Player player) {
        String name = player.getName();
        if (isBanned(name)) {
            Long expiry = bans.get(name);
            String expiryStr = expiry == -1 ? "навсегда" : getTimeLeft(expiry);
            player.kickPlayer("§cВы забанены!\n§7Причина: " + getBanReason(name) + "\n§7Осталось: " + expiryStr);
        }
    }

    private String getBanReason(String playerName) {
        List<HistoryEntry> list = history.get(playerName);
        if (list == null || list.isEmpty()) return "Неизвестно";
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).type.equals("ban")) {
                return list.get(i).reason;
            }
        }
        return "Неизвестно";
    }

    // ============================================
    // ==== ПРОВЕРКИ =====
    // ============================================
    public boolean isBanned(String playerName) {
        Long expiry = bans.get(playerName);
        if (expiry == null) return false;
        if (expiry == -1) return true;
        if (System.currentTimeMillis() > expiry) {
            bans.remove(playerName);
            return false;
        }
        return true;
    }

    public boolean isMuted(String playerName) {
        Long expiry = mutes.get(playerName);
        if (expiry == null) return false;
        if (expiry == -1) return true;
        if (System.currentTimeMillis() > expiry) {
            mutes.remove(playerName);
            muteIssuers.remove(playerName);
            muteReasons.remove(playerName);
            return false;
        }
        return true;
    }

    public boolean isValidTime(String time) {
        if (time == null) return false;
        if (time.equals("навсегда")) return true;
        return time.matches("\\d+[smhdwMy]");
    }

    // ============================================
    // ==== СПИСКИ =====
    // ============================================
    public List<String> getBanList(int page, int pageSize) {
        List<String> result = new ArrayList<>();
        int start = (page - 1) * pageSize;
        int index = 0;

        for (Map.Entry<String, Long> entry : bans.entrySet()) {
            if (index >= start && result.size() < pageSize) {
                String playerName = entry.getKey();
                long expiry = entry.getValue();
                String expiryStr = expiry == -1 ? "навсегда" : getTimeLeft(expiry);
                result.add("🔴 " + playerName + " — " + expiryStr);
            }
            index++;
        }
        return result;
    }

    public List<String> getMuteList(int page, int pageSize) {
        List<String> result = new ArrayList<>();
        int start = (page - 1) * pageSize;
        int index = 0;

        for (Map.Entry<String, Long> entry : mutes.entrySet()) {
            if (index >= start && result.size() < pageSize) {
                String playerName = entry.getKey();
                long expiry = entry.getValue();
                String expiryStr = expiry == -1 ? "навсегда" : getTimeLeft(expiry);
                result.add("🟡 " + playerName + " — " + expiryStr);
            }
            index++;
        }
        return result;
    }

    // ============================================
    // ==== ИСТОРИЯ =====
    // ============================================
    public List<HistoryEntry> getHistory(String playerName) {
        return history.getOrDefault(playerName, new ArrayList<>());
    }

    private void addHistorySync(String playerName, HistoryEntry entry) {
        List<HistoryEntry> list = history.computeIfAbsent(playerName, k -> new ArrayList<>());
        list.add(entry);
        saveHistory();
    }

    // ============================================
    // ==== ВСПОМОГАТЕЛЬНЫЕ =====
    // ============================================
    private long parseTimeToMillis(String time) {
        if (time == null || time.equals("навсегда")) return Long.MAX_VALUE;
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
            default: return Long.MAX_VALUE;
        }
    }

    private String getTimeLeft(long expiry) {
        if (expiry == -1) return "навсегда";
        long diff = expiry - System.currentTimeMillis();
        if (diff <= 0) return "истек";
        long days = diff / (24 * 60 * 60 * 1000);
        long hours = (diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (diff % (60 * 60 * 1000)) / (60 * 1000);
        if (days > 0) return days + "д " + hours + "ч";
        if (hours > 0) return hours + "ч " + minutes + "м";
        return minutes + "м";
    }

    public String getTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long days = diff / (24 * 60 * 60 * 1000);
        long hours = (diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        if (days > 0) return days + "д " + hours + "ч назад";
        if (hours > 0) return hours + "ч назад";
        return "только что";
    }

    // ============================================
    // ==== КЛАСС ИСТОРИИ =====
    // ============================================
    public static class HistoryEntry {
        public String type;
        public String player;
        public String issuer;
        public String reason;
        public String duration;
        public long timestamp;
        public boolean hidden = false;

        public String getActionName() {
            switch (type) {
                case "ban": return "забанен";
                case "mute": return "замучен";
                case "kick": return "кикнут";
                case "unban": return "разбанен";
                case "unmute": return "размучен";
                default: return type;
            }
        }
    }
}
