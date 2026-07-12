package com.grifmcpo.consolebot;

import net.johnewart.rconclient.RconClient;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public class CommandExecutor {

    private final JavaPlugin plugin;
    private RconClient rcon;

    public CommandExecutor(JavaPlugin plugin) {
        this.plugin = plugin;
        connectRcon();
    }

    private void connectRcon() {
        try {
            String host = plugin.getConfig().getString("rcon.host", "localhost");
            int port = plugin.getConfig().getInt("rcon.port", 25575);
            String password = plugin.getConfig().getString("rcon.password");

            if (password == null || password.isEmpty()) {
                plugin.getLogger().warning("❌ RCON пароль не указан в config.yml!");
                return;
            }

            rcon = new RconClient(host, port, password);
            plugin.getLogger().info("✅ RCON подключён к " + host + ":" + port);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка подключения RCON: " + e.getMessage());
        }
    }

    public String executeCommand(String command, String senderName) {
        if (rcon == null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return "⚠️ RCON не подключён, команда выполнена через консоль";
        }

        try {
            String response = rcon.sendCommand(command);
            if (response == null || response.isEmpty()) {
                return "✅ Команда выполнена (ответа нет)";
            }
            return response;
        } catch (IOException e) {
            plugin.getLogger().warning("❌ Ошибка выполнения команды: " + e.getMessage());
            return "❌ Ошибка: " + e.getMessage();
        }
    }

    public void close() {
        if (rcon != null) {
            try {
                rcon.disconnect();
            } catch (Exception e) {
                // игнорируем
            }
        }
    }

    public void reconnect() {
        close();
        connectRcon();
    }
}
