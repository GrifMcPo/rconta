package com.grifmcpo.reports;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class ReportsPlugin extends JavaPlugin {

    private static ReportsPlugin instance;
    private ReportManager reportManager;
    private TelegramNotifier telegramNotifier;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("✅ ReportsPlugin включен!");

        saveDefaultConfig();
        reportManager = new ReportManager(this);
        telegramNotifier = new TelegramNotifier(this);

        getCommand("report").setExecutor(new ReportCommand(this));
        getCommand("reports").setExecutor(new ReportCommand(this));
        getCommand("reporttake").setExecutor(new ReportCommand(this));
        getCommand("reportclose").setExecutor(new ReportCommand(this));
        getCommand("bc").setExecutor(new ReportCommand(this));
        getCommand("bcast").setExecutor(new ReportCommand(this));

        Bukkit.getPluginManager().registerEvents(new ReportListener(this), this);

        getLogger().info("✅ ReportsPlugin загружен успешно!");
    }

    @Override
    public void onDisable() {
        if (reportManager != null) {
            reportManager.saveReports();
        }
        getLogger().info("❌ ReportsPlugin выключен.");
    }

    public static ReportsPlugin getInstance() {
        return instance;
    }

    public ReportManager getReportManager() {
        return reportManager;
    }

    public TelegramNotifier getTelegramNotifier() {
        return telegramNotifier;
    }
}
