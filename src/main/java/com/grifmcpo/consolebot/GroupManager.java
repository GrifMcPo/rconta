package com.grifmcpo.consolebot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.RestrictChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.UnbanChatMember;
import org.telegram.telegrambots.meta.api.objects.ChatPermissions;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GroupManager {

    private final TelegramConsoleBot plugin;
    private File groupFile;
    private FileConfiguration groupConfig;
    private final Map<Long, GroupData> groups = new ConcurrentHashMap<>();
    private final Map<Long, Long> groupOwners = new ConcurrentHashMap<>(); // groupId -> ownerId

    public GroupManager(TelegramConsoleBot plugin) {
        this.plugin = plugin;
        loadGroups();
    }

    private void loadGroups() {
        groupFile = new File(plugin.getDataFolder(), "groups.yml");
        if (!groupFile.exists()) {
            try {
                groupFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("❌ Не удалось создать groups.yml");
            }
        }
        groupConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(groupFile);
        loadGroupsFromConfig();
    }

    private void loadGroupsFromConfig() {
        groups.clear();
        groupOwners.clear();

        for (String key : groupConfig.getKeys(false)) {
            if (key.equals("nextId")) continue;
            long groupId = Long.parseLong(key);
            long ownerId = groupConfig.getLong(key + ".owner");
            String groupName = groupConfig.getString(key + ".name", "Неизвестно");
            boolean isBanned = groupConfig.getBoolean(key + ".isBanned", false);

            GroupData group = new GroupData(groupId, ownerId, groupName);
            group.setBanned(isBanned);
            groups.put(groupId, group);
            groupOwners.put(groupId, ownerId);
        }

        plugin.getLogger().info("✅ Загружено групп: " + groups.size());
    }

    public void saveGroups() {
        for (Map.Entry<Long, GroupData> entry : groups.entrySet()) {
            long groupId = entry.getKey();
            GroupData group = entry.getValue();
            groupConfig.set(String.valueOf(groupId) + ".owner", group.getOwnerId());
            groupConfig.set(String.valueOf(groupId) + ".name", group.getName());
            groupConfig.set(String.valueOf(groupId) + ".isBanned", group.isBanned());
        }
        try {
            groupConfig.save(groupFile);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка сохранения groups.yml: " + e.getMessage());
        }
    }

    // ===== ПРИВЯЗКА ГРУППЫ =====
    public boolean linkGroup(long groupId, long ownerId, String groupName) {
        if (groups.containsKey(groupId)) return false;

        // Проверяем, не привязана ли уже группа к этому владельцу
        for (GroupData g : groups.values()) {
            if (g.getOwnerId() == ownerId) {
                return false; // У владельца уже есть группа
            }
        }

        GroupData group = new GroupData(groupId, ownerId, groupName);
        groups.put(groupId, group);
        groupOwners.put(groupId, ownerId);
        saveGroups();
        return true;
    }

    public boolean unlinkGroup(long groupId) {
        if (!groups.containsKey(groupId)) return false;
        groups.remove(groupId);
        groupOwners.remove(groupId);
        groupConfig.set(String.valueOf(groupId), null);
        saveGroups();
        return true;
    }

    public boolean isGroupLinked(long groupId) {
        return groups.containsKey(groupId);
    }

    public Long getGroupOwner(long groupId) {
        return groupOwners.get(groupId);
    }

    public List<Long> getAllGroups() {
        return new ArrayList<>(groups.keySet());
    }

    // ===== БАН В ГРУППЕ =====
    public boolean banUser(long groupId, long userId, long chatId, String reason, String duration) {
        if (!groups.containsKey(groupId)) return false;

        try {
            BanChatMember ban = new BanChatMember();
            ban.setChatId(String.valueOf(groupId));
            ban.setUserId(userId);

            plugin.getBotHandler().execute(ban);

            // Сохраняем бан
            groups.get(groupId).addBan(userId, reason, duration);
            saveGroups();
            return true;
        } catch (TelegramApiException e) {
            plugin.getLogger().warning("❌ Ошибка бана в группе: " + e.getMessage());
            return false;
        }
    }

    public boolean unbanUser(long groupId, long userId, String reason) {
        if (!groups.containsKey(groupId)) return false;

        try {
            UnbanChatMember unban = new UnbanChatMember();
            unban.setChatId(String.valueOf(groupId));
            unban.setUserId(userId);

            plugin.getBotHandler().execute(unban);

            groups.get(groupId).removeBan(userId);
            saveGroups();
            return true;
        } catch (TelegramApiException e) {
            plugin.getLogger().warning("❌ Ошибка разбана в группе: " + e.getMessage());
            return false;
        }
    }

    // ===== МУТ В ГРУППЕ =====
    public boolean muteUser(long groupId, long userId, String reason, String duration) {
        if (!groups.containsKey(groupId)) return false;

        try {
            RestrictChatMember mute = new RestrictChatMember();
            mute.setChatId(String.valueOf(groupId));
            mute.setUserId(userId);

            ChatPermissions permissions = new ChatPermissions();
            permissions.setCanSendMessages(false);
            permissions.setCanSendMediaMessages(false);
            permissions.setCanSendOtherMessages(false);
            mute.setPermissions(permissions);

            // Устанавливаем время
            if (!duration.equals("навсегда")) {
                long timeInSeconds = parseTimeToSeconds(duration);
                mute.setUntilDate(System.currentTimeMillis() / 1000 + timeInSeconds);
            }

            plugin.getBotHandler().execute(mute);

            groups.get(groupId).addMute(userId, reason, duration);
            saveGroups();
            return true;
        } catch (TelegramApiException e) {
            plugin.getLogger().warning("❌ Ошибка мута в группе: " + e.getMessage());
            return false;
        }
    }

    public boolean unmuteUser(long groupId, long userId, String reason) {
        if (!groups.containsKey(groupId)) return false;

        try {
            RestrictChatMember unmute = new RestrictChatMember();
            unmute.setChatId(String.valueOf(groupId));
            unmute.setUserId(userId);

            ChatPermissions permissions = new ChatPermissions();
            permissions.setCanSendMessages(true);
            permissions.setCanSendMediaMessages(true);
            permissions.setCanSendOtherMessages(true);
            unmute.setPermissions(permissions);

            plugin.getBotHandler().execute(unmute);

            groups.get(groupId).removeMute(userId);
            saveGroups();
            return true;
        } catch (TelegramApiException e) {
            plugin.getLogger().warning("❌ Ошибка размута в группе: " + e.getMessage());
            return false;
        }
    }

    private long parseTimeToSeconds(String time) {
        if (time == null || time.equals("навсегда")) return 0;
        char unit = time.charAt(time.length() - 1);
        long value = Long.parseLong(time.substring(0, time.length() - 1));
        switch (unit) {
            case 'm': return value * 60;
            case 'h': return value * 60 * 60;
            case 'd': return value * 24 * 60 * 60;
            case 'w': return value * 7 * 24 * 60 * 60;
            case 'M': return value * 30L * 24 * 60 * 60;
            case 'y': return value * 365L * 24 * 60 * 60;
            default: return 0;
        }
    }

    // ===== КЛАСС ДАННЫХ ГРУППЫ =====
    public static class GroupData {
        private final long groupId;
        private final long ownerId;
        private final String name;
        private boolean isBanned = false;
        private final Map<Long, BanData> bans = new HashMap<>();
        private final Map<Long, MuteData> mutes = new HashMap<>();

        public GroupData(long groupId, long ownerId, String name) {
            this.groupId = groupId;
            this.ownerId = ownerId;
            this.name = name;
        }

        public long getGroupId() { return groupId; }
        public long getOwnerId() { return ownerId; }
        public String getName() { return name; }
        public boolean isBanned() { return isBanned; }
        public void setBanned(boolean banned) { isBanned = banned; }

        public void addBan(long userId, String reason, String duration) {
            bans.put(userId, new BanData(userId, reason, duration, System.currentTimeMillis()));
        }

        public void removeBan(long userId) {
            bans.remove(userId);
        }

        public void addMute(long userId, String reason, String duration) {
            mutes.put(userId, new MuteData(userId, reason, duration, System.currentTimeMillis()));
        }

        public void removeMute(long userId) {
            mutes.remove(userId);
        }

        public boolean isMuted(long userId) {
            return mutes.containsKey(userId);
        }

        public boolean isBanned(long userId) {
            return bans.containsKey(userId);
        }
    }

    public static class BanData {
        public final long userId;
        public final String reason;
        public final String duration;
        public final long timestamp;

        public BanData(long userId, String reason, String duration, long timestamp) {
            this.userId = userId;
            this.reason = reason;
            this.duration = duration;
            this.timestamp = timestamp;
        }
    }

    public static class MuteData {
        public final long userId;
        public final String reason;
        public final String duration;
        public final long timestamp;

        public MuteData(long userId, String reason, String duration, long timestamp) {
            this.userId = userId;
            this.reason = reason;
            this.duration = duration;
            this.timestamp = timestamp;
        }
    }
}
