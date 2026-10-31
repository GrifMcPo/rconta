package com.grifmcpo.consolebot;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class AccountLinkManager {

    private static final long LINK_CODE_TTL_MS = TimeUnit.MINUTES.toMillis(10);
    private final JavaPlugin plugin;
    private final File pendingLinksFile;
    private final File linkedAccountsFile;
    private final File pendingUnlinksFile;

    public AccountLinkManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.pendingLinksFile = new File(plugin.getDataFolder(), "pending_links.txt");
        this.linkedAccountsFile = new File(plugin.getDataFolder(), "linked_accounts.txt");
        this.pendingUnlinksFile = new File(plugin.getDataFolder(), "pending_unlinks.txt");
        ensureFiles();
    }

    private void ensureFiles() {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку плагина для привязок.");
            }
            if (!pendingLinksFile.exists() && !pendingLinksFile.createNewFile()) {
                plugin.getLogger().warning("Не удалось создать pending_links.txt");
            }
            if (!linkedAccountsFile.exists() && !linkedAccountsFile.createNewFile()) {
                plugin.getLogger().warning("Не удалось создать linked_accounts.txt");
            }
            if (!pendingUnlinksFile.exists() && !pendingUnlinksFile.createNewFile()) {
                plugin.getLogger().warning("Не удалось создать pending_unlinks.txt");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Ошибка создания файлов привязок: " + e.getMessage());
        }
    }

    public String createCodeForPlayer(Player player) {
        if (player == null) {
            return "";
        }
        String code = generateCode();
        long expiresAt = System.currentTimeMillis() + LINK_CODE_TTL_MS;
        String line = player.getName() + "|" + player.getUniqueId() + "|" + code + "|" + expiresAt + "|false";

        List<String> existing = readLines(pendingLinksFile);
        existing.add(line);
        writeLines(pendingLinksFile, existing);
        return code;
    }

    public String findPlayerNameByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }

        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        for (String line : readLines(pendingLinksFile)) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\|");
            if (parts.length < 5) {
                continue;
            }

            String savedCode = parts[2];
            long expiresAt = parseLong(parts[3], 0L);
            String savedUsed = parts[4];

            if (!savedCode.equalsIgnoreCase(normalizedCode)) {
                continue;
            }
            if (Boolean.parseBoolean(savedUsed)) {
                continue;
            }
            if (expiresAt > 0 && expiresAt < System.currentTimeMillis()) {
                continue;
            }

            return parts[0];
        }
        return null;
    }

    public LinkStatus completeTelegramLink(String code, long telegramId, String telegramUsername) {
        String playerName = findPlayerNameByCode(code);
        if (playerName == null) {
            return new LinkStatus(false, "Код не найден или срок его действия истёк.", "NOT_FOUND");
        }
        if (isPlayerLinked(playerName)) {
            return new LinkStatus(false, "Этот аккаунт уже привязан.", "ALREADY_LINKED");
        }
        if (isTelegramLinked(telegramId)) {
            return new LinkStatus(false, "Этот Telegram уже привязан к другому аккаунту.", "ALREADY_LINKED_TELEGRAM");
        }
        return linkPlayerByCode(playerName, code, telegramId, telegramUsername);
    }

    public LinkStatus linkPlayerByCode(String playerName, String code, long telegramId, String telegramUsername) {
        if (playerName == null || code == null || code.trim().isEmpty()) {
            return new LinkStatus(false, "Неверный код привязки.", "INVALID");
        }

        String normalizedPlayer = playerName.trim();
        String normalizedCode = code.trim();

        if (isPlayerLinked(normalizedPlayer)) {
            return new LinkStatus(false, "Этот аккаунт уже привязан.", "ALREADY_LINKED");
        }

        if (isTelegramLinked(telegramId)) {
            return new LinkStatus(false, "Этот Telegram уже привязан к другому аккаунту.", "ALREADY_LINKED_TELEGRAM");
        }

        List<String> lines = readLines(pendingLinksFile);
        List<String> updated = new ArrayList<>();
        boolean found = false;

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\|");
            if (parts.length < 5) {
                updated.add(line);
                continue;
            }

            String savedName = parts[0];
            String savedCode = parts[2];
            String savedUsed = parts[4];
            long expiresAt = parseLong(parts[3], 0L);

            if (savedName.equalsIgnoreCase(normalizedPlayer) && savedCode.equalsIgnoreCase(normalizedCode)) {
                if (Boolean.parseBoolean(savedUsed)) {
                    updated.add(line);
                    return new LinkStatus(false, "Код уже использован.", "USED");
                }
                if (expiresAt > 0 && expiresAt < System.currentTimeMillis()) {
                    continue;
                }
                found = true;
                String uuid = parts[1];
                String linkedLine = normalizedPlayer + "|" + uuid + "|" + telegramId + "|" + safeUsername(telegramUsername) + "|" + System.currentTimeMillis();
                appendLine(linkedAccountsFile, linkedLine);
                updated.add(savedName + "|" + uuid + "|" + savedCode + "|" + expiresAt + "|true");
                writeLines(pendingLinksFile, updated);
                return new LinkStatus(true, "Привязка успешно выполнена.", "SUCCESS");
            }
            updated.add(line);
        }

        if (!found) {
            return new LinkStatus(false, "Код не найден или срок действия истёк.", "NOT_FOUND");
        }

        writeLines(pendingLinksFile, updated);
        return new LinkStatus(false, "Не удалось завершить привязку.", "ERROR");
    }

    public boolean isPlayerLinked(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return false;
        }
        for (String line : readLines(linkedAccountsFile)) {
            String[] parts = line.split("\\|");
            if (parts.length < 2) {
                continue;
            }
            if (parts[0].equalsIgnoreCase(playerName.trim())) {
                return true;
            }
        }
        return false;
    }

    public boolean isTelegramLinked(long telegramId) {
        for (String line : readLines(linkedAccountsFile)) {
            String[] parts = line.split("\\|");
            if (parts.length < 3) {
                continue;
            }
            try {
                if (Long.parseLong(parts[2]) == telegramId) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    public String getLinkedPlayerNameByTelegramId(long telegramId) {
        for (String line : readLines(linkedAccountsFile)) {
            String[] parts = line.split("\\|");
            if (parts.length < 3) {
                continue;
            }
            try {
                if (Long.parseLong(parts[2]) == telegramId) {
                    return parts[0];
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public boolean createUnlinkRequest(String playerName, long telegramId, String reason) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return false;
        }
        if (!isPlayerLinked(playerName)) {
            return false;
        }

        String linkedLine = findLinkedAccountLine(playerName);
        if (linkedLine == null) {
            return false;
        }

        String[] parts = linkedLine.split("\\|");
        String uuid = parts.length > 1 ? parts[1] : "";
        String username = parts.length > 3 ? parts[3] : "";
        String safeReason = reason == null || reason.trim().isEmpty() ? "Без причины" : reason.trim();
        String request = playerName + "|" + uuid + "|" + telegramId + "|" + safeUsername(username) + "|" + safeReason + "|" + System.currentTimeMillis();
        appendLine(pendingUnlinksFile, request);
        return true;
    }

    public UnlinkApproval approveUnlink(String playerName) {
        List<String> pending = readLines(pendingUnlinksFile);
        List<String> remaining = new ArrayList<>();
        UnlinkApproval approval = null;

        for (String line : pending) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\|");
            if (parts.length < 5) {
                remaining.add(line);
                continue;
            }
            if (parts[0].equalsIgnoreCase(playerName.trim())) {
                long telegramId = parseLong(parts[2], -1L);
                String username = parts[3];
                String reason = parts[4];
                approval = new UnlinkApproval(playerName, telegramId, username, reason);
                continue;
            }
            remaining.add(line);
        }

        writeLines(pendingUnlinksFile, remaining);
        if (approval == null) {
            return null;
        }

        List<String> linked = readLines(linkedAccountsFile);
        List<String> updatedLinked = new ArrayList<>();
        for (String line : linked) {
            String[] parts = line.split("\\|");
            if (parts.length < 2) {
                updatedLinked.add(line);
                continue;
            }
            if (!parts[0].equalsIgnoreCase(playerName.trim())) {
                updatedLinked.add(line);
            }
        }
        writeLines(linkedAccountsFile, updatedLinked);
        return approval;
    }

    private String findLinkedAccountLine(String playerName) {
        for (String line : readLines(linkedAccountsFile)) {
            String[] parts = line.split("\\|");
            if (parts.length > 0 && parts[0].equalsIgnoreCase(playerName.trim())) {
                return line;
            }
        }
        return null;
    }

    private String generateCode() {
        Random random = new Random();
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        String code = sb.toString();
        for (String line : readLines(pendingLinksFile)) {
            String[] parts = line.split("\\|");
            if (parts.length >= 3 && parts[2].equalsIgnoreCase(code)) {
                return generateCode();
            }
        }
        return code;
    }

    private List<String> readLines(File file) {
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try {
            return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Ошибка чтения файла " + file.getName() + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void writeLines(File file, List<String> lines) {
        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            plugin.getLogger().warning("Ошибка записи файла " + file.getName() + ": " + e.getMessage());
        }
    }

    private void appendLine(File file, String line) {
        List<String> current = readLines(file);
        current.add(line);
        writeLines(file, current);
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private String safeUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "unknown";
        }
        return username.trim();
    }

    public static class LinkStatus {
        private final boolean success;
        private final String message;
        private final String code;

        public LinkStatus(boolean success, String message, String code) {
            this.success = success;
            this.message = message;
            this.code = code;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getCode() {
            return code;
        }
    }

    public static class UnlinkApproval {
        private final String playerName;
        private final long telegramId;
        private final String username;
        private final String reason;

        public UnlinkApproval(String playerName, long telegramId, String username, String reason) {
            this.playerName = playerName;
            this.telegramId = telegramId;
            this.username = username;
            this.reason = reason;
        }

        public String getPlayerName() {
            return playerName;
        }

        public long getTelegramId() {
            return telegramId;
        }

        public String getUsername() {
            return username;
        }

        public String getReason() {
            return reason;
        }
    }
}
