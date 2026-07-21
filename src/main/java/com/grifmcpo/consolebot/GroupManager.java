package com.grifmcpo.consolebot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GroupManager {

    private final TelegramConsoleBot plugin;
    private File groupsFile;
    private FileConfiguration groupsConfig;
    private final Map<String, List<Long>> groups = new ConcurrentHashMap<>();
    private final Map<String, List<String>> groupPermissions = new ConcurrentHashMap<>();
    private final Map<Long, String> userGroup = new ConcurrentHashMap<>();

    public GroupManager(TelegramConsoleBot plugin) {
        this.plugin = plugin;
        loadGroups();
    }

    private void loadGroups() {
        groupsFile = new File(plugin.getDataFolder(), "groups.yml");
        if (!groupsFile.exists()) {
            try {
                groupsFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("❌ Не удалось создать groups.yml");
            }
        }
        groupsConfig = YamlConfiguration.loadConfiguration(groupsFile);
        loadGroupsFromConfig();
    }

    private void loadGroupsFromConfig() {
        groups.clear();
        userGroup.clear();
        groupPermissions.clear();

        for (String groupName : groupsConfig.getKeys(false)) {
            List<Long> users = groupsConfig.getLongList(groupName);
            groups.put(groupName, users);
            for (long id : users) {
                userGroup.put(id, groupName);
            }

            // Загружаем права группы
            List<String> perms = groupsConfig.getStringList(groupName + ".permissions");
            if (perms != null && !perms.isEmpty()) {
                groupPermissions.put(groupName, perms);
            }
        }

        plugin.getLogger().info("✅ Загружено групп: " + groups.size());
    }

    public void saveGroups() {
        for (Map.Entry<String, List<Long>> entry : groups.entrySet()) {
            groupsConfig.set(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, List<String>> entry : groupPermissions.entrySet()) {
            groupsConfig.set(entry.getKey() + ".permissions", entry.getValue());
        }
        try {
            groupsConfig.save(groupsFile);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка сохранения groups.yml: " + e.getMessage());
        }
    }

    public String getUserGroup(long telegramId) {
        return userGroup.get(telegramId);
    }

    public boolean hasPermission(long telegramId, String command) {
        String group = userGroup.get(telegramId);
        if (group == null) return false;

        List<String> perms = groupPermissions.get(group);
        if (perms == null) return false;

        if (perms.contains("ALL")) return true;

        for (String perm : perms) {
            if (command.startsWith(perm) || command.equals(perm)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getAvailableCommands(long telegramId) {
        String group = userGroup.get(telegramId);
        if (group == null) return new ArrayList<>();

        List<String> perms = groupPermissions.get(group);
        if (perms == null) return new ArrayList<>();

        if (perms.contains("ALL")) {
            return Arrays.asList(
                "ban", "unban", "mute", "unmute", "kick",
                "banlist", "mutelist", "shist", "hist", "logs",
                "checkban", "checkmute", "warn", "unwarn",
                "checkwarn", "whois", "seen", "iphist",
                "dupeip", "alts", "checkalts", "staffhistory",
                "banip", "bc", "messageall"
            );
        }

        List<String> commands = new ArrayList<>();
        for (String perm : perms) {
            String cmd = perm.replace("!rcon global ", "");
            commands.add(cmd);
        }
        return commands;
    }

    public boolean isAdmin(long telegramId) {
        String group = userGroup.get(telegramId);
        return group != null && (group.equals("admin") || group.equals("owner") || group.equals("leader"));
    }

    public boolean isOwner(long telegramId) {
        String group = userGroup.get(telegramId);
        return group != null && group.equals("owner");
    }

    public void reload() {
        loadGroups();
    }
}
