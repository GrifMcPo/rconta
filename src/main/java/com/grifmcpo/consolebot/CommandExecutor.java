package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class CommandExecutor {

    private final JavaPlugin plugin;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String host;
    private int port;
    private String password;
    private boolean connected = false;
    private int reconnectAttempts = 0;
    private final int MAX_RECONNECT_ATTEMPTS = 10;

    public CommandExecutor(JavaPlugin plugin) {
        this.plugin = plugin;
        this.host = plugin.getConfig().getString("rcon.host", "localhost");
        this.port = plugin.getConfig().getInt("rcon.port", 25575);
        this.password = plugin.getConfig().getString("rcon.password");

        // Подключаемся с задержкой через 3 секунды (чтобы RCON успел запуститься)
        Bukkit.getScheduler().runTaskLater(plugin, this::connectRcon, 60L); // 60 тиков = 3 секунды
    }

    private void connectRcon() {
        try {
            if (password == null || password.isEmpty()) {
                plugin.getLogger().warning("❌ RCON пароль не указан в config.yml!");
                return;
            }

            if (socket != null && !socket.isClosed()) {
                return;
            }

            plugin.getLogger().info("🔄 Подключение к RCON " + host + ":" + port + "...");

            socket = new Socket(host, port);
            socket.setSoTimeout(5000);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            // Аутентификация
            int requestId = 1;
            byte[] body = password.getBytes("UTF-8");
            int size = 10 + body.length;
            out.writeInt(size);
            out.writeInt(requestId);
            out.writeInt(3);
            out.writeShort(body.length);
            out.write(body);
            out.flush();

            int responseSize = in.readInt();
            int responseId = in.readInt();
            int responseType = in.readInt();
            int responseBodyLength = in.readShort();
            byte[] responseBody = new byte[responseBodyLength];
            in.readFully(responseBody);

            if (responseId == -1) {
                plugin.getLogger().severe("❌ Ошибка аутентификации RCON! Неверный пароль.");
                socket.close();
                return;
            }

            connected = true;
            reconnectAttempts = 0;
            plugin.getLogger().info("✅ RCON подключён к " + host + ":" + port);

        } catch (Exception e) {
            connected = false;
            plugin.getLogger().warning("❌ Ошибка подключения RCON: " + e.getMessage());

            // Пробуем переподключиться
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++;
                plugin.getLogger().info("🔄 Попытка переподключения " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + " через 5 секунд...");
                Bukkit.getScheduler().runTaskLater(plugin, this::connectRcon, 100L);
            } else {
                plugin.getLogger().warning("❌ Не удалось подключиться к RCON после " + MAX_RECONNECT_ATTEMPTS + " попыток. Команды будут выполняться через консоль.");
            }
        }
    }

    public String executeCommand(String command, String senderName) {
        // Проверяем соединение
        if (!connected || socket == null || socket.isClosed()) {
            // Пробуем переподключиться
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++;
                plugin.getLogger().info("🔄 Переподключение к RCON...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, this::connectRcon);
            }
            // Выполняем через консоль
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return "⚠️ RCON не подключён, команда выполнена через консоль";
        }

        try {
            int requestId = 2;
            byte[] body = command.getBytes("UTF-8");
            int size = 10 + body.length;
            out.writeInt(size);
            out.writeInt(requestId);
            out.writeInt(2);
            out.writeShort(body.length);
            out.write(body);
            out.flush();

            int responseSize = in.readInt();
            int responseId = in.readInt();
            int responseType = in.readInt();
            int responseBodyLength = in.readShort();
            byte[] responseBody = new byte[responseBodyLength];
            in.readFully(responseBody);

            String response = new String(responseBody, "UTF-8");

            // Читаем пустой пакет
            try {
                int emptySize = in.readInt();
                in.readInt();
                in.readInt();
                int emptyBodyLen = in.readShort();
                if (emptyBodyLen > 0) {
                    byte[] emptyBody = new byte[emptyBodyLen];
                    in.readFully(emptyBody);
                }
            } catch (Exception e) {
                // Игнорируем
            }

            if (response == null || response.isEmpty()) {
                return "✅ Команда выполнена (ответа нет)";
            }
            return response;

        } catch (Exception e) {
            plugin.getLogger().warning("❌ Ошибка выполнения команды: " + e.getMessage());
            connected = false;
            // Пробуем переподключиться
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::connectRcon);
            return "❌ Ошибка: " + e.getMessage();
        }
    }

    public void close() {
        try {
            if (socket != null) socket.close();
            connected = false;
        } catch (Exception e) {
            // игнорируем
        }
    }

    public void reconnect() {
        close();
        connectRcon();
    }

    public boolean isConnected() {
        return connected;
    }
}
