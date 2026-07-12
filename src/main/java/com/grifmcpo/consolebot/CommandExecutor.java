package com.grifmcpo.consolebot;

import net.kronos.rcon.RconClient;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class CommandExecutor {

    private final JavaPlugin plugin;
    private RconClient rcon;
    private boolean connected = false;
    private int reconnectAttempts = 0;
    private final int MAX_RECONNECT_ATTEMPTS = 10;
    private String host;
    private int port;
    private String password;

    public CommandExecutor(JavaPlugin plugin) {
        this.plugin = plugin;
        this.host = plugin.getConfig().getString("rcon.host", "localhost");
        this.port = plugin.getConfig().getInt("rcon.port", 25575);
        this.password = plugin.getConfig().getString("rcon.password");

        Bukkit.getScheduler().runTaskLater(plugin, this::connectRcon, 60L);
    }

    private void connectRcon() {
        try {
            if (password == null || password.isEmpty()) {
                plugin.getLogger().warning("❌ RCON пароль не указан!");
                return;
            }

            plugin.getLogger().info("🔄 Подключение к RCON " + host + ":" + port + "...");

            rcon = new RconClient(host, port, password);
            connected = true;
            reconnectAttempts = 0;
            plugin.getLogger().info("✅ RCON подключён к " + host + ":" + port);

        } catch (Exception e) {
            connected = false;
            plugin.getLogger().warning("❌ Ошибка подключения RCON: " + e.getMessage());

            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++;
                plugin.getLogger().info("🔄 Попытка " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + " через 5 сек...");
                Bukkit.getScheduler().runTaskLater(plugin, this::connectRcon, 100L);
            } else {
                plugin.getLogger().warning("❌ RCON не подключён. Команды через консоль.");
            }
        }
    }

    public String executeCommand(String command, String senderName) {
        if (!connected || rcon == null) {
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++;
                plugin.getLogger().info("🔄 Переподключение к RCON...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, this::connectRcon);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return "⚠️ RCON не подключён, команда через консоль: " + command;
        }

        try {
            String response = rcon.sendCommand(command);
            if (response == null || response.isEmpty()) {
                return "✅ Команда выполнена (ответа нет)";
            }
            return response;
        } catch (Exception e) {
            plugin.getLogger().warning("❌ Ошибка выполнения: " + e.getMessage());
            connected = false;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return "⚠️ Ошибка RCON, команда через консоль: " + command;
        }
    }

    public void close() {
        try {
            if (rcon != null) rcon.close();
            connected = false;
        } catch (Exception e) {}
    }

    public void reconnect() {
        close();
        connectRcon();
    }

    public boolean isConnected() {
        return connected;
    }
}
