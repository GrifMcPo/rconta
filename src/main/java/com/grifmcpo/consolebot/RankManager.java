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
    private final Map<Long, String> userRanks = new ConcurrentHashMap<>();
    private final Set<Long> allUsers = ConcurrentHashMap.newKeySet();

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
        allUsers.clear();

        // Загружаем всех пользователей
        List<Long> users = rankConfig.getLongList("users");
        allUsers.addAll(users);

        for (String rankName : rankConfig.getKeys(false)) {
            if (rankName.equals("users")) continue;
            Rank rank = new Rank(rankName);

            List<Map<?, ?>> permissions = rankConfig.getMapList(rankName + ".permissions");
            for (Map<?, ?> perm : permissions) {
                String command = (String) perm.get("command");
                String limit = (String) perm.get("limit");
                if (command != null) {
                    rank.addPermission(command, limit != null ? limit : "навсегда");
                }
            }

            List<Long> rankUsers = rankConfig.getLongList(rankName + ".users");
            for (long id : rankUsers) {
                rank.addUser(id);
                userRanks.put(id, rankName);
                allUsers.add(id);
            }

            ranks.put(rankName, rank);
        }

        plugin.getLogger().info("✅ Загружено рангов: " + ranks.size() + ", пользователей: " + allUsers.size());
    }

    public void saveRanks() {
        for (String rankName : ranks.keySet()) {
            Rank rank = ranks.get(rankName);
            rankConfig.set(rankName + ".permissions", rank.getPermissionsList());
            rankConfig.set(rankName + ".users", new ArrayList<>(rank.getUsers()));
        }
        rankConfig.set("users", new ArrayList<>(allUsers));
        try {
            rankConfig.save(rankFile);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка сохранения ranks.yml: " + e.getMessage());
        }
    }

    // ===== ПОЛЬЗОВАТЕЛИ =====
    public void addUser(long telegramId) {
        if (allUsers.add(telegramId)) {
            saveRanks();
        }
    }

    public List<Long> getAllUsers() {
        return new ArrayList<>(allUsers);
    }

    public boolean isUserExists(long telegramId) {
        return allUsers.contains(telegramId);
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
        Rank rank = ranks.get(name);
        for (long id : rank.getUsers()) {
            userRanks.remove(id);
        }
        ranks.remove(name);
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

        String oldRank = userRanks.get(telegramId);
        if (oldRank != null) {
            Rank old = ranks.get(oldRank);
            if (old != null) old.removeUser(telegramId);
        }

        rank.addUser(telegramId);
        userRanks.put(telegramId, rankName);
        allUsers.add(telegramId);
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

    // ===== ПРОВЕРКА ПРАВ =====
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

    public boolean isTechWork() {
        return plugin.getConfig().getBoolean("maintenance", false);
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

        public List<Map<String, String>> getPermissionsList() {
            List<Map<String, String>> list = new ArrayList<>();
            for (Map.Entry<String, String> entry : permissions.entrySet()) {
                Map<String, String> perm = new HashMap<>();
                perm.put("command", entry.getKey());
                perm.put("limit", entry.getValue());
                list.add(perm);
            }
            return list;
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
