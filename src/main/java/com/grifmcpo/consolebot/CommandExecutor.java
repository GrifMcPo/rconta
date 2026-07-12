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

    public CommandExecutor(JavaPlugin plugin) {
        this.plugin = plugin;
        this.host = plugin.getConfig().getString("rcon.host", "localhost");
        this.port = plugin.getConfig().getInt("rcon.port", 25575);
        this.password = plugin.getConfig().getString("rcon.password");
        connectRcon();
    }

    private void connectRcon() {
        try {
            if (password == null || password.isEmpty()) {
                plugin.getLogger().warning("❌ RCON пароль не указан в config.yml!");
                return;
            }

            socket = new Socket(host, port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            // Отправляем команду аутентификации (RCON протокол)
            // Пакет: size, requestId, type, body
            int requestId = 1;
            byte[] body = password.getBytes("UTF-8");
            int size = 10 + body.length; // 4 + 4 + 2 + body.length
            out.writeInt(size);
            out.writeInt(requestId);
            out.writeInt(3); // SERVERDATA_AUTH
            out.writeShort(body.length);
            out.write(body);
            out.flush();

            // Читаем ответ
            int responseSize = in.readInt();
            int responseId = in.readInt();
            int responseType = in.readInt();
            int responseBodyLength = in.readShort();
            byte[] responseBody = new byte[responseBodyLength];
            in.readFully(responseBody);

            String response = new String(responseBody, "UTF-8");
            if (responseId == -1) {
                plugin.getLogger().severe("❌ Ошибка аутентификации RCON! Неверный пароль.");
                socket.close();
                return;
            }

            connected = true;
            plugin.getLogger().info("✅ RCON подключён к " + host + ":" + port);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка подключения RCON: " + e.getMessage());
            connected = false;
        }
    }

    public String executeCommand(String command, String senderName) {
        if (!connected || socket == null || socket.isClosed()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return "⚠️ RCON не подключён, команда выполнена через консоль";
        }

        try {
            // Отправляем команду
            int requestId = 2;
            byte[] body = command.getBytes("UTF-8");
            int size = 10 + body.length;
            out.writeInt(size);
            out.writeInt(requestId);
            out.writeInt(2); // SERVERDATA_EXECCOMMAND
            out.writeShort(body.length);
            out.write(body);
            out.flush();

            // Читаем ответ
            int responseSize = in.readInt();
            int responseId = in.readInt();
            int responseType = in.readInt();
            int responseBodyLength = in.readShort();
            byte[] responseBody = new byte[responseBodyLength];
            in.readFully(responseBody);

            String response = new String(responseBody, "UTF-8");

            if (response == null || response.isEmpty()) {
                return "✅ Команда выполнена (ответа нет)";
            }

            // Читаем ещё один пустой пакет (RCON протокол отправляет 2 пакета)
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
                // Игнорируем, если пустого пакета нет
            }

            return response;
        } catch (Exception e) {
            plugin.getLogger().warning("❌ Ошибка выполнения команды: " + e.getMessage());
            connected = false;
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
