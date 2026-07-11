
package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class TelegramConsoleBot extends JavaPlugin {

    private Map<String, String> admins = new HashMap<>();
    private long ownerId = 8889631346L;
    private File adminsFile;
    private PlayerManager playerManager;

    @Override
    public void onEnable() {
        getLogger().info("✅ ConsoleBot включен!");

        saveDefaultConfig();
        String token = getConfig().getString("telegram-token");
        if (token == null || token.isEmpty()) {
            getLogger().severe("❌ Токен не найден в config.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadAdmins();
        playerManager = new PlayerManager(this);

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new TelegramBotHandler(token, this, playerManager));
            getLogger().info("✅ Telegram-бот успешно зарегистрирован!");
        } catch (TelegramApiException e) {
            getLogger().severe("❌ Ошибка при регистрации бота: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("❌ ConsoleBot выключен.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("tg")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cЭта команда только для игроков!");
                return true;
            }

            Player player = (Player) sender;
            String playerName = player.getName();

            if (playerManager.isRegistered(playerName)) {
                player.sendMessage("§eВаш аккаунт уже привязан к Telegram!");
                player.sendMessage("§7Если хотите отвязать — используйте /unreg в Telegram");
                return true;
            }

            String code = playerManager.generateCode(playerName);
            player.sendMessage("§a🔑 Ваш код для привязки аккаунта:");
            player.sendMessage("§e" + code);
            player.sendMessage("§7Отправьте этот код боту в Telegram:");
            player.sendMessage("§f/register " + code);
            player.sendMessage("§8(Код действителен 5 минут)");
            return true;
        }
        return false;
    }

    private void loadAdmins() {
        adminsFile = new File(getDataFolder(), "admins.yml");
        if (!adminsFile.exists()) {
            saveResource("admins.yml", false);
        }
        reloadAdmins();
    }

    public void reloadAdmins() {
        admins.clear();
        if (adminsFile.exists()) {
            try {
                org.bukkit.configuration.file.YamlConfiguration config =
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(adminsFile);
                for (String key : config.getKeys(false)) {
                    admins.put(key, config.getString(key));
                }
            } catch (Exception e) {
                getLogger().warning("❌ Ошибка загрузки admins.yml: " + e.getMessage());
            }
        }
        getLogger().info("✅ Загружено администраторов: " + admins.size());
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
            getLogger().severe("❌ Ошибка сохранения admins.yml: " + e.getMessage());
        }
    }

    public Map<String, String> getAdmins() {
        return admins;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public void addAdmin(String telegramId, String playerName) {
        admins.put(telegramId, playerName);
        saveAdmins();
    }

    public void removeAdmin(String telegramId) {
        admins.remove(telegramId);
        saveAdmins();
    }

    public boolean isAdmin(long telegramId) {
        return admins.containsKey(String.valueOf(telegramId));
    }

    public String getCustomSender(long telegramId) {
        return admins.get(String.valueOf(telegramId));
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }
}
