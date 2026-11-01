package com.grifmcpo.consolebot.storage;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class DatabaseManager {

    private static final List<String> EXPECTED_TABLES = Arrays.asList(
            "punishments", "ip_history", "command_logs", "admins"
    );

    private final JavaPlugin plugin;
    private final File databaseFile;
    private final Map<String, List<Map<String, Object>>> activePunishmentCache = new HashMap<>();
    private Connection connection;
    private boolean initialized;
    private boolean storageAvailable;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку данных плагина: " + dataFolder.getAbsolutePath());
        }
        this.databaseFile = new File(dataFolder, "data.db");
        this.storageAvailable = false;
    }

    public synchronized void loadActivePunishmentCache() {
        activePunishmentCache.clear();
        if (!ensureConnection()) {
            return;
        }
        List<Map<String, Object>> rows = getAllActivePunishmentsSync();
        for (Map<String, Object> row : rows) {
            String targetName = (String) row.get("target_name");
            if (targetName == null || targetName.trim().isEmpty()) {
                continue;
            }
            activePunishmentCache.computeIfAbsent(targetName.toLowerCase(), key -> new ArrayList<>()).add(row);
        }
        plugin.getLogger().info("Punishment cache loaded: " + activePunishmentCache.size() + " active entries");
    }

    public synchronized List<Map<String, Object>> getCachedActivePunishments(String targetName) {
        if (targetName == null || targetName.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> cached = activePunishmentCache.get(targetName.toLowerCase());
        return cached == null ? new ArrayList<>() : new ArrayList<>(cached);
    }

    public synchronized void invalidateActivePunishmentCache(String targetName) {
        if (targetName == null || targetName.trim().isEmpty()) {
            return;
        }
        activePunishmentCache.remove(targetName.toLowerCase());
    }

    public synchronized void invalidateAllActivePunishmentCache() {
        activePunishmentCache.clear();
    }

    public synchronized void init() {
        if (initialized && connection != null) {
            try {
                if (!connection.isClosed()) {
                    storageAvailable = true;
                    plugin.getLogger().info("SQLite init complete: tables=[punishments, ip_history, command_logs, admins], storageAvailable=true");
                    return;
                }
            } catch (SQLException ignored) {
                // continue reopen
            }
        }

        storageAvailable = false;
        try {
            plugin.getLogger().info("SQLite init: opening connection to " + databaseFile.getAbsolutePath());
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());

            try (Statement statement = connection.createStatement()) {
                plugin.getLogger().info("SQLite init: applying PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA journal_mode=WAL;");
                plugin.getLogger().info("SQLite init: applying PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA foreign_keys=ON;");
                plugin.getLogger().info("SQLite init: applying PRAGMA busy_timeout=5000");
                statement.execute("PRAGMA busy_timeout = 5000;");

                plugin.getLogger().info("SQLite init: creating table punishments");
                statement.execute("CREATE TABLE IF NOT EXISTS punishments ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "target_uuid TEXT, "
                        + "target_name TEXT NOT NULL, "
                        + "type TEXT NOT NULL, "
                        + "reason TEXT, "
                        + "operator TEXT NOT NULL, "
                        + "created_at INTEGER NOT NULL, "
                        + "expires_at INTEGER DEFAULT 0, "
                        + "active INTEGER DEFAULT 1, "
                        + "ip_address TEXT, "
                        + "source TEXT DEFAULT 'manual')");

                plugin.getLogger().info("SQLite init: creating table ip_history");
                statement.execute("CREATE TABLE IF NOT EXISTS ip_history ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "player_uuid TEXT NOT NULL, "
                        + "player_name TEXT NOT NULL, "
                        + "ip_address TEXT NOT NULL, "
                        + "seen_at INTEGER NOT NULL)");

                plugin.getLogger().info("SQLite init: creating index idx_ip_history_uuid");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_ip_history_uuid ON ip_history(player_uuid, seen_at)");
                plugin.getLogger().info("SQLite init: creating index idx_ip_history_ip");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_ip_history_ip ON ip_history(ip_address, seen_at)");

                plugin.getLogger().info("SQLite init: creating table command_logs");
                statement.execute("CREATE TABLE IF NOT EXISTS command_logs ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "executor TEXT NOT NULL, "
                        + "command TEXT NOT NULL, "
                        + "result TEXT, "
                        + "created_at INTEGER NOT NULL)");

                plugin.getLogger().info("SQLite init: creating table admins");
                statement.execute("CREATE TABLE IF NOT EXISTS admins ("
                        + "uuid TEXT PRIMARY KEY, "
                        + "name TEXT, "
                        + "added_by TEXT, "
                        + "added_at INTEGER NOT NULL)");

                plugin.getLogger().info("SQLite init: creating index idx_admins_name");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_admins_name ON admins(name)");
            }

            initialized = true;
            storageAvailable = true;
            plugin.getLogger().info("SQLite init complete: tables=[punishments, ip_history, command_logs, admins], storageAvailable=true");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found in classpath; continuing with storage disabled.", e);
            initialized = false;
            storageAvailable = false;
            connection = null;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка инициализации SQLite.", e);
            initialized = false;
            storageAvailable = false;
            connection = null;
        }
    }

    public synchronized void verifySchema() {
        init();
        if (connection == null || !storageAvailable) {
            plugin.getLogger().severe("Schema verify: storage unavailable, no DB tables checked");
            return;
        }

        List<String> found = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                found.add(resultSet.getString("name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Schema verify failed while querying sqlite_master.", e);
            storageAvailable = false;
            return;
        }

        List<String> missing = new ArrayList<>();
        for (String expected : EXPECTED_TABLES) {
            if (!found.contains(expected)) {
                missing.add(expected);
            }
        }

        int expected = EXPECTED_TABLES.size();
        int foundCount = found.size();
        plugin.getLogger().info("Schema verify: expected=" + expected + ", found=" + foundCount + ", missing=" + missing);

        if (!missing.isEmpty()) {
            plugin.getLogger().log(Level.SEVERE, "Missing SQLite tables: " + missing + ". Storage disabled.");
            storageAvailable = false;
        }
    }

    public synchronized void close() {
        if (connection == null) {
            initialized = false;
            storageAvailable = false;
            return;
        }

        try {
            connection.close();
            plugin.getLogger().info("SQLite connection closed: " + databaseFile.getAbsolutePath());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка закрытия SQLite.", e);
        } finally {
            connection = null;
            initialized = false;
            storageAvailable = false;
        }
    }

    public boolean isStorageAvailable() {
        return storageAvailable;
    }

    public boolean hasAnyAdminRecords() {
        if (!storageAvailable || connection == null) {
            return false;
        }

        String sql = "SELECT COUNT(*) FROM admins";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet set = statement.executeQuery()) {
            if (set.next()) {
                return set.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logException("hasAnyAdminRecords", e);
        }
        return false;
    }

    public void migrateAdminsFromYaml(File adminsFile, Map<String, String> admins) {
        if (adminsFile == null || !adminsFile.exists()) {
            return;
        }
        if (!storageAvailable || connection == null) {
            plugin.getLogger().warning("SQLite storage is not available; skipping admins.yml migration.");
            return;
        }

        try {
            if (!hasAnyAdminRecords()) {
                int imported = 0;
                for (Map.Entry<String, String> entry : admins.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null || entry.getValue().trim().isEmpty()) {
                        continue;
                    }
                    String sql = "INSERT OR IGNORE INTO admins(uuid, name, added_by, added_at) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setString(1, entry.getKey());
                        statement.setString(2, entry.getValue());
                        statement.setString(3, "migrated-from-admins-yml");
                        statement.setLong(4, System.currentTimeMillis());
                        if (statement.executeUpdate() > 0) {
                            imported++;
                        }
                    }
                }
                plugin.getLogger().info("Migrated admins.yml: " + imported + " entries");
            } else {
                plugin.getLogger().info("Skipped migration: table not empty");
            }

            File migratedFile = new File(adminsFile.getParentFile(), adminsFile.getName() + ".migrated");
            if (!migratedFile.exists()) {
                if (adminsFile.renameTo(migratedFile)) {
                    plugin.getLogger().info("admins.yml renamed to admins.yml.migrated");
                } else {
                    plugin.getLogger().warning("Не удалось переименовать admins.yml в admins.yml.migrated");
                }
            }
        } catch (Exception e) {
            logException("migrateAdminsFromYaml", e);
        }
    }

    public synchronized boolean ensureConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                init();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка проверки соединения SQLite.", e);
            connection = null;
            initialized = false;
            storageAvailable = false;
            return false;
        }
        return connection != null && storageAvailable;
    }

    public void scheduleCleanup() {
        if (!storageAvailable) {
            return;
        }
        long periodTicks = 20L * 60L * 60L * 24L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpiredDataInternal, periodTicks, periodTicks);
    }

    public CompletableFuture<Void> cleanupExpiredData() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                cleanupExpiredDataInternal();
                future.complete(null);
            } catch (Exception e) {
                logException("cleanupExpiredData", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> recordIp(UUID uuid, String playerName, String ip) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!storageAvailable) {
            plugin.getLogger().warning("DatabaseManager.recordIp: storage unavailable, returning false");
            future.complete(false);
            return future;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean result = recordIpInternal(uuid, playerName, ip);
                plugin.getLogger().fine("recordIp: name=" + playerName + ", ip=" + ip + ", result=" + result);
                future.complete(result);
            } catch (Exception e) {
                logException("recordIp", e);
                future.complete(false);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> addPunishment(UUID targetUuid, String targetName, String type,
                                                      String reason, String operator, long expiresAt,
                                                      String ipAddress, String source) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!storageAvailable) {
            plugin.getLogger().warning("DatabaseManager.addPunishment: storage unavailable, returning false");
            future.complete(false);
            return future;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean success = addPunishmentInternal(targetUuid, targetName, type, reason, operator, expiresAt, ipAddress, source);
                if (success) {
                    invalidateActivePunishmentCache(targetName);
                }
                plugin.getLogger().fine("addPunishment: target=" + targetName + ", type=" + type + ", active=" + success + ", expiresAt=" + expiresAt);
                future.complete(success);
            } catch (Exception e) {
                logException("addPunishment", e);
                future.complete(false);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> deactivatePunishment(String name, String type) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!storageAvailable) {
            plugin.getLogger().warning("DatabaseManager.deactivatePunishment: storage unavailable, returning false");
            future.complete(false);
            return future;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean success = deactivatePunishmentInternal(name, type);
                if (success) {
                    invalidateActivePunishmentCache(name);
                }
                plugin.getLogger().fine("deactivatePunishment: name=" + name + ", type=" + type + ", result=" + success);
                future.complete(success);
            } catch (Exception e) {
                logException("deactivatePunishment", e);
                future.complete(false);
            }
        });
        return future;
    }

    public CompletableFuture<List<Map<String, Object>>> getActivePunishments(String name) {
        CompletableFuture<List<Map<String, Object>>> future = new CompletableFuture<>();
        if (!storageAvailable) {
            plugin.getLogger().warning("DatabaseManager.getActivePunishments: storage unavailable, returning empty list");
            future.complete(new ArrayList<>());
            return future;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Map<String, Object>> rows = getActivePunishmentsInternal(name);
                plugin.getLogger().fine("getActivePunishments: name=" + name + ", rows=" + rows.size());
                future.complete(rows);
            } catch (Exception e) {
                logException("getActivePunishments", e);
                future.complete(new ArrayList<>());
            }
        });
        return future;
    }

    public List<Map<String, Object>> getAllActivePunishmentsSync() {
        if (!ensureConnection()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT id, target_uuid, target_name, type, reason, operator, created_at, expires_at, active, ip_address, source "
                + "FROM punishments WHERE active = 1 ORDER BY created_at DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", set.getLong("id"));
                row.put("target_uuid", set.getString("target_uuid"));
                row.put("target_name", set.getString("target_name"));
                row.put("type", set.getString("type"));
                row.put("reason", set.getString("reason"));
                row.put("operator", set.getString("operator"));
                row.put("created_at", set.getLong("created_at"));
                row.put("expires_at", set.getLong("expires_at"));
                row.put("active", set.getInt("active"));
                row.put("ip_address", set.getString("ip_address"));
                row.put("source", set.getString("source"));
                result.add(row);
            }
        } catch (SQLException e) {
            logException("getAllActivePunishmentsSync", e);
        }
        return result;
    }

    public List<Map<String, Object>> getPunishmentsByTarget(String targetName, int limit) {
        if (!storageAvailable) {
            plugin.getLogger().warning("DatabaseManager.getPunishmentsByTarget: storage unavailable, returning empty list");
            return new ArrayList<>();
        }
        if (targetName == null || targetName.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT * FROM punishments WHERE target_name = ? ORDER BY created_at DESC LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetName);
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", set.getLong("id"));
                    row.put("target_uuid", set.getString("target_uuid"));
                    row.put("target_name", set.getString("target_name"));
                    row.put("type", set.getString("type"));
                    row.put("reason", set.getString("reason"));
                    row.put("operator", set.getString("operator"));
                    row.put("created_at", set.getLong("created_at"));
                    row.put("expires_at", set.getLong("expires_at"));
                    row.put("active", set.getInt("active"));
                    row.put("ip_address", set.getString("ip_address"));
                    row.put("source", set.getString("source"));
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            logException("getPunishmentsByTarget", e);
        }
        return result;
    }

    public CompletableFuture<Boolean> isIpBanned(String ip) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!storageAvailable) {
            plugin.getLogger().warning("DatabaseManager.isIpBanned: storage unavailable, returning false");
            future.complete(false);
            return future;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean result = isIpBannedInternal(ip);
                plugin.getLogger().fine("isIpBanned: ip=" + ip + ", result=" + result);
                future.complete(result);
            } catch (Exception e) {
                logException("isIpBanned", e);
                future.complete(false);
            }
        });
        return future;
    }

    public CompletableFuture<List<String>> getRecentIps(UUID uuid, int days) {
        CompletableFuture<List<String>> future = new CompletableFuture<>();
        if (!storageAvailable) {
            plugin.getLogger().warning("DatabaseManager.getRecentIps: storage unavailable, returning empty list");
            future.complete(new ArrayList<>());
            return future;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<String> result = getRecentIpsInternal(uuid, days);
                plugin.getLogger().fine("getRecentIps: uuid=" + uuid + ", days=" + days + ", rows=" + result.size());
                future.complete(result);
            } catch (Exception e) {
                logException("getRecentIps", e);
                future.complete(new ArrayList<>());
            }
        });
        return future;
    }

    public CompletableFuture<Integer> countDistinctIps(UUID uuid, int days) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        if (!storageAvailable) {
            plugin.getLogger().warning("DatabaseManager.countDistinctIps: storage unavailable, returning 0");
            future.complete(0);
            return future;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int result = countDistinctIpsInternal(uuid, days);
                plugin.getLogger().fine("countDistinctIps: uuid=" + uuid + ", days=" + days + ", result=" + result);
                future.complete(result);
            } catch (Exception e) {
                logException("countDistinctIps", e);
                future.complete(0);
            }
        });
        return future;
    }

    public CompletableFuture<Integer> countDistinctUuidsForIp(String ip, int days) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        if (!storageAvailable) {
            plugin.getLogger().warning("DatabaseManager.countDistinctUuidsForIp: storage unavailable, returning 0");
            future.complete(0);
            return future;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int result = countDistinctUuidsForIpInternal(ip, days);
                plugin.getLogger().fine("countDistinctUuidsForIp: ip=" + ip + ", days=" + days + ", result=" + result);
                future.complete(result);
            } catch (Exception e) {
                logException("countDistinctUuidsForIp", e);
                future.complete(0);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> logCommand(String executor, String command, String result) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!storageAvailable) {
            plugin.getLogger().warning("DatabaseManager.logCommand: storage unavailable, returning false");
            future.complete(false);
            return future;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean success = logCommandInternal(executor, command, result);
                plugin.getLogger().fine("logCommand: executor=" + executor + ", command=" + command + ", result=" + result + ", success=" + success);
                future.complete(success);
            } catch (Exception e) {
                logException("logCommand", e);
                future.complete(false);
            }
        });
        return future;
    }

    public List<Map<String, Object>> getCommandLogs(int limit) {
        if (!storageAvailable) {
            plugin.getLogger().warning("DatabaseManager.getCommandLogs: storage unavailable, returning empty list");
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT * FROM command_logs ORDER BY created_at DESC LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, Math.min(200, limit)));
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", set.getLong("id"));
                    row.put("executor", set.getString("executor"));
                    row.put("command", set.getString("command"));
                    row.put("result", set.getString("result"));
                    row.put("created_at", set.getLong("created_at"));
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            logException("getCommandLogs", e);
        }
        return result;
    }

    private synchronized boolean recordIpInternal(UUID uuid, String playerName, String ip) {
        if (!ensureConnection()) {
            return false;
        }

        String normalizedIp = normalizeIp(ip);
        if (normalizedIp == null || isLoopback(normalizedIp)) {
            plugin.getLogger().warning("recordIp: skip loopback/IP without writing: " + ip + " (player=" + playerName + ")");
            return false;
        }

        if (uuid == null) {
            plugin.getLogger().warning("recordIp: UUID cannot be null for player " + playerName);
            return false;
        }

        String sql = "INSERT INTO ip_history(player_uuid, player_name, ip_address, seen_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, playerName == null ? "unknown" : playerName);
            statement.setString(3, normalizedIp);
            statement.setLong(4, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logException("recordIpInternal", e);
            return false;
        }
    }

    private synchronized boolean addPunishmentInternal(UUID targetUuid, String targetName, String type,
                                                      String reason, String operator, long expiresAt,
                                                      String ipAddress, String source) {
        if (!ensureConnection()) {
            return false;
        }

        if (targetName == null || targetName.trim().isEmpty()) {
            plugin.getLogger().warning("addPunishment: target_name cannot be empty");
            return false;
        }

        String sql = "INSERT INTO punishments(target_uuid, target_name, type, reason, operator, created_at, expires_at, active, ip_address, source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetUuid == null ? null : targetUuid.toString());
            statement.setString(2, targetName);
            statement.setString(3, type);
            statement.setString(4, reason);
            statement.setString(5, operator == null ? "system" : operator);
            long createdAt = System.currentTimeMillis();
            statement.setLong(6, createdAt);
            statement.setLong(7, expiresAt > 0 ? expiresAt : 0L);
            statement.setString(8, ipAddress);
            statement.setString(9, source == null || source.trim().isEmpty() ? "manual" : source);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logException("addPunishmentInternal", e);
            return false;
        }
    }

    private synchronized boolean deactivatePunishmentInternal(String name, String type) {
        if (!ensureConnection()) {
            return false;
        }

        String sql = "UPDATE punishments SET active = 0 WHERE target_name = ? AND type = ? AND active = 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, type);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logException("deactivatePunishmentInternal", e);
            return false;
        }
    }

    private synchronized List<Map<String, Object>> getActivePunishmentsInternal(String name) {
        if (!ensureConnection()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT id, target_uuid, target_name, type, reason, operator, created_at, expires_at, active, ip_address, source "
                + "FROM punishments WHERE target_name = ? AND active = 1 ORDER BY created_at DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", set.getLong("id"));
                    row.put("target_uuid", set.getString("target_uuid"));
                    row.put("target_name", set.getString("target_name"));
                    row.put("type", set.getString("type"));
                    row.put("reason", set.getString("reason"));
                    row.put("operator", set.getString("operator"));
                    row.put("created_at", set.getLong("created_at"));
                    row.put("expires_at", set.getLong("expires_at"));
                    row.put("active", set.getInt("active"));
                    row.put("ip_address", set.getString("ip_address"));
                    row.put("source", set.getString("source"));
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            logException("getActivePunishmentsInternal", e);
        }
        return result;
    }

    private synchronized boolean isIpBannedInternal(String ip) {
        if (!ensureConnection()) {
            return false;
        }

        String normalizedIp = normalizeIp(ip);
        if (normalizedIp == null) {
            return false;
        }

        String sql = "SELECT COUNT(*) FROM punishments WHERE active = 1 AND ip_address = ? AND (expires_at = 0 OR expires_at > ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedIp);
            statement.setLong(2, System.currentTimeMillis());
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logException("isIpBannedInternal", e);
        }
        return false;
    }

    private synchronized List<String> getRecentIpsInternal(UUID uuid, int days) {
        if (!ensureConnection() || uuid == null) {
            return new ArrayList<>();
        }

        long cutoff = getCutoffTime(days);
        List<String> result = new ArrayList<>();
        String sql = "SELECT DISTINCT ip_address FROM ip_history WHERE player_uuid = ? AND seen_at >= ? ORDER BY seen_at DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, cutoff);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    String ip = set.getString("ip_address");
                    if (ip != null && !ip.trim().isEmpty()) {
                        result.add(ip);
                    }
                }
            }
        } catch (SQLException e) {
            logException("getRecentIpsInternal", e);
        }
        return result;
    }

    private synchronized int countDistinctIpsInternal(UUID uuid, int days) {
        if (!ensureConnection() || uuid == null) {
            return 0;
        }

        long cutoff = getCutoffTime(days);
        String sql = "SELECT COUNT(DISTINCT ip_address) FROM ip_history WHERE player_uuid = ? AND seen_at >= ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, cutoff);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getInt(1);
                }
            }
        } catch (SQLException e) {
            logException("countDistinctIpsInternal", e);
        }
        return 0;
    }

    private synchronized int countDistinctUuidsForIpInternal(String ip, int days) {
        if (!ensureConnection()) {
            return 0;
        }

        String normalizedIp = normalizeIp(ip);
        if (normalizedIp == null) {
            return 0;
        }

        long cutoff = getCutoffTime(days);
        String sql = "SELECT COUNT(DISTINCT player_uuid) FROM ip_history WHERE ip_address = ? AND seen_at >= ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedIp);
            statement.setLong(2, cutoff);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getInt(1);
                }
            }
        } catch (SQLException e) {
            logException("countDistinctUuidsForIpInternal", e);
        }
        return 0;
    }

    private synchronized boolean logCommandInternal(String executor, String command, String result) {
        if (!ensureConnection()) {
            return false;
        }

        String sql = "INSERT INTO command_logs(executor, command, result, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, executor == null ? "unknown" : executor);
            statement.setString(2, command == null ? "" : command);
            statement.setString(3, result);
            statement.setLong(4, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logException("logCommandInternal", e);
            return false;
        }
    }

    private synchronized void cleanupExpiredDataInternal() {
        if (!ensureConnection()) {
            return;
        }

        try {
            long now = System.currentTimeMillis();
            int ipHistoryDays = plugin.getConfig().getInt("storage.cleanup.ip-history-days", 90);
            int commandLogsDays = plugin.getConfig().getInt("storage.cleanup.command-logs-days", 30);

            long ipHistoryCutoff = now - TimeUnit.DAYS.toMillis(Math.max(1, ipHistoryDays));
            long commandCutoff = now - TimeUnit.DAYS.toMillis(Math.max(1, commandLogsDays));

            try (PreparedStatement deleteIpHistory = connection.prepareStatement("DELETE FROM ip_history WHERE seen_at < ?")) {
                deleteIpHistory.setLong(1, ipHistoryCutoff);
                deleteIpHistory.executeUpdate();
            }

            try (PreparedStatement deleteCommandLogs = connection.prepareStatement("DELETE FROM command_logs WHERE created_at < ?")) {
                deleteCommandLogs.setLong(1, commandCutoff);
                deleteCommandLogs.executeUpdate();
            }

            try (PreparedStatement deactivatePunishments = connection.prepareStatement(
                    "UPDATE punishments SET active = 0 WHERE active = 1 AND expires_at > 0 AND expires_at <= ?")) {
                deactivatePunishments.setLong(1, now);
                deactivatePunishments.executeUpdate();
            }
        } catch (SQLException e) {
            logException("cleanupExpiredDataInternal", e);
        }
    }

    private long getCutoffTime(int days) {
        if (days <= 0) {
            return 0L;
        }
        return System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
    }

    private String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        String normalized = ip.trim();
        if (normalized.isEmpty() || normalized.equals("—") || normalized.equalsIgnoreCase("localhost")) {
            return null;
        }
        return normalized;
    }

    private boolean isLoopback(String ip) {
        if (ip == null) {
            return true;
        }
        String normalized = ip.trim();
        return normalized.equals("0.0.0.0")
                || normalized.equals("127.0.0.1")
                || normalized.equals("::1")
                || normalized.equalsIgnoreCase("localhost")
                || normalized.startsWith("127.")
                || normalized.startsWith("::1");
    }

    private void logException(String method, Exception e) {
        plugin.getLogger().log(Level.SEVERE, "DatabaseManager." + method + " failed.", e);
    }
}
