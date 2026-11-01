package com.grifmcpo.consolebot;

import com.grifmcpo.consolebot.storage.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TelegramConsoleBot extends JavaPlugin {

    private Map<String, String> admins = new HashMap<>();
    private long ownerId = 8308522569L;
    private File adminsFile;

    private PlayerManager playerManager;
    private CommandLogger commandLogger;
    private LogsCommand logsCommand;
    private CommandExecutor commandExecutor;
    private PunishmentManager punishmentManager;
    private AdminLogger adminLogger;
    private BotBanManager botBanManager;
    private GroupManager groupManager;
    private TechWorksManager techWorksManager;
    private PrivateMessageManager privateMessageManager;
    private ReportManager reportManager;
    private BotManager botManager;
    private AccountLinkManager accountLinkManager;
    private TelegramBotHandler botHandler;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        getLogger().info("ConsoleBot включен!");

        saveDefaultConfig();
        databaseManager = new DatabaseManager(this);
        databaseManager.init();
        databaseManager.scheduleCleanup();

        String token = getConfig().getString("telegram-token");
        if (token == null || token.isEmpty()) {
            getLogger().severe("Токен не найден в config.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadAdmins();
        syncLuckPermsPermissionsForExistingGroups();

        playerManager = new PlayerManager(this, databaseManager);
        commandLogger = new CommandLogger(this);
        logsCommand = new LogsCommand(this);
        commandExecutor = new CommandExecutor(this);
        adminLogger = new AdminLogger(this);
        punishmentManager = new PunishmentManager(this, adminLogger);
        botBanManager = new BotBanManager(this);
        groupManager = new GroupManager(this);
        techWorksManager = new TechWorksManager(this);
        privateMessageManager = new PrivateMessageManager(this);
        reportManager = new ReportManager(this);
        botManager = new BotManager(this);
        accountLinkManager = new AccountLinkManager(this);

        Bukkit.getPluginManager().registerEvents(punishmentManager, this);
        Bukkit.getPluginManager().registerEvents(commandLogger, this);
        Bukkit.getPluginManager().registerEvents(techWorksManager, this);
        Bukkit.getPluginManager().registerEvents(privateMessageManager, this);
        Bukkit.getPluginManager().registerEvents(botManager, this);

        PlayerCommands playerCommands = new PlayerCommands(this, punishmentManager, playerManager, commandLogger);

        try {
            getCommand("ban").setExecutor(playerCommands);
            getCommand("banuuid").setExecutor(playerCommands);
            getCommand("mute").setExecutor(playerCommands);
            getCommand("kick").setExecutor(playerCommands);
            getCommand("warn").setExecutor(playerCommands);
            getCommand("unwarn").setExecutor(playerCommands);
            getCommand("bc").setExecutor(playerCommands);
            getCommand("logs").setExecutor(playerCommands);
            getCommand("dupeip").setExecutor(playerCommands);
            getCommand("seen").setExecutor(playerCommands);
            getCommand("pex").setExecutor(playerCommands);
            getLogger().info("Все команды для игроков зарегистрированы!");
        } catch (NullPointerException e) {
            getLogger().warning("Некоторые команды не найдены в plugin.yml");
        }

        try {
            getCommand("tg").setExecutor(new TelegramLinkCommand(this, accountLinkManager));
            getLogger().info("✅ Команда /tg зарегистрирована!");
        } catch (NullPointerException e) {
            getLogger().warning("Команда /tg не найдена в plugin.yml");
        }

        PrivateMessageCommands pmCommands = new PrivateMessageCommands(privateMessageManager);
        try {
            getCommand("msg").setExecutor(pmCommands);
            getCommand("tell").setExecutor(pmCommands);
            getCommand("t").setExecutor(pmCommands);
            getCommand("r").setExecutor(pmCommands);
            getCommand("reply").setExecutor(pmCommands);
            getCommand("togglemsg").setExecutor(pmCommands);
            getCommand("msgtoggle").setExecutor(pmCommands);
            getLogger().info("✅ Команды ЛС зарегистрированы!");
        } catch (NullPointerException e) {
            getLogger().warning("Некоторые команды ЛС не найдены в plugin.yml");
        }

        try {
            getCommand("report").setExecutor(new ReportCommand(this, reportManager));
            getLogger().info("✅ Команда /report зарегистрирована!");
        } catch (NullPointerException e) {
            getLogger().warning("Команда /report не найдена в plugin.yml");
        }

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botHandler = new TelegramBotHandler(token, this, playerManager, commandLogger, logsCommand,
                    commandExecutor, punishmentManager, botBanManager, groupManager);
            botsApi.registerBot(botHandler);
            getLogger().info("Telegram-бот успешно зарегистрирован!");
        } catch (TelegramApiException e) {
            getLogger().severe("Ошибка при регистрации бота: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void syncLuckPermsPermissionsForExistingGroups() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            getLogger().warning("LuckPerms не найден: пропускаю выдачу прав группам.");
            return;
        }

        Map<String, List<String>> permissionsByGroup = new LinkedHashMap<>();
        permissionsByGroup.put("default", Collections.emptyList());
        permissionsByGroup.put("premium", Collections.emptyList());
        permissionsByGroup.put("creativ", Collections.emptyList());
        permissionsByGroup.put("moder", Arrays.asList(
                "telegramconsolebot.ban",
                "telegramconsolebot.link"
        ));
        permissionsByGroup.put("admin", Arrays.asList(
                "telegramconsolebot.ban",
                "telegramconsolebot.mute",
                "telegramconsolebot.link"
        ));
        permissionsByGroup.put("lord", Arrays.asList(
                "telegramconsolebot.ban",
                "telegramconsolebot.mute",
                "telegramconsolebot.kick",
                "telegramconsolebot.link"
        ));
        permissionsByGroup.put("admin+", Arrays.asList(
                "telegramconsolebot.ban",
                "telegramconsolebot.mute",
                "telegramconsolebot.kick",
                "telegramconsolebot.link"
        ));
        permissionsByGroup.put("creator", Arrays.asList(
                "telegramconsolebot.ban",
                "telegramconsolebot.mute",
                "telegramconsolebot.kick",
                "telegramconsolebot.link"
        ));
        permissionsByGroup.put("founder", Arrays.asList(
                "telegramconsolebot.ban",
                "telegramconsolebot.mute",
                "telegramconsolebot.kick",
                "telegramconsolebot.link"
        ));
        permissionsByGroup.put("owner", Arrays.asList(
                "telegramconsolebot.ban",
                "telegramconsolebot.mute",
                "telegramconsolebot.kick",
                "telegramconsolebot.link"
        ));
        permissionsByGroup.put("console", Arrays.asList(
                "telegramconsolebot.ban",
                "telegramconsolebot.mute",
                "telegramconsolebot.kick",
                "telegramconsolebot.link"
        ));
        permissionsByGroup.put("caesar", Arrays.asList(
                "telegramconsolebot.ban",
                "telegramconsolebot.mute",
                "telegramconsolebot.kick",
                "telegramconsolebot.link"
        ));
        permissionsByGroup.put("server", Arrays.asList(
                "telegramconsolebot.ban",
                "telegramconsolebot.mute",
                "telegramconsolebot.kick",
                "telegramconsolebot.link"
        ));
        permissionsByGroup.put("helper", Arrays.asList(
                "telegramconsolebot.ban",
                "telegramconsolebot.kick",
                "telegramconsolebot.dupeip",
                "telegramconsolebot.seen",
                "telegramconsolebot.pex",
                "telegramconsolebot.link"
        ));
        permissionsByGroup.put("hype", Arrays.asList(
                "telegramconsolebot.ban",
                "telegramconsolebot.mute",
                "telegramconsolebot.kick",
                "telegramconsolebot.dupeip",
                "telegramconsolebot.seen",
                "telegramconsolebot.pex",
                "telegramconsolebot.link"
        ));
        permissionsByGroup.put("staff", Arrays.asList(
                "telegramconsolebot.ban",
                "telegramconsolebot.mute",
                "telegramconsolebot.kick",
                "telegramconsolebot.warn",
                "telegramconsolebot.unwarn",
                "telegramconsolebot.bc",
                "telegramconsolebot.logs",
                "telegramconsolebot.dupeip",
                "telegramconsolebot.seen",
                "telegramconsolebot.pex",
                "telegramconsolebot.msg",
                "telegramconsolebot.report",
                "telegramconsolebot.link"
        ));
        permissionsByGroup.put("leader", Arrays.asList(
                "telegramconsolebot.ban",
                "telegramconsolebot.mute",
                "telegramconsolebot.kick",
                "telegramconsolebot.warn",
                "telegramconsolebot.unwarn",
                "telegramconsolebot.bc",
                "telegramconsolebot.logs",
                "telegramconsolebot.dupeip",
                "telegramconsolebot.seen",
                "telegramconsolebot.pex",
                "telegramconsolebot.msg",
                "telegramconsolebot.report",
                "telegramconsolebot.link"
        ));
        permissionsByGroup.put("sponsor", Arrays.asList(
                "telegramconsolebot.ban",
                "telegramconsolebot.mute",
                "telegramconsolebot.kick",
                "telegramconsolebot.warn",
                "telegramconsolebot.unwarn",
                "telegramconsolebot.bc",
                "telegramconsolebot.logs",
                "telegramconsolebot.dupeip",
                "telegramconsolebot.seen",
                "telegramconsolebot.pex",
                "telegramconsolebot.msg",
                "telegramconsolebot.report",
                "telegramconsolebot.link"
        ));

        List<String> groups = new ArrayList<>(permissionsByGroup.keySet());
        int granted = 0;

        for (Map.Entry<String, List<String>> entry : permissionsByGroup.entrySet()) {
            String groupName = entry.getKey();
            List<String> perms = entry.getValue();

            for (String perm : perms) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group " + groupName + " permission set " + perm + " true");
                granted++;
            }
        }

        getLogger().info("✅ Выдано LuckPerms прав для групп: " + groups.size() + " | всего операций: " + granted);
    }

    @Override
    public void onDisable() {
        getLogger().info("ConsoleBot выключен.");
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (commandLogger != null) commandLogger.saveLogs();
        if (commandExecutor != null) commandExecutor.close();
    }

    private void loadAdmins() {
        adminsFile = new File(getDataFolder(), "admins.yml");
        if (!adminsFile.exists()) saveResource("admins.yml", false);
        reloadAdmins();
    }

    public void reloadAdmins() {
        admins.clear();
        if (adminsFile.exists()) {
            try {
                org.bukkit.configuration.file.YamlConfiguration config =
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(adminsFile);
                for (String key : config.getKeys(false)) {
                    String value = config.getString(key);
                    if (key != null && value != null && !value.trim().isEmpty()) {
                        admins.put(key, value);
                    }
                }
            } catch (Exception e) {
                getLogger().warning("Ошибка загрузки admins.yml: " + e.getMessage());
            }
        }

        if (!admins.containsKey(String.valueOf(ownerId))) {
            admins.put(String.valueOf(ownerId), "Owner");
        }

        getLogger().info("Загружено администраторов: " + admins.size());
    }

    public void saveAdmins() {
        try {
            org.bukkit.configuration.file.YamlConfiguration config =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(adminsFile);
            for (Map.Entry<String, String> entry : admins.entrySet()) {
                config.set(entry.getKey(), entry.getValue());
            }
            config.save(adminsFile);
        } catch (Exception e) {
            getLogger().severe("Ошибка сохранения admins.yml: " + e.getMessage());
        }
    }

    public void sendMessageAsBot(long chatId, String text) {
        if (botHandler != null) {
            botHandler.sendMessage(chatId, text);
        }
    }

    public String getPlayerIp(String playerName) {
        return playerManager.getPlayerIp(playerName);
    }

    public List<String> getPlayersByIp(String ip) {
        return playerManager.getPlayersByIp(ip);
    }

    public Map<String, String> getAdmins() { return admins; }
    public long getOwnerId() { return ownerId; }
    public void addAdmin(String telegramId, String playerName) { admins.put(telegramId, playerName); saveAdmins(); }
    public void removeAdmin(String telegramId) { admins.remove(telegramId); saveAdmins(); }
    public boolean isAdmin(long telegramId) { return admins.containsKey(String.valueOf(telegramId)) || telegramId == ownerId; }
    public String getCustomSender(long telegramId) {
        String key = String.valueOf(telegramId);

        if (admins.containsKey(key)) {
            String name = admins.get(key);
            if (name != null && !name.trim().isEmpty()) {
                return name;
            }
        }

        if (telegramId == ownerId) {
            return "Owner";
        }

        return "RCON@" + telegramId;
    }
    public PlayerManager getPlayerManager() { return playerManager; }
    public CommandLogger getCommandLogger() { return commandLogger; }
    public CommandExecutor getCommandExecutor() { return commandExecutor; }
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public AdminLogger getAdminLogger() { return adminLogger; }
    public BotBanManager getBotBanManager() { return botBanManager; }
    public GroupManager getGroupManager() { return groupManager; }
    public TechWorksManager getTechWorksManager() { return techWorksManager; }
    public PrivateMessageManager getPrivateMessageManager() { return privateMessageManager; }
    public ReportManager getReportManager() { return reportManager; }
    public BotManager getBotManager() { return botManager; }
    public AccountLinkManager getAccountLinkManager() { return accountLinkManager; }
    public TelegramBotHandler getBotHandler() { return botHandler; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
}

