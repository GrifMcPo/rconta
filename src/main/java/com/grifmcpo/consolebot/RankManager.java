package com.grifmcpo.consolebot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RankManager {

    private final JavaPlugin plugin;
    private File rankFile;
    private FileConfiguration rankConfig;
    private final Map<String, Rank> ranks = new ConcurrentHashMap<>();
    private final Map<Long, String> userRanks = new ConcurrentHashMap<>(); // telegramId -> rankName

    public RankManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadRanks();
    }

    private void loadRanks() {
        rankFile = new File(plugin.getDataFolder(), "ranks.yml");
        if (!rankFile.exists()) {
            try {
                rankFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("❌ Не удалось создать ranks.yml");
            }
        }
        rankConfig = YamlConfiguration.loadConfiguration(rankFile);
        loadRanksFromConfig();
    }

    private void loadRanksFromConfig() {
        ranks.clear();
        userRanks.clear();

        for (String rankName : rankConfig.getKeys(false)) {
            Rank rank = new Rank(rankName);
            List<Map<?, ?>> permissions = rankConfig.getMapList(rankName + ".permissions");
            for (Map<?, ?> perm : permissions) {
                String command = (String) perm.get("command");
                String limit = (String) perm.get("limit");
                rank.addPermission(command, limit != null ? limit : "навсегда");
            }
            ranks.put(rankName, rank);
        }

        // Загружаем пользователей
        for (String rankName : ranks.keySet()) {
            List<Long> users = rankConfig.getLongList(rankName + ".users");
            for (long id : users) {
                userRanks.put(id, rankName);
            }
        }

        plugin.getLogger().info("✅ Загружено рангов: " + ranks.size());
    }

    public void saveRanks() {
        for (String rankName : ranks.keySet()) {
            Rank rank = ranks.get(rankName);
            rankConfig.set(rankName + ".permissions", rank.getPermissions());
            rankConfig.set(rankName + ".users", new ArrayList<>(rank.getUsers()));
        }
        try {
            rankConfig.save(rankFile);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка сохранения ranks.yml");
        }
    }

    // ===== РАНГИ =====

    public boolean createRank(String name) {
        if (ranks.containsKey(name)) return false;
        Rank rank = new Rank(name);
        ranks.put(name, rank);
        saveRanks();
        return true;
    }

    public boolean deleteRank(String name) {
        if (!ranks.containsKey(name)) return false;
        ranks.remove(name);
        // Удаляем пользователей из ранга
        userRanks.entrySet().removeIf(entry -> entry.getValue().equals(name));
        rankConfig.set(name, null);
        saveRanks();
        return true;
    }

    public boolean addPermission(String rankName, String command, String limit) {
        Rank rank = ranks.get(rankName);
        if (rank == null) return false;
        rank.addPermission(command, limit != null ? limit : "навсегда");
        saveRanks();
        return true;
    }

    public boolean removePermission(String rankName, String command) {
        Rank rank = ranks.get(rankName);
        if (rank == null) return false;
        rank.removePermission(command);
        saveRanks();
        return true;
    }

    public boolean addUserToRank(String rankName, long telegramId) {
        Rank rank = ranks.get(rankName);
        if (rank == null) return false;
        rank.addUser(telegramId);
        userRanks.put(telegramId, rankName);
        saveRanks();
        return true;
    }

    public boolean removeUserFromRank(long telegramId, String reason) {
        String rankName = userRanks.get(telegramId);
        if (rankName == null) return false;
        Rank rank = ranks.get(rankName);
        if (rank != null) {
            rank.removeUser(telegramId);
        }
        userRanks.remove(telegramId);
        saveRanks();
        return true;
    }

    public String getUserRank(long telegramId) {
        return userRanks.get(telegramId);
    }

    public Rank getRank(String name) {
        return ranks.get(name);
    }

    public List<String> getRankNames() {
        return new ArrayList<>(ranks.keySet());
    }

    public boolean hasPermission(long telegramId, String command) {
        String rankName = userRanks.get(telegramId);
        if (rankName == null) return false;
        Rank rank = ranks.get(rankName);
        if (rank == null) return false;
        return rank.hasPermission(command);
    }

    public String getCommandLimit(long telegramId, String command) {
        String rankName = userRanks.get(telegramId);
        if (rankName == null) return null;
        Rank rank = ranks.get(rankName);
        if (rank == null) return null;
        return rank.getCommandLimit(command);
    }

    public List<Long> getAllUsers() {
        return new ArrayList<>(userRanks.keySet());
    }

    public Map<Long, String> getUserRanks() {
        return userRanks;
    }

    // ===== КЛАСС РАНГА =====

    public static class Rank {
        private final String name;
        private final Map<String, String> permissions = new HashMap<>();
        private final List<Long> users = new ArrayList<>();

        public Rank(String name) {
            this.name = name;
        }

        public String getName() { return name; }

        public void addPermission(String command, String limit) {
            permissions.put(command, limit);
        }

        public void removePermission(String command) {
            permissions.remove(command);
        }

        public boolean hasPermission(String command) {
            return permissions.containsKey(command);
        }

        public String getCommandLimit(String command) {
            return permissions.get(command);
        }

        public Map<String, String> getPermissions() {
            return permissions;
        }

        public List<Long> getUsers() {
            return users;
        }

        public void addUser(long telegramId) {
            if (!users.contains(telegramId)) users.add(telegramId);
        }

        public void removeUser(long telegramId) {
            users.remove(telegramId);
        }
    }
}
