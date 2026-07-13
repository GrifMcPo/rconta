package com.grifmcpo.consolebot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ReportManager {

    private final JavaPlugin plugin;
    private File reportsFile;
    private FileConfiguration reportsConfig;
    private final Map<Integer, Report> reports = new ConcurrentHashMap<>();
    private int nextId = 1;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    public ReportManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadReports();
    }

    private void loadReports() {
        reportsFile = new File(plugin.getDataFolder(), "reports.yml");
        if (!reportsFile.exists()) {
            try {
                reportsFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("❌ Не удалось создать reports.yml");
            }
        }
        reportsConfig = YamlConfiguration.loadConfiguration(reportsFile);
        loadReportsFromConfig();
    }

    private void loadReportsFromConfig() {
        reports.clear();
        for (String key : reportsConfig.getKeys(false)) {
            if (key.equals("nextId")) continue;
            int id = Integer.parseInt(key);
            String reporter = reportsConfig.getString(key + ".reporter");
            String target = reportsConfig.getString(key + ".target");
            String reason = reportsConfig.getString(key + ".reason");
            String timestamp = reportsConfig.getString(key + ".timestamp");
            String status = reportsConfig.getString(key + ".status");
            String assignee = reportsConfig.getString(key + ".assignee");
            String closeReason = reportsConfig.getString(key + ".closeReason");

            Report report = new Report(id, reporter, target, reason, timestamp);
            report.setStatus(ReportStatus.valueOf(status));
            report.setAssignee(assignee);
            report.setCloseReason(closeReason);
            reports.put(id, report);
        }
        nextId = reportsConfig.getInt("nextId", 1);
    }

    public void saveReports() {
        for (Report report : reports.values()) {
            reportsConfig.set(String.valueOf(report.getId()) + ".reporter", report.getReporter());
            reportsConfig.set(String.valueOf(report.getId()) + ".target", report.getTarget());
            reportsConfig.set(String.valueOf(report.getId()) + ".reason", report.getReason());
            reportsConfig.set(String.valueOf(report.getId()) + ".timestamp", report.getTimestamp());
            reportsConfig.set(String.valueOf(report.getId()) + ".status", report.getStatus().name());
            reportsConfig.set(String.valueOf(report.getId()) + ".assignee", report.getAssignee());
            reportsConfig.set(String.valueOf(report.getId()) + ".closeReason", report.getCloseReason());
        }
        reportsConfig.set("nextId", nextId);
        try {
            reportsConfig.save(reportsFile);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка сохранения reports.yml: " + e.getMessage());
        }
    }

    public int createReport(String reporter, String target, String reason) {
        int id = nextId++;
        String timestamp = dateFormat.format(new Date());
        Report report = new Report(id, reporter, target, reason, timestamp);
        report.setStatus(ReportStatus.NEW);
        reports.put(id, report);
        saveReports();
        return id;
    }

    public Report getReport(int id) {
        return reports.get(id);
    }

    public List<Report> getAllReports() {
        return new ArrayList<>(reports.values());
    }

    public boolean takeReport(int id, String assignee) {
        Report report = reports.get(id);
        if (report == null || report.getStatus() != ReportStatus.NEW) {
            return false;
        }
        report.setStatus(ReportStatus.IN_PROGRESS);
        report.setAssignee(assignee);
        saveReports();
        return true;
    }

    public boolean closeReport(int id, String assignee, String reason) {
        Report report = reports.get(id);
        if (report == null || report.getStatus() == ReportStatus.CLOSED) {
            return false;
        }
        report.setStatus(ReportStatus.CLOSED);
        report.setAssignee(assignee);
        report.setCloseReason(reason);
        saveReports();
        return true;
    }

    public enum ReportStatus {
        NEW, IN_PROGRESS, CLOSED
    }

    public static class Report {
        private final int id;
        private final String reporter;
        private final String target;
        private final String reason;
        private final String timestamp;
        private ReportStatus status;
        private String assignee;
        private String closeReason;

        public Report(int id, String reporter, String target, String reason, String timestamp) {
            this.id = id;
            this.reporter = reporter;
            this.target = target;
            this.reason = reason;
            this.timestamp = timestamp;
            this.status = ReportStatus.NEW;
        }

        public int getId() { return id; }
        public String getReporter() { return reporter; }
        public String getTarget() { return target; }
        public String getReason() { return reason; }
        public String getTimestamp() { return timestamp; }
        public ReportStatus getStatus() { return status; }
        public String getAssignee() { return assignee; }
        public String getCloseReason() { return closeReason; }

        public void setStatus(ReportStatus status) { this.status = status; }
        public void setAssignee(String assignee) { this.assignee = assignee; }
        public void setCloseReason(String closeReason) { this.closeReason = closeReason; }

        public String getStatusColor() {
            switch (status) {
                case NEW: return "§c";
                case IN_PROGRESS: return "§e";
                case CLOSED: return "§a";
                default: return "§f";
            }
        }

        public String getStatusName() {
            switch (status) {
                case NEW: return "Новая";
                case IN_PROGRESS: return "В работе";
                case CLOSED: return "Закрыта";
                default: return "Неизвестно";
            }
        }
    }
}
