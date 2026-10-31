package com.grifmcpo.consolebot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TelegramLinkCommand implements CommandExecutor {

    private final TelegramConsoleBot plugin;
    private final AccountLinkManager accountLinkManager;

    public TelegramLinkCommand(TelegramConsoleBot plugin, AccountLinkManager accountLinkManager) {
        this.plugin = plugin;
        this.accountLinkManager = accountLinkManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам.");
            return true;
        }

        if (!player.hasPermission("telegramconsolebot.link")) {
            sender.sendMessage("§cУ вас нет прав на использование команды /tg.");
            return true;
        }

        if (accountLinkManager.isPlayerLinked(player.getName())) {
            player.sendMessage("§cВаш аккаунт уже привязан к Telegram.");
            return true;
        }

        String code = accountLinkManager.createCodeForPlayer(player);
        if (code == null || code.isEmpty()) {
            player.sendMessage("§cНе удалось создать код привязки. Попробуйте позже.");
            return true;
        }

        player.sendMessage("§6[Telegram] §fВаш код привязки: §a" + code + "§f");
        player.sendMessage("§7Используйте этот код в боте Telegram для привязки аккаунта.");
        player.sendMessage("§7Код действует 10 минут.");
        plugin.getLogger().info("Сгенерирован код привязки для игрока " + player.getName() + ": " + code);
        return true;
    }
}
