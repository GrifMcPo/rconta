package com.grifmcpo.rconhandler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class RCONHandler extends JavaPlugin implements Listener {

    // ========== НАСТРОЙКИ (меняй здесь!) ==========
    // Соответствие: Telegram ID -> Имя, которое будет подставлено
    private final Map<String, String> senderMap = new HashMap<>();
    // =============================================

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        loadMappings();
        getLogger().info("✅ RCONHandler включен!");
        getLogger().info("📌 Загружено привязок: " + senderMap.size());
    }

    @Override
    public void onDisable() {
        getLogger().info("❌ RCONHandler выключен.");
    }

    // Загружаем соответствия Telegram ID -> Имя
    private void loadMappings() {
        senderMap.clear();
        
        // =============================================
        // ТУТ ДОБАВЛЯЙ СВОИ ПРИВЯЗКИ!
        // Формат: senderMap.put("TelegramID", "НовоеИмя");
        // =============================================
        senderMap.put("8308522569", "RCON@pley1657");  // Твой ID
        senderMap.put("7627820921", "RCON@rewte42");   // ID друга (пример)
        // Добавляй сюда других игроков
        // =============================================
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        // Проверяем, что команда пришла от консоли (RCON)
        if (!event.getSender().getName().equals("Console")) {
            return;
        }

        String command = event.getCommand();
        if (command == null || command.isEmpty()) {
            return;
        }

        // Проверяем команду на наличие Telegram ID
        for (Map.Entry<String, String> entry : senderMap.entrySet()) {
            String telegramId = entry.getKey();
            String newSenderName = entry.getValue();

            // Если команда начинается с Telegram ID
            if (command.startsWith(telegramId + ":")) {
                try {
                    // Извлекаем команду без ID
                    String actualCommand = command.substring((telegramId + ":").length()).trim();

                    if (actualCommand.isEmpty()) {
                        event.setCancelled(true);
                        getLogger().warning("❌ Команда от " + telegramId + " пуста!");
                        return;
                    }

                    // Выполняем команду от нового имени
                    getLogger().info("📤 Команда от " + newSenderName + ": " + actualCommand);
                    
                    // Запускаем команду от имени игрока (если он онлайн) или от нового имени
                    Player player = Bukkit.getPlayerExact(newSenderName.replace("RCON@", ""));
                    if (player != null && player.isOnline()) {
                        // Если игрок онлайн, выполняем от его имени
                        Bukkit.dispatchCommand(player, actualCommand);
                    } else {
                        // Если не онлайн — просто от консоли с логом
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), actualCommand);
                        getLogger().warning("⚠️ Игрок " + newSenderName + " не онлайн, команда выполнена от консоли.");
                    }

                    // Отменяем выполнение исходной команды консолью
                    event.setCancelled(true);
                    return;

                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "❌ Ошибка при обработке команды от " + telegramId + ": " + e.getMessage());
                }
            }
        }
    }
}
