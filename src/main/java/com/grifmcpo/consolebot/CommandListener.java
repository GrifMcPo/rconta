package com.grifmcpo.consolebot;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class CommandListener implements Listener {

    private final CommandLogger commandLogger;
    private final PunishmentManager punishmentManager;

    // Список заблокированных команд (из FlectonePulse)
    private final String[] BLOCKED_COMMANDS = {
        "/ban", "/tempban", "/unban",
        "/mute", "/tempmute", "/unmute",
        "/kick",
        "/warn", "/unwarn",
        "/jail", "/unjail"
    };

    public CommandListener(CommandLogger commandLogger, PunishmentManager punishmentManager) {
        this.commandLogger = commandLogger;
        this.punishmentManager = punishmentManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        // Логируем команду
        commandLogger.logCommand(player.getName(), event.getMessage());

        // Блокируем команды наказаний из FlectonePulse
        for (String blocked : BLOCKED_COMMANDS) {
            if (command.startsWith(blocked) || command.startsWith("flectonepulse:" + blocked)) {
                event.setCancelled(true);
                player.sendMessage("§c⛔ Эта команда отключена!");
                player.sendMessage("§7Используйте Telegram-бота для наказаний.");
                return;
            }
        }

        // Проверка мута (блокировка чата)
        if (command.startsWith("/") && !command.startsWith("/msg") && !command.startsWith("/tell") && !command.startsWith("/w")) {
            // Игнорируем команды, которые не влияют на чат
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!punishmentManager.canPlayerChat(player)) {
            event.setCancelled(true);
            String issuer = punishmentManager.getMuteIssuer(player.getName());
            String reason = punishmentManager.getMuteReason(player.getName());
            player.sendMessage("§cВы замучены!");
            player.sendMessage("§fПричина: §e" + reason);
            player.sendMessage("§fВыдал: §e" + issuer);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        punishmentManager.checkOnJoin(player);
    }
}
