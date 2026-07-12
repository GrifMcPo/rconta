package com.grifmcpo.consolebot;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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

        Bukkit.getScheduler().runTaskLater(plugin, this::connectRcon, 60L);
    }

    private void connectRcon() {
        try {
            if (password == null || password.isEmpty()) {
                plugin.getLogger().warning("❌ RCON пароль не указан!");
                return;
            }

            plugin.getLogger().info("🔄 Подключение к RCON " + host + ":" + port + "...");

            socket = new Socket(host, port);
            socket.setSoTimeout(10000);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            // --- АУТЕНТИФИКАЦИЯ ---
            int requestId = 1;
            byte[] body = password.getBytes("UTF-8");
            
            // Пакет: размер (4) + requestId (4) + type (4) + body (N) + \0 (2)
            int packetSize = 10 + body.length;
            ByteBuffer packet = ByteBuffer.allocate(packetSize);
            packet.order(ByteOrder.LITTLE_ENDIAN);
            packet.putInt(packetSize);
            packet.putInt(requestId);
            packet.putInt(3); // SERVERDATA_AUTH
            packet.put(body);
            packet.putShort((short) 0);

            out.write(packet.array());
            out.flush();

            // --- ЧТЕНИЕ ОТВЕТА ---
            int size = in.readInt();
            int responseId = in.readInt();
            int type = in.readInt();
            
            // Читаем тело
            byte[] responseBody = new byte[size - 8];
            in.readFully(responseBody);
            
            // Читаем завершающие нули
            in.readShort();

            if (responseId == -1) {
                plugin.getLogger().severe("❌ Неверный пароль RCON!");
                socket.close();
                return;
            }

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
        if (!connected || socket == null || socket.isClosed()) {
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, this::connectRcon);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return "⚠️ RCON не подключён, команда через консоль: " + command;
        }

        try {
            // --- ОТПРАВКА КОМАНДЫ ---
            int requestId = 2;
            byte[] body = command.getBytes("UTF-8");
            
            int packetSize = 10 + body.length;
            ByteBuffer packet = ByteBuffer.allocate(packetSize);
            packet.order(ByteOrder.LITTLE_ENDIAN);
            packet.putInt(packetSize);
            packet.putInt(requestId);
            packet.putInt(2); // SERVERDATA_EXECCOMMAND
            packet.put(body);
            packet.putShort((short) 0);

            out.write(packet.array());
            out.flush();

            // --- ЧТЕНИЕ ОТВЕТА ---
            int size = in.readInt();
            int responseId = in.readInt();
            int type = in.readInt();
            
            byte[] responseBody = new byte[size - 8];
            in.readFully(responseBody);
            in.readShort();

            String response = new String(responseBody, "UTF-8").trim();

            // Читаем второй пакет (RCON отправляет 2 пакета)
            try {
                int size2 = in.readInt();
                if (size2 > 0) {
                    in.readInt(); // id
                    in.readInt(); // type
                    byte[] body2 = new byte[size2 - 8];
                    in.readFully(body2);
                    in.readShort();
                }
            } catch (Exception e) {
                // Игнорируем, если второго пакета нет
            }

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
            if (socket != null) socket.close();
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
