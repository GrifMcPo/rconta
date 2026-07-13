package com.grifmcpo.consolebot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RankManager {

    private final TelegramConsoleBot plugin;
    private File rankFile;
    private FileConfiguration rankConfig;
    private final Map<String, Rank> ranks = new ConcurrentHashMap<>();
    private final Map<Long, String> userRanks = new ConcurrentHashMap<>();
    private final Set<Long> allUsers = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> tempRanks = new ConcurrentHashMap<>(); // userId -> expires

    public RankManager(TelegramConsoleBot plugin) {
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
        tempRanks.clear();

        // Загружаем всех пользователей
        List<Long> users = rankConfig.getLongList("users");
        allUsers.addAll(users);

        for (String rankName : rankConfig.getKeys(false)) {
            if (rankName.equals("users")) continue;
            
            Rank rank = new Rank(rankName);
            
            // Внешний вид
            rank.setPrefix(rankConfig.getString(rankName + ".prefix", ""));
            rank.setSuffix(rankConfig.getString(rankName + ".suffix", ""));
            rank.setEmoji(rankConfig.getString(rankName + ".emoji", ""));
            rank.setColor(rankConfig.getString(rankName + ".color", ""));
            rank.setPriority(rankConfig.getInt(rankName + ".priority", 0));

            // Права
            List<Map<?, ?>> permissions = rankConfig.getMapList(rankName + ".permissions");
            for (Map<?, ?> perm : permissions) {
                String command = (String) perm.get("command");
                String limit = (String) perm.get("limit");
                if (command != null) {
                    rank.addPermission(command, limit != null ? limit : "навсегда");
                }
            }

            // Наследование
            List<String> inherits = rankConfig.getStringList(rankName + ".inherits");
            rank.setInherits(new HashSet<>(inherits));

            // Пользователи
            List<Long> rankUsers = rankConfig.getLongList(rankName + ".users");
            for (long id : rankUsers) {
                rank.addUser(id);
                userRanks.put(id, rankName);
                allUsers.add(id);
            }

            // Временные ранги
            List<Map<?, ?>> temps = rankConfig.getMapList(rankName + ".temp");
            for (Map<?, ?> temp : temps) {
                long userId = ((Number) temp.get("id")).longValue();
                long expires = ((Number) temp.get("expires")).longValue();
                tempRanks.put(userId, expires);
            }

            ranks.put(rankName, rank);
        }

        // Удаляем истекшие временные ранги
        checkTempRanks();

        plugin.getLogger().info("✅ Загружено рангов: " + ranks.size() + ", пользователей: " + allUsers.size());
    }

    public void saveRanks() {
        for (String rankName : ranks.keySet()) {
            Rank rank = ranks.get(rankName);
            
            rankConfig.set(rankName + ".prefix", rank.getPrefix());
            rankConfig.set(rankName + ".suffix", rank.getSuffix());
            rankConfig.set(rankName + ".emoji", rank.getEmoji());
            rankConfig.set(rankName + ".color", rank.getColor());
            rankConfig.set(rankName + ".priority", rank.getPriority());
            
            rankConfig.set(rankName + ".permissions", rank.getPermissionsList());
            rankConfig.set(rankName + ".inherits", new ArrayList<>(rank.getInherits()));
            rankConfig.set(rankName + ".users", new ArrayList<>(rank.getUsers()));
        }
        rankConfig.set("users", new ArrayList<>(allUsers));
        try {
            rankConfig.save(rankFile);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка сохранения ranks.yml: " + e.getMessage());
        }
    }

    // ===== ПРОВЕРКА ВРЕМЕННЫХ РАНГОВ =====
    private void checkTempRanks() {
        long now = System.currentTimeMillis();
        List<Long> toRemove = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : tempRanks.entrySet()) {
            if (entry.getValue() <= now) {
                toRemove.add(entry.getKey());
            }
        }
        for (long userId : toRemove) {
            removeUserFromRank(userId, "Временный ранг истек");
            tempRanks.remove(userId);
        }
        if (!toRemove.isEmpty()) {
            saveRanks();
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
    public boolean createRank(String name, String prefix, String emoji, String color) {
        if (ranks.containsKey(name)) return false;
        Rank rank = new Rank(name);
        rank.setPrefix(prefix != null ? prefix : "");
        rank.setEmoji(emoji != null ? emoji : "");
        rank.setColor(color != null ? color : "");
        rank.setPriority(ranks.size());
        ranks.put(name, rank);
        saveRanks();
        return true;
    }

    public boolean deleteRank(String name) {
        if (!ranks.containsKey(name)) return false;
        Rank rank = ranks.get(name);
        for (long id : rank.getUsers()) {
            userRanks.remove(id);
            tempRanks.remove(id);
        }
        ranks.remove(name);
        rankConfig.set(name, null);
        saveRanks();
        return true;
    }

    public boolean renameRank(String oldName, String newName) {
        if (!ranks.containsKey(oldName)) return false;
        if (ranks.containsKey(newName)) return false;
        
        Rank rank = ranks.remove(oldName);
        rank.setName(newName);
        ranks.put(newName, rank);
        
        for (Map.Entry<Long, String> entry : userRanks.entrySet()) {
            if (entry.getValue().equals(oldName)) {
                userRanks.put(entry.getKey(), newName);
            }
        }
        
        rankConfig.set(oldName, null);
        saveRanks();
        return true;
    }

    public boolean cloneRank(String source, String target) {
        if (!ranks.containsKey(source)) return false;
        if (ranks.containsKey(target)) return false;
        
        Rank sourceRank = ranks.get(source);
        Rank newRank = new Rank(target);
        newRank.setPrefix(sourceRank.getPrefix());
        newRank.setSuffix(sourceRank.getSuffix());
        newRank.setEmoji(sourceRank.getEmoji());
        newRank.setColor(sourceRank.getColor());
        newRank.setPriority(sourceRank.getPriority());
        newRank.setInherits(new HashSet<>(sourceRank.getInherits()));
        
        for (Map.Entry<String, String> perm : sourceRank.getPermissions().entrySet()) {
            newRank.addPermission(perm.getKey(), perm.getValue());
        }
        
        ranks.put(target, newRank);
        saveRanks();
        return true;
    }

    // ===== ВНЕШНИЙ ВИД =====
    public boolean setRankPrefix(String name, String prefix) {
        Rank rank = ranks.get(name);
        if (rank == null) return false;
        rank.setPrefix(prefix);
        saveRanks();
        return true;
    }

    public boolean setRankSuffix(String name, String suffix) {
        Rank rank = ranks.get(name);
        if (rank == null) return false;
        rank.setSuffix(suffix);
        saveRanks();
        return true;
    }

    public boolean setRankEmoji(String name, String emoji) {
        Rank rank = ranks.get(name);
        if (rank == null) return false;
        rank.setEmoji(emoji);
        saveRanks();
        return true;
    }

    public boolean setRankColor(String name, String color) {
        Rank rank = ranks.get(name);
        if (rank == null) return false;
        rank.setColor(color);
        saveRanks();
        return true;
    }

    public boolean setRankPriority(String name, int priority) {
        Rank rank = ranks.get(name);
        if (rank == null) return false;
        rank.setPriority(priority);
        saveRanks();
        return true;
    }

    // ===== ПРАВА =====
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

    public Map<String, String> getRankPermissions(String rankName) {
        Rank rank = ranks.get(rankName);
        return rank != null ? rank.getPermissions() : new HashMap<>();
    }

    public boolean hasPermission(long telegramId, String command) {
        String rankName = userRanks.get(telegramId);
        if (rankName == null) return false;
        Rank rank = ranks.get(rankName);
        if (rank == null) return false;
        
        // Проверяем свои права
        if (rank.hasPermission(command)) return true;
        
        // Проверяем наследование
        for (String inherit : rank.getInherits()) {
            Rank parent = ranks.get(inherit);
            if (parent != null && parent.hasPermission(command)) return true;
        }
        
        return false;
    }

    public String getCommandLimit(long telegramId, String command) {
        String rankName = userRanks.get(telegramId);
        if (rankName == null) return null;
        Rank rank = ranks.get(rankName);
        if (rank == null) return null;
        
        String limit = rank.getCommandLimit(command);
        if (limit != null) return limit;
        
        for (String inherit : rank.getInherits()) {
            Rank parent = ranks.get(inherit);
            if (parent != null) {
                limit = parent.getCommandLimit(command);
                if (limit != null) return limit;
            }
        }
        
        return null;
    }

    // ===== ПОЛЬЗОВАТЕЛИ В РАНГЕ =====
    public boolean addUserToRank(String rankName, long telegramId, String reason) {
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
        
        plugin.sendMessageAsBot(telegramId, "🔰 Вас добавили в ранг \"" + rankName + "\"!\n" +
                "📝 Причина: " + (reason != null ? reason : "Без причины"));
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
        tempRanks.remove(telegramId);
        saveRanks();
        
        plugin.sendMessageAsBot(telegramId, "🔰 Вас сняли с ранга \"" + rankName + "\"!\n" +
                "📝 Причина: " + (reason != null ? reason : "Без причины"));
        return true;
    }

    public boolean promoteUser(long telegramId, String reason) {
        String currentRank = userRanks.get(telegramId);
        if (currentRank == null) return false;
        
        List<String> sortedRanks = getSortedRankNames();
        int currentIndex = sortedRanks.indexOf(currentRank);
        if (currentIndex == -1 || currentIndex == 0) return false;
        
        String newRank = sortedRanks.get(currentIndex - 1);
        return addUserToRank(newRank, telegramId, reason != null ? reason : "Повышение");
    }

    public boolean demoteUser(long telegramId, String reason) {
        String currentRank = userRanks.get(telegramId);
        if (currentRank == null) return false;
        
        List<String> sortedRanks = getSortedRankNames();
        int currentIndex = sortedRanks.indexOf(currentRank);
        if (currentIndex == -1 || currentIndex == sortedRanks.size() - 1) return false;
        
        String newRank = sortedRanks.get(currentIndex + 1);
        return addUserToRank(newRank, telegramId, reason != null ? reason : "Понижение");
    }

    public boolean transferUser(String fromRank, String toRank, long telegramId) {
        if (!ranks.containsKey(fromRank) || !ranks.containsKey(toRank)) return false;
        if (!userRanks.containsKey(telegramId)) return false;
        if (!userRanks.get(telegramId).equals(fromRank)) return false;
        
        Rank from = ranks.get(fromRank);
        Rank to = ranks.get(toRank);
        from.removeUser(telegramId);
        to.addUser(telegramId);
        userRanks.put(telegramId, toRank);
        saveRanks();
        return true;
    }

    public boolean swapRanks(long id1, long id2) {
        String rank1 = userRanks.get(id1);
        String rank2 = userRanks.get(id2);
        if (rank1 == null || rank2 == null) return false;
        
        userRanks.put(id1, rank2);
        userRanks.put(id2, rank1);
        saveRanks();
        return true;
    }

    public boolean massAddUsers(String rankName, List<Long> ids, String reason) {
        Rank rank = ranks.get(rankName);
        if (rank == null) return false;
        
        for (long id : ids) {
            addUserToRank(rankName, id, reason);
        }
        return true;
    }

    public boolean massRemoveUsers(String rankName, List<Long> ids) {
        Rank rank = ranks.get(rankName);
        if (rank == null) return false;
        
        for (long id : ids) {
            if (userRanks.containsKey(id) && userRanks.get(id).equals(rankName)) {
                removeUserFromRank(id, "Массовое удаление");
            }
        }
        return true;
    }

    // ===== НАСЛЕДОВАНИЕ =====
    public boolean setInherit(String rankName, String parent) {
        Rank rank = ranks.get(rankName);
        if (rank == null) return false;
        if (!ranks.containsKey(parent)) return false;
        
        rank.getInherits().clear();
        rank.getInherits().add(parent);
        saveRanks();
        return true;
    }

    public boolean addInherit(String rankName, String parent) {
        Rank rank = ranks.get(rankName);
        if (rank == null) return false;
        if (!ranks.containsKey(parent)) return false;
        
        rank.getInherits().add(parent);
        saveRanks();
        return true;
    }

    public boolean removeInherit(String rankName, String parent) {
        Rank rank = ranks.get(rankName);
        if (rank == null) return false;
        
        rank.getInherits().remove(parent);
        saveRanks();
        return true;
    }

    public Set<String> getInherits(String rankName) {
        Rank rank = ranks.get(rankName);
        return rank != null ? rank.getInherits() : new HashSet<>();
    }

    public List<String> getInheritTree(String rankName) {
        List<String> tree = new ArrayList<>();
        Rank rank = ranks.get(rankName);
        if (rank == null) return tree;
        
        tree.add(rankName);
        for (String parent : rank.getInherits()) {
            tree.addAll(getInheritTree(parent));
        }
        return tree;
    }

    // ===== ВРЕМЕННЫЕ РАНГИ =====
    public boolean addTempRank(String rankName, long telegramId, long duration, String reason) {
        if (!ranks.containsKey(rankName)) return false;
        
        long expires = System.currentTimeMillis() + duration;
        tempRanks.put(telegramId, expires);
        
        boolean result = addUserToRank(rankName, telegramId, reason != null ? reason : "Временный ранг");
        if (result) {
            long days = duration / (24 * 60 * 60 * 1000);
            long hours = (duration % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
            String timeStr = days > 0 ? days + "д " + hours + "ч" : hours + "ч";
            
            plugin.sendMessageAsBot(telegramId, "⏳ Вам выдан временный ранг \"" + rankName + "\"!\n" +
                    "⏱ Срок: " + timeStr + "\n" +
                    "📝 Причина: " + (reason != null ? reason : "Без причины"));
        }
        return result;
    }

    public boolean removeTempRank(long telegramId, String reason) {
        if (!tempRanks.containsKey(telegramId)) return false;
        tempRanks.remove(telegramId);
        return removeUserFromRank(telegramId, reason != null ? reason : "Досрочное снятие");
    }

    public boolean extendTempRank(long telegramId, long extraDuration) {
        if (!tempRanks.containsKey(telegramId)) return false;
        long newExpires = tempRanks.get(telegramId) + extraDuration;
        tempRanks.put(telegramId, newExpires);
        saveRanks();
        
        long days = extraDuration / (24 * 60 * 60 * 1000);
        long hours = (extraDuration % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        String timeStr = days > 0 ? days + "д " + hours + "ч" : hours + "ч";
        
        plugin.sendMessageAsBot(telegramId, "⏳ Ваш временный ранг продлён на " + timeStr + "!");
        return true;
    }

    public Map<Long, Long> getTempRanks() {
        checkTempRanks();
        return new HashMap<>(tempRanks);
    }

    public long getTempRankTimeLeft(long telegramId) {
        if (!tempRanks.containsKey(telegramId)) return -1;
        long left = tempRanks.get(telegramId) - System.currentTimeMillis();
        return left > 0 ? left : 0;
    }

    // ===== ИНФОРМАЦИЯ =====
    public String getUserRank(long telegramId) {
        checkTempRanks();
        return userRanks.get(telegramId);
    }

    public Rank getRank(String name) {
        return ranks.get(name);
    }

    public List<String> getRankNames() {
        return new ArrayList<>(ranks.keySet());
    }

    public List<String> getSortedRankNames() {
        List<String> names = new ArrayList<>(ranks.keySet());
        names.sort((a, b) -> {
            int pa = ranks.get(a).getPriority();
            int pb = ranks.get(b).getPriority();
            return Integer.compare(pb, pa); // Высший приоритет — первый
        });
        return names;
    }

    public String getRankDisplay(long telegramId) {
        String rankName = userRanks.get(telegramId);
        if (rankName == null) return "";
        Rank rank = ranks.get(rankName);
        if (rank == null) return "";
        
        String display = "";
        if (!rank.getEmoji().isEmpty()) display += rank.getEmoji() + " ";
        if (!rank.getPrefix().isEmpty()) display += rank.getPrefix() + " ";
        if (!rank.getColor().isEmpty()) display += rank.getColor() + " ";
        return display.trim();
    }

    public String getFullRankDisplay(long telegramId) {
        String rankName = userRanks.get(telegramId);
        if (rankName == null) return "Без ранга";
        Rank rank = ranks.get(rankName);
        if (rank == null) return "Без ранга";
        
        String display = "";
        if (!rank.getEmoji().isEmpty()) display += rank.getEmoji() + " ";
        if (!rank.getPrefix().isEmpty()) display += rank.getPrefix() + " ";
        if (!rank.getColor().isEmpty()) display += rank.getColor();
        return display.trim();
    }

    public List<Long> getRankUsers(String rankName) {
        Rank rank = ranks.get(rankName);
        return rank != null ? new ArrayList<>(rank.getUsers()) : new ArrayList<>();
    }

    public int getRankUserCount(String rankName) {
        Rank rank = ranks.get(rankName);
        return rank != null ? rank.getUsers().size() : 0;
    }

    // ===== ОЧИСТКА =====
    public boolean clearRank(String rankName) {
        Rank rank = ranks.get(rankName);
        if (rank == null) return false;
        for (long id : rank.getUsers()) {
            userRanks.remove(id);
            tempRanks.remove(id);
        }
        rank.getUsers().clear();
        saveRanks();
        return true;
    }

    public boolean resetRank(String rankName) {
        Rank rank = ranks.get(rankName);
        if (rank == null) return false;
        rank.getPermissions().clear();
        saveRanks();
        return true;
    }

    public boolean wipeRank(String rankName) {
        if (!ranks.containsKey(rankName)) return false;
        Rank rank = ranks.get(rankName);
        for (long id : rank.getUsers()) {
            userRanks.remove(id);
            tempRanks.remove(id);
        }
        ranks.remove(rankName);
        rankConfig.set(rankName, null);
        saveRanks();
        return true;
    }

    public void clearAllRanks() {
        ranks.clear();
        userRanks.clear();
        allUsers.clear();
        tempRanks.clear();
        saveRanks();
    }

    // ===== СТАТИСТИКА =====
    public Map<String, Integer> getRankStats() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (String name : getSortedRankNames()) {
            stats.put(name, getRankUserCount(name));
        }
        return stats;
    }

    public int getTotalUsers() {
        return allUsers.size();
    }

    public int getTotalRanks() {
        return ranks.size();
    }

    // ===== КЛАСС РАНГА =====
    public static class Rank {
        private String name;
        private String prefix = "";
        private String suffix = "";
        private String emoji = "";
        private String color = "";
        private int priority = 0;
        private final Map<String, String> permissions = new HashMap<>();
        private final Set<String> inherits = new HashSet<>();
        private final List<Long> users = new ArrayList<>();

        public Rank(String name) {
            this.name = name;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }

        public String getSuffix() { return suffix; }
        public void setSuffix(String suffix) { this.suffix = suffix; }

        public String getEmoji() { return emoji; }
        public void setEmoji(String emoji) { this.emoji = emoji; }

        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }

        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }

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

        public Set<String> getInherits() { return inherits; }
        public void setInherits(Set<String> inherits) { this.inherits.clear(); this.inherits.addAll(inherits); }

        public List<Long> getUsers() { return users; }

        public void addUser(long telegramId) {
            if (!users.contains(telegramId)) users.add(telegramId);
        }

        public void removeUser(long telegramId) {
            users.remove(telegramId);
        }

        public String getDisplayName() {
            return emoji + " " + prefix;
        }
    }
}
