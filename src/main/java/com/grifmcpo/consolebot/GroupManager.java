package com.grifmcpo.consolebot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class GroupManager {

    private final TelegramConsoleBot plugin;
    private final File groupsFile;
    private Map<String, Group> groups = new HashMap<>();

    public GroupManager(TelegramConsoleBot plugin) {
        this.plugin = plugin;
        this.groupsFile = new File(plugin.getDataFolder(), "groups.yml");
        loadGroups();
    }

    private void loadGroups() {
        if (!groupsFile.exists()) {
            plugin.saveResource("groups.yml", false);
        }
        reloadGroups();
    }

    public void reloadGroups() {
        groups.clear();
        if (!groupsFile.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(groupsFile);
        for (String groupName : config.getKeys(false)) {
            Group group = new Group(groupName);
            List<String> permissions = config.getStringList(groupName + ".permissions");
            List<Long> members = config.getLongList(groupName + ".members");
            String prefix = config.getString(groupName + ".prefix", "");
            String suffix = config.getString(groupName + ".suffix", "");

            group.setPermissions(new HashSet<>(permissions));
            
            // ИСПРАВЛЕНО: конвертируем Long в Integer
            Set<Integer> memberSet = new HashSet<>();
            for (long member : members) {
                memberSet.add((int) member); // Явное приведение long к int
            }
            group.setMembers(memberSet);
            
            group.setPrefix(prefix);
            group.setSuffix(suffix);
            groups.put(groupName, group);
        }
        plugin.getLogger().info("✅ Загружено групп: " + groups.size());
    }

    public void saveGroups() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, Group> entry : groups.entrySet()) {
            String name = entry.getKey();
            Group group = entry.getValue();
            config.set(name + ".permissions", new ArrayList<>(group.getPermissions()));
            
            // ИСПРАВЛЕНО: конвертируем Integer в Long для сохранения
            List<Long> members = new ArrayList<>();
            for (int member : group.getMembers()) {
                members.add((long) member);
            }
            config.set(name + ".members", members);
            
            config.set(name + ".prefix", group.getPrefix());
            config.set(name + ".suffix", group.getSuffix());
        }
        try {
            config.save(groupsFile);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка сохранения groups.yml: " + e.getMessage());
        }
    }

    // ============================================
    // ==== МЕТОДЫ РАБОТЫ С ГРУППАМИ =====
    // ============================================

    public boolean createGroup(String name) {
        if (groups.containsKey(name)) return false;
        groups.put(name, new Group(name));
        saveGroups();
        return true;
    }

    public boolean deleteGroup(String name) {
        if (!groups.containsKey(name)) return false;
        groups.remove(name);
        saveGroups();
        return true;
    }

    public boolean addMember(String groupName, long userId) {
        Group group = groups.get(groupName);
        if (group == null) return false;
        // ИСПРАВЛЕНО: конвертируем long в int
        group.getMembers().add((int) userId);
        saveGroups();
        return true;
    }

    public boolean removeMember(String groupName, long userId) {
        Group group = groups.get(groupName);
        if (group == null) return false;
        // ИСПРАВЛЕНО: конвертируем long в int
        return group.getMembers().remove((int) userId);
    }

    public boolean addPermission(String groupName, String permission) {
        Group group = groups.get(groupName);
        if (group == null) return false;
        group.getPermissions().add(permission);
        saveGroups();
        return true;
    }

    public boolean removePermission(String groupName, String permission) {
        Group group = groups.get(groupName);
        if (group == null) return false;
        return group.getPermissions().remove(permission);
    }

    public boolean setPrefix(String groupName, String prefix) {
        Group group = groups.get(groupName);
        if (group == null) return false;
        group.setPrefix(prefix);
        saveGroups();
        return true;
    }

    public boolean setSuffix(String groupName, String suffix) {
        Group group = groups.get(groupName);
        if (group == null) return false;
        group.setSuffix(suffix);
        saveGroups();
        return true;
    }

    // ============================================
    // ==== ГЕТТЕРЫ =====
    // ============================================

    public Group getGroup(String name) {
        return groups.get(name);
    }

    public Set<String> getGroupNames() {
        return groups.keySet();
    }

    public String getGroupNameByUser(long userId) {
        // ИСПРАВЛЕНО: конвертируем long в int для поиска
        int id = (int) userId;
        for (Map.Entry<String, Group> entry : groups.entrySet()) {
            if (entry.getValue().getMembers().contains(id)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public List<String> getGroupPermissions(String groupName) {
        Group group = groups.get(groupName);
        return group != null ? new ArrayList<>(group.getPermissions()) : new ArrayList<>();
    }

    public boolean hasGroupPermission(String groupName, String permission) {
        Group group = groups.get(groupName);
        return group != null && group.getPermissions().contains(permission);
    }

    // ============================================
    // ==== ВНУТРЕННИЙ КЛАСС GROUP =====
    // ============================================

    public static class Group {
        private final String name;
        private Set<Integer> members = new HashSet<>();
        private Set<String> permissions = new HashSet<>();
        private String prefix = "";
        private String suffix = "";

        public Group(String name) {
            this.name = name;
        }

        public String getName() { return name; }
        public Set<Integer> getMembers() { return members; }
        public void setMembers(Set<Integer> members) { this.members = members; }
        public Set<String> getPermissions() { return permissions; }
        public void setPermissions(Set<String> permissions) { this.permissions = permissions; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
        public String getSuffix() { return suffix; }
        public void setSuffix(String suffix) { this.suffix = suffix; }
    }
}
