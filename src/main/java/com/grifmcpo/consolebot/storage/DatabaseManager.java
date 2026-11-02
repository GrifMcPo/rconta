package com.grifmcpo.consolebot.storage;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    private static final long STORAGE_AUDIT_PERIOD_MS = 30_000L;

    private final JavaPlugin plugin;
    private final File databaseFile;
    private final File sqliteLegacyFile;
    private final Map<String, List<Map<String, Object>>> activePunishmentCache = new HashMap<>();
    private Connection connection;
    private boolean initialized;
    private boolean storageAvailable;
    private boolean storageAuditStarted;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку данных плагина: " + dataFolder.getAbsolutePath());
        }
        this.databaseFile = new File(dataFolder, "data.mv.db");
        this.sqliteLegacyFile = new File(dataFolder, "data.db");
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
                    plugin.getLogger().info("H2 init complete: tables=[punishments, ip_history, command_logs, admins], storageAvailable=true");
                    return;
                }
            } catch (SQLException ignored) {
            }
        }

        storageAvailable = false;
        try {
            plugin.getLogger().info("H2 init: opening connection to " + getJdbcUrl());
            Class.forName("org.h2.Driver");
            connection = DriverManager.getConnection(getJdbcUrl());
            plugin.getLogger().info("H2 init: connection opened");

            boolean shouldMigrateSqlite = sqliteLegacyFile.exists() && !databaseFile.exists();
            if (shouldMigrateSqlite) {
                plugin.getLogger().info("H2 init: detected legacy SQLite database");
                migrateSqliteToH2();
            } else if (databaseFile.exists()) {
                plugin.getLogger().info("H2 already exists, skipping migration");
            }

            createCoreTables();
            initialized = true;
            storageAvailable = true;
            verifySchema();
            migrateLegacyHistoryIfNeeded();
            migrateLegacyCommandLogsIfNeeded();
            plugin.getLogger().info("H2 init complete: tables=[punishments, ip_history, command_logs, admins], storageAvailable=true");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "H2 JDBC driver not found in classpath; continuing with storage disabled.", e);
            initialized = false;
            storageAvailable = false;
            connection = null;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка инициализации H2.", e);
            initialized = false;
            storageAvailable = false;
            connection = null;
        }
    }

    public synchronized void verifySchema() {
        if (connection == null || !storageAvailable) {
            plugin.getLogger().severe("Schema verify: storage unavailable, no DB tables checked");
            return;
        }

        List<String> found = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' ORDER BY TABLE_NAME");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                found.add(resultSet.getString("TABLE_NAME").toLowerCase());
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Schema verify failed while querying INFORMATION_SCHEMA.TABLES.", e);
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
            plugin.getLogger().log(Level.SEVERE, "Missing H2 tables: " + missing + ". Attempting CREATE TABLE IF NOT EXISTS.", new RuntimeException("schema-missing"));
            try {
                createCoreTables();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to recreate missing H2 tables.", ex);
                storageAvailable = false;
            }
        }
    }

    public synchronized void startPeriodicStorageAudit() {
        if (storageAuditStarted) {
            return;
        }
        storageAuditStarted = true;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!ensureConnection()) {
                plugin.getLogger().warning("STORAGE CHECK: H2 unavailable. validation=failed");
                return;
            }

            int punishments = countRows("punishments");
            int activePunishments = countRowsWhere("punishments", "active = 1");
            int ipHistory = countRows("ip_history");
            int commandLogs = countRows("command_logs");
            int admins = countRows("admins");

            plugin.getLogger().info("STORAGE CHECK OK: punishments=" + punishments
                    + ", active=" + activePunishments
                    + ", ip_history=" + ipHistory
                    + ", command_logs=" + commandLogs
                    + ", admins=" + admins
                    + ", interval_ms=" + STORAGE_AUDIT_PERIOD_MS);
        }, STORAGE_AUDIT_PERIOD_MS / 50L, STORAGE_AUDIT_PERIOD_MS / 50L);
    }

    public synchronized void close() {
        if (connection == null) {
            initialized = false;
            storageAvailable = false;
            return;
        }

        try {
            connection.close();
            plugin.getLogger().info("H2 connection closed: " + databaseFile.getAbsolutePath());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка закрытия H2.", e);
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
            plugin.getLogger().warning("Storage is not available; skipping admins.yml migration.");
            return;
        }

        try {
            if (!hasAnyAdminRecords()) {
                int imported = 0;
                for (Map.Entry<String, String> entry : admins.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null || entry.getValue().trim().isEmpty()) {
                        continue;
                    }
                    String sql = "MERGE INTO admins KEY(uuid) VALUES (?, ?, ?, ?)";
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
            if (!migratedFile.exists() && adminsFile.renameTo(migratedFile)) {
                plugin.getLogger().info("admins.yml renamed to admins.yml.migrated");
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
            plugin.getLogger().log(Level.SEVERE, "Ошибка проверки соединения H2.", e);
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
                long createdAt = System.currentTimeMillis();
                boolean result = recordIpInternal(uuid, playerName, ip, createdAt);
                boolean verified = result && verifyRecentIpWrite(uuid, normalizeIp(ip), createdAt);
                plugin.getLogger().info("recordIp: name=" + playerName + ", ip=" + ip + ", result=" + result + ", verified=" + verified + ", createdAt=" + createdAt);
                future.complete(result && verified);
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
                long createdAt = System.currentTimeMillis();
                boolean success = addPunishmentInternal(targetUuid, targetName, type, reason, operator, expiresAt, ipAddress, source, createdAt);
                if (success) {
                    invalidateActivePunishmentCache(targetName);
                    boolean verified = verifyRecentPunishmentWrite(targetName, type, createdAt);
                    plugin.getLogger().info("Punishment inserted: type=" + type + ", target=" + targetName + ", expiresAt=" + expiresAt + ", verified=" + verified + ", createdAt=" + createdAt);
                    if (!verified) {
                        plugin.getLogger().warning("STORAGE CHECK FAIL: punishment not visible after insert. target=" + targetName + ", type=" + type + ", createdAt=" + createdAt);
                    }
                } else {
                    plugin.getLogger().warning("STORAGE CHECK FAIL: insert returned false. target=" + targetName + ", type=" + type + ", createdAt=" + createdAt);
                }
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
        String sql = "SELECT id, target_uuid, target_name, type, reason, operator, created_at, expires_at, active, ip_address, source FROM punishments WHERE active = 1 ORDER BY created_at DESC";
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
            statement.setInt(2, Math.max(1, Math.min(200, limit > 0 ? limit : 50)));
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

    public List<Map<String, Object>> getPunishmentsByOperator(String operator, int limit) {
        if (!storageAvailable) {
            return new ArrayList<>();
        }
        if (operator == null || operator.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT * FROM punishments WHERE operator = ? ORDER BY created_at DESC LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operator);
            statement.setInt(2, Math.max(1, Math.min(200, limit > 0 ? limit : 50)));
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
            logException("getPunishmentsByOperator", e);
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
                long createdAt = System.currentTimeMillis();
                boolean success = logCommandInternal(executor, command, result, createdAt);
                boolean verified = success && verifyRecentCommandWrite(executor, command, createdAt);
                future.complete(success && verified);
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

    public List<Map<String, Object>> getBaseBans(int limit) {
        if (!storageAvailable) {
            return new ArrayList<>();
        }
        String sql = "SELECT * FROM punishments ORDER BY created_at DESC LIMIT ?";
        List<Map<String, Object>> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, Math.min(50, limit > 0 ? limit : 10)));
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    result.add(mapRow(set));
                }
            }
        } catch (SQLException e) {
            logException("getBaseBans", e);
        }
        return result;
    }

    public List<Map<String, Object>> getBaseIpHistory(String playerName, int limit) {
        if (!storageAvailable) {
            return new ArrayList<>();
        }
        String sql = "SELECT * FROM ip_history WHERE player_name = ? ORDER BY seen_at DESC LIMIT ?";
        List<Map<String, Object>> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerName == null ? "" : playerName);
            statement.setInt(2, Math.max(1, Math.min(50, limit > 0 ? limit : 10)));
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    result.add(ipHistoryRow(set));
                }
            }
        } catch (SQLException e) {
            logException("getBaseIpHistory", e);
        }
        return result;
    }

    public List<Map<String, Object>> getBaseLogs(int limit) {
        return getCommandLogs(Math.max(1, Math.min(50, limit > 0 ? limit : 10)));
    }

    public List<Map<String, Object>> getAdminsRows() {
        if (!storageAvailable) {
            return new ArrayList<>();
        }
        String sql = "SELECT * FROM admins ORDER BY added_at DESC";
        List<Map<String, Object>> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("uuid", set.getString("uuid"));
                row.put("name", set.getString("name"));
                row.put("added_by", set.getString("added_by"));
                row.put("added_at", set.getLong("added_at"));
                result.add(row);
            }
        } catch (SQLException e) {
            logException("getAdminsRows", e);
        }
        return result;
    }

    public boolean checkpointSync() {
        if (!storageAvailable || connection == null) {
            return false;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("CHECKPOINT SYNC");
            return true;
        } catch (SQLException e) {
            logException("checkpointSync", e);
            return false;
        }
    }

    private String getJdbcUrl() {
        return "jdbc:h2:file:" + plugin.getDataFolder().getAbsolutePath() + File.separator + "data;AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=FALSE";
    }

    private void createCoreTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            plugin.getLogger().info("H2 init: creating table punishments");
            statement.execute("CREATE TABLE IF NOT EXISTS punishments ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "target_uuid VARCHAR(64), "
                    + "target_name VARCHAR(128) NOT NULL, "
                    + "type VARCHAR(32) NOT NULL, "
                    + "reason CLOB, "
                    + "operator VARCHAR(128) NOT NULL, "
                    + "created_at BIGINT NOT NULL, "
                    + "expires_at BIGINT DEFAULT 0, "
                    + "active BOOLEAN DEFAULT TRUE, "
                    + "ip_address VARCHAR(45), "
                    + "source VARCHAR(128) DEFAULT 'manual')");

            plugin.getLogger().info("H2 init: creating table ip_history");
            statement.execute("CREATE TABLE IF NOT EXISTS ip_history ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "player_uuid VARCHAR(64) NOT NULL, "
                    + "player_name VARCHAR(128) NOT NULL, "
                    + "ip_address VARCHAR(45) NOT NULL, "
                    + "seen_at BIGINT NOT NULL)");

            plugin.getLogger().info("H2 init: creating index idx_ip_history_player");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_ip_history_player ON ip_history(player_uuid, seen_at)");
            plugin.getLogger().info("H2 init: creating index idx_ip_history_ip");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_ip_history_ip ON ip_history(ip_address, seen_at)");

            plugin.getLogger().info("H2 init: creating table command_logs");
            statement.execute("CREATE TABLE IF NOT EXISTS command_logs ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "executor VARCHAR(128) NOT NULL, "
                    + "command CLOB NOT NULL, "
                    + "result CLOB, "
                    + "created_at BIGINT NOT NULL)");

            plugin.getLogger().info("H2 init: creating table admins");
            statement.execute("CREATE TABLE IF NOT EXISTS admins ("
                    + "uuid VARCHAR(64) PRIMARY KEY, "
                    + "name VARCHAR(128), "
                    + "added_by VARCHAR(128), "
                    + "added_at BIGINT NOT NULL)");

            plugin.getLogger().info("H2 init: creating index idx_admins_name");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_admins_name ON admins(name)");
        }
    }

    private void migrateSqliteToH2() {
        File sqliteFile = sqliteLegacyFile;
        if (!sqliteFile.exists()) {
            return;
        }
        try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.getAbsolutePath())) {
            int punishments = copyTable(sqlite, "punishments", "punishments", true);
            int ipHistory = copyTable(sqlite, "ip_history", "ip_history", false);
            int commandLogs = copyTable(sqlite, "command_logs", "command_logs", false);
            int admins = copyTable(sqlite, "admins", "admins", false);
            plugin.getLogger().info("Migrated SQLite → H2: punishments=" + punishments + ", ip_history=" + ipHistory + ", command_logs=" + commandLogs + ", admins=" + admins);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error migrating SQLite to H2.", e);
            return;
        }

        File migrated = new File(sqliteFile.getParentFile(), sqliteFile.getName() + ".migrated");
        if (!migrated.exists()) {
            try {
                Files.move(sqliteFile.toPath(), migrated.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("data.db renamed to data.db.migrated");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to rename SQLite file to .migrated.", e);
            }
        }
    }

    private int copyTable(Connection sqlite, String sourceTable, String targetTable, boolean includeId) {
        if (connection == null) {
            return 0;
        }
        try {
            String sql = "SELECT * FROM " + sourceTable;
            List<Map<String, Object>> rows = new ArrayList<>();
            try (PreparedStatement ps = sqlite.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        String column = rs.getMetaData().getColumnName(i);
                        row.put(column, rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
            for (Map<String, Object> row : rows) {
                if (targetTable.equals("punishments")) {
                    insertPunishmentRow(row);
                } else if (targetTable.equals("ip_history")) {
                    insertIpHistoryRow(row);
                } else if (targetTable.equals("command_logs")) {
                    insertCommandLogRow(row);
                } else if (targetTable.equals("admins")) {
                    insertAdminRow(row);
                }
            }
            return rows.size();
        } catch (SQLException e) {
            logException("copyTable(" + sourceTable + ")", e);
            return 0;
        }
    }

    private void insertPunishmentRow(Map<String, Object> row) throws SQLException {
        String sql = "MERGE INTO punishments KEY(id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, row.getOrDefault("id", null));
            ps.setObject(2, row.get("target_uuid"));
            ps.setObject(3, row.get("target_name"));
            ps.setObject(4, row.get("type"));
            ps.setObject(5, row.get("reason"));
            ps.setObject(6, row.get("operator"));
            ps.setObject(7, row.get("created_at"));
            ps.setObject(8, row.get("expires_at"));
            ps.setObject(9, row.get("active"));
            ps.setObject(10, row.get("ip_address"));
            ps.setObject(11, row.get("source"));
            ps.executeUpdate();
        }
    }

    private void insertIpHistoryRow(Map<String, Object> row) throws SQLException {
        String sql = "MERGE INTO ip_history KEY(id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, row.getOrDefault("id", null));
            ps.setObject(2, row.get("player_uuid"));
            ps.setObject(3, row.get("player_name"));
            ps.setObject(4, row.get("ip_address"));
            ps.setObject(5, row.get("seen_at"));
            ps.executeUpdate();
        }
    }

    private void insertCommandLogRow(Map<String, Object> row) throws SQLException {
        String sql = "MERGE INTO command_logs KEY(id) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, row.getOrDefault("id", null));
            ps.setObject(2, row.get("executor"));
            ps.setObject(3, row.get("command"));
            ps.setObject(4, row.get("result"));
            ps.setObject(5, row.get("created_at"));
            ps.executeUpdate();
        }
    }

    private void insertAdminRow(Map<String, Object> row) throws SQLException {
        String sql = "MERGE INTO admins KEY(uuid) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, row.get("uuid"));
            ps.setObject(2, row.get("name"));
            ps.setObject(3, row.get("added_by"));
            ps.setObject(4, row.get("added_at"));
            ps.executeUpdate();
        }
    }

    private Map<String, Object> mapRow(ResultSet set) throws SQLException {
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
        return row;
    }

    private Map<String, Object> ipHistoryRow(ResultSet set) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", set.getLong("id"));
        row.put("player_uuid", set.getString("player_uuid"));
        row.put("player_name", set.getString("player_name"));
        row.put("ip_address", set.getString("ip_address"));
        row.put("seen_at", set.getLong("seen_at"));
        return row;
    }

    private synchronized boolean recordIpInternal(UUID uuid, String playerName, String ip, long createdAt) {
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
            statement.setLong(4, createdAt);
            boolean result = statement.executeUpdate() > 0;
            if (result) {
                plugin.getLogger().info("IP recorded: uuid=" + uuid + ", name=" + playerName + ", ip=" + normalizedIp + ", createdAt=" + createdAt);
            }
            return result;
        } catch (SQLException e) {
            logException("recordIpInternal", e);
            return false;
        }
    }

    private synchronized boolean addPunishmentInternal(UUID targetUuid, String targetName, String type,
                                                      String reason, String operator, long expiresAt,
                                                      String ipAddress, String source, long createdAt) {
        if (!ensureConnection()) {
            return false;
        }

        if (targetName == null || targetName.trim().isEmpty()) {
            plugin.getLogger().warning("addPunishment: target_name cannot be empty");
            return false;
        }

        String sql = "INSERT INTO punishments(target_uuid, target_name, type, reason, operator, created_at, expires_at, active, ip_address, source) VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetUuid == null ? null : targetUuid.toString());
            statement.setString(2, targetName);
            statement.setString(3, type);
            statement.setString(4, reason);
            statement.setString(5, operator == null ? "system" : operator);
            statement.setLong(6, createdAt);
            statement.setLong(7, expiresAt > 0 ? expiresAt : 0L);
            statement.setString(8, ipAddress);
            statement.setString(9, source == null || source.trim().isEmpty() ? "manual" : source);
            boolean result = statement.executeUpdate() > 0;
            if (result) {
                plugin.getLogger().info("Punishment inserted: target=" + targetName + ", type=" + type + ", operator=" + (operator == null ? "system" : operator) + ", expiresAt=" + expiresAt + ", createdAt=" + createdAt);
            }
            return result;
        } catch (SQLException e) {
            logException("addPunishmentInternal", e);
            return false;
        }
    }

    private synchronized boolean deactivatePunishmentInternal(String name, String type) {
        if (!ensureConnection()) {
            return false;
        }

        String sql = "UPDATE punishments SET active = FALSE WHERE target_name = ? AND type = ? AND active = TRUE";
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
        String sql = "SELECT id, target_uuid, target_name, type, reason, operator, created_at, expires_at, active, ip_address, source FROM punishments WHERE target_name = ? AND active = TRUE ORDER BY created_at DESC";
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

        String sql = "SELECT COUNT(*) FROM punishments WHERE active = TRUE AND ip_address = ? AND (expires_at = 0 OR expires_at > ?)";
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

    private synchronized boolean logCommandInternal(String executor, String command, String result, long createdAt) {
        if (!ensureConnection()) {
            return false;
        }

        String sql = "INSERT INTO command_logs(executor, command, result, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, executor == null ? "unknown" : executor);
            statement.setString(2, command == null ? "" : command);
            statement.setString(3, result == null ? "" : result);
            statement.setLong(4, createdAt);
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
                    "UPDATE punishments SET active = FALSE WHERE active = TRUE AND expires_at > 0 AND expires_at <= ?")) {
                deactivatePunishments.setLong(1, now);
                deactivatePunishments.executeUpdate();
            }
        } catch (SQLException e) {
            logException("cleanupExpiredDataInternal", e);
        }
    }

    private synchronized void migrateLegacyHistoryIfNeeded() {
        if (!storageAvailable || connection == null) {
            return;
        }
        File historyFile = new File(plugin.getDataFolder(), "history.yml");
        if (!historyFile.exists()) {
            return;
        }
        if (rowCount("punishments") > 0) {
            renameLegacyFile(historyFile, "history.yml.migrated");
            plugin.getLogger().info("Skipped migration: table not empty");
            return;
        }

        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(historyFile);
            int imported = 0;
            for (String playerName : config.getKeys(false)) {
                Object value = config.get(playerName);
                if (!(value instanceof List<?>)) {
                    continue;
                }
                for (Object entryObj : (List<?>) value) {
                    if (!(entryObj instanceof Map<?, ?>)) {
                        continue;
                    }
                    Map<?, ?> entry = (Map<?, ?>) entryObj;
                    String type = entry.get("type") == null ? "" : String.valueOf(entry.get("type"));
                    String targetName = entry.get("player") == null ? playerName : String.valueOf(entry.get("player"));
                    String operator = entry.get("issuer") == null ? "system" : String.valueOf(entry.get("issuer"));
                    String reason = entry.get("reason") == null ? "" : String.valueOf(entry.get("reason"));
                    long createdAt = entry.get("timestamp") instanceof Number ? ((Number) entry.get("timestamp")).longValue() : System.currentTimeMillis();
                    long expiresAt = entry.get("expiry") instanceof Number ? ((Number) entry.get("expiry")).longValue() : 0L;
                    boolean active = !type.startsWith("un") && !"kick".equals(type) && !(expiresAt > 0 && expiresAt <= System.currentTimeMillis());
                    if (type == null || type.trim().isEmpty()) {
                        continue;
                    }
                    String sql = "INSERT INTO punishments(target_uuid, target_name, type, reason, operator, created_at, expires_at, active, ip_address, source) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setString(1, null);
                        statement.setString(2, targetName);
                        statement.setString(3, type);
                        statement.setString(4, reason);
                        statement.setString(5, operator);
                        statement.setLong(6, createdAt);
                        statement.setLong(7, expiresAt > 0 ? expiresAt : 0L);
                        statement.setBoolean(8, active);
                        statement.setString(9, null);
                        statement.setString(10, "migrated-from-history-yml");
                        statement.executeUpdate();
                        imported++;
                    }
                }
            }
            if (imported > 0) {
                plugin.getLogger().info("Migrated history.yml to punishments: " + imported + " entries");
            }
            renameLegacyFile(historyFile, "history.yml.migrated");
        } catch (Exception e) {
            logException("migrateLegacyHistoryIfNeeded", e);
        }
    }

    private synchronized void migrateLegacyCommandLogsIfNeeded() {
        if (!storageAvailable || connection == null) {
            return;
        }
        File dataFolder = plugin.getDataFolder();
        if (dataFolder == null || !dataFolder.exists()) {
            return;
        }
        File[] files = dataFolder.listFiles((dir, name) -> name.startsWith("logs_") && name.endsWith(".txt"));
        if (files == null || files.length == 0) {
            return;
        }
        if (rowCount("command_logs") > 0) {
            for (File file : files) {
                renameLegacyFile(file, file.getName() + ".migrated");
            }
            plugin.getLogger().info("Skipped migration: table not empty");
            return;
        }

        int imported = 0;
        for (File file : files) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    String[] parts = trimmed.split(" \\| ", 3);
                    if (parts.length < 3) {
                        continue;
                    }
                    long createdAt;
                    try {
                        createdAt = java.time.LocalDateTime.parse(parts[0].replace(" ", "T")).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    } catch (Exception ignored) {
                        createdAt = System.currentTimeMillis();
                    }
                    String executor = parts[1];
                    String command = parts[2];
                    String sql = "INSERT INTO command_logs(executor, command, result, created_at) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setString(1, executor);
                        statement.setString(2, command);
                        statement.setString(3, "migrated-from-logs-txt");
                        statement.setLong(4, createdAt);
                        statement.executeUpdate();
                        imported++;
                    } catch (SQLException e) {
                        logException("migrateLegacyCommandLogsIfNeeded", e);
                    }
                }
            } catch (IOException e) {
                logException("migrateLegacyCommandLogsIfNeeded", e);
            }
            renameLegacyFile(file, file.getName() + ".migrated");
        }
        if (imported > 0) {
            plugin.getLogger().info("Migrated logs_*.txt to command_logs: " + imported + " entries");
        }
    }

    private int rowCount(String tableName) {
        if (!ensureConnection()) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet set = statement.executeQuery()) {
            if (set.next()) {
                return set.getInt(1);
            }
        } catch (SQLException e) {
            logException("rowCount", e);
        }
        return 0;
    }

    private int countRows(String tableName) {
        if (!ensureConnection()) {
            return 0;
        }
        return rowCount(tableName);
    }

    private int countRowsWhere(String tableName, String whereClause) {
        if (!ensureConnection()) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE " + whereClause;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet set = statement.executeQuery()) {
            if (set.next()) {
                return set.getInt(1);
            }
        } catch (SQLException e) {
            logException("countRowsWhere", e);
        }
        return 0;
    }

    public boolean verifyRecentPunishmentWrite(String targetName, String type, long createdAt) {
        if (!ensureConnection()) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM punishments WHERE target_name = ? AND type = ? AND created_at = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetName);
            statement.setString(2, type);
            statement.setLong(3, createdAt);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    boolean passed = set.getInt(1) > 0;
                    plugin.getLogger().info("VERIFY PUNISHMENT: target=" + targetName + ", type=" + type + ", createdAt=" + createdAt + ", exists=" + passed);
                    return passed;
                }
            }
        } catch (SQLException e) {
            logException("verifyRecentPunishmentWrite", e);
        }
        return false;
    }

    public boolean verifyRecentIpWrite(UUID uuid, String ip, long createdAt) {
        if (!ensureConnection() || uuid == null || ip == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM ip_history WHERE player_uuid = ? AND ip_address = ? AND seen_at = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, ip);
            statement.setLong(3, createdAt);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    boolean passed = set.getInt(1) > 0;
                    plugin.getLogger().info("VERIFY IP: uuid=" + uuid + ", ip=" + ip + ", createdAt=" + createdAt + ", exists=" + passed);
                    return passed;
                }
            }
        } catch (SQLException e) {
            logException("verifyRecentIpWrite", e);
        }
        return false;
    }

    public boolean verifyRecentCommandWrite(String executor, String command, long createdAt) {
        if (!ensureConnection()) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM command_logs WHERE executor = ? AND command = ? AND created_at = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, executor == null ? "unknown" : executor);
            statement.setString(2, command == null ? "" : command);
            statement.setLong(3, createdAt);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    boolean passed = set.getInt(1) > 0;
                    plugin.getLogger().info("VERIFY COMMAND: executor=" + executor + ", command=" + command + ", createdAt=" + createdAt + ", exists=" + passed);
                    return passed;
                }
            }
        } catch (SQLException e) {
            logException("verifyRecentCommandWrite", e);
        }
        return false;
    }

    private void renameLegacyFile(File file, String targetName) {
        if (file == null || !file.exists()) {
            return;
        }
        File target = new File(file.getParentFile(), targetName);
        if (!target.exists() && file.renameTo(target)) {
            plugin.getLogger().info(file.getName() + " renamed to " + target.getName());
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
