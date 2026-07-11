package com.grifmcpo.rconhandler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class RCONHandler extends JavaPlugin implements Listener {

    // ========== НАСТРОЙКИ (меняй здесь!) ==========
    // Твой Telegram ID (цифры)
    private static final long YOUR_TELEGRAM_ID = 8308522569L;
    
    // Текст, который будет подставляться вместо "Console"
    private static final String CUSTOM_NAME = "RCON@pley1657";
    // =============================================

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("✅ RCONHandler включен!");
        getLogger().info("📌 Команды с Telegram ID " + YOUR_TELEGRAM_ID + " будут выполняться от имени " + CUSTOM_NAME);
    }

    @Override
    public void onDisable() {
        getLogger().info("❌ RCONHandler выключен.");
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        // Проверяем, что команда пришла от RCON или консоли
        if (!event.getSender().getName().equals("Console")) {
            return;
        }

        String command = event.getCommand();
        if (command == null || command.isEmpty()) {
            return;
        }

        // Проверяем, содержит ли команда твой Telegram ID
        // (Бот будет добавлять его в команду, например: "8308522569:ban Игрок")
        if (command.startsWith(YOUR_TELEGRAM_ID + ":")) {
            try {
                // Извлекаем команду без ID
                String actualCommand = command.substring((YOUR_TELEGRAM_ID + ":").length()).trim();

                if (actualCommand.isEmpty()) {
                    event.setCancelled(true);
                    getLogger().warning("❌ Команда пуста!");
                    return;
                }

                // Выполняем команду от имени CUSTOM_NAME
                getLogger().info("📤 Выполняем команду от имени " + CUSTOM_NAME + ": " + actualCommand);
                
                // Подменяем отправителя
                event.setCommand(actualCommand);
                // Меняем имя отправителя на CUSTOM_NAME
                event.getSender().setName(CUSTOM_NAME);

            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "❌ Ошибка при обработке команды: " + e.getMessage());
            }
        }
    }
}
