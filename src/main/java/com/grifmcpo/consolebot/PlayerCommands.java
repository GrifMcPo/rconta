package com.grifmcpo.consolebot;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerCommands implements CommandExecutor {

    private static final Map<String, Integer> GROUP_PRIORITY = createGroupPriority();
    private static final Map<String, Long> MAX_BAN_TIME = createMaxBanTime();
    private static final Map<String, Long> MAX_MUTE_TIME = createMaxMuteTime();
    private static final Map<String, Long> MAX_KICK_TIME = createMaxKickTime();

    private final TelegramConsoleBot plugin;
    private final PunishmentManager punishmentManager;
    private final PlayerManager playerManager;
    private final CommandLogger commandLogger;

    public PlayerCommands(TelegramConsoleBot plugin, PunishmentManager punishmentManager,
                          PlayerManager playerManager, CommandLogger commandLogger) {
        this.plugin = plugin;
        this.punishmentManager = punishmentManager;
        this.playerManager = playerManager;
        this.commandLogger = commandLogger;
    }

    private static Map<String, Integer> createGroupPriority() {
        Map<String, Integer> map = new HashMap<>();
        map.put("default", 0);
        map.put("premium", 1);
        map.put("creativ", 2);
        map.put("moder", 3);
        map.put("admin", 4);
        map.put("lord", 5);
        map.put("admin+", 6);
        map.put("creator", 7);
        map.put("founder", 8);
        map.put("owner", 9);
        map.put("console", 10);
        map.put("caesar", 11);
        map.put("server", 12);
        map.put("helper", 13);
        map.put("hype", 14);
        map.put("staff", 15);
        map.put("leader", 16);
        map.put("sponsor", 17);
        return map;
    }

    private static Map<String, Long> createMaxBanTime() {
        Map<String, Long> map = new HashMap<>();
        map.put("moder", parseDurationToMillis("10m"));
        map.put("admin", parseDurationToMillis("20m"));
        map.put("lord", parseDurationToMillis("30m"));
        map.put("admin+", parseDurationToMillis("30m"));
        map.put("creator", parseDurationToMillis("40m"));
        map.put("founder", parseDurationToMillis("1h"));
        map.put("owner", parseDurationToMillis("1h"));
        map.put("console", parseDurationToMillis("2h"));
        map.put("caesar", parseDurationToMillis("12h"));
        map.put("server", parseDurationToMillis("1d"));
        map.put("helper", parseDurationToMillis("7d"));
        map.put("hype", Long.MAX_VALUE);
        map.put("staff", Long.MAX_VALUE);
        map.put("leader", Long.MAX_VALUE);
        map.put("sponsor", Long.MAX_VALUE);
        return map;
    }

    private static Map<String, Long> createMaxMuteTime() {
        Map<String, Long> map = new HashMap<>();
        map.put("admin", parseDurationToMillis("20m"));
        map.put("lord", parseDurationToMillis("30m"));
        map.put("admin+", parseDurationToMillis("30m"));
        map.put("creator", parseDurationToMillis("40m"));
        map.put("founder", parseDurationToMillis("1h"));
        map.put("owner", parseDurationToMillis("1h"));
        map.put("console", parseDurationToMillis("2h"));
        map.put("caesar", parseDurationToMillis("12h"));
        map.put("server", parseDurationToMillis("1d"));
        map.put("helper", parseDurationToMillis("7d"));
        map.put("hype", Long.MAX_VALUE);
        map.put("staff", Long.MAX_VALUE);
        map.put("leader", Long.MAX_VALUE);
        map.put("sponsor", Long.MAX_VALUE);
        return map;
    }

    private static Map<String, Long> createMaxKickTime() {
        Map<String, Long> map = new HashMap<>();
        map.put("moder", 0L);
        map.put("admin", 0L);
        map.put("lord", parseDurationToMillis("30m"));
        map.put("admin+", parseDurationToMillis("30m"));
        map.put("creator", parseDurationToMillis("40m"));
        map.put("founder", parseDurationToMillis("1h"));
        map.put("owner", parseDurationToMillis("1h"));
        map.put("console", parseDurationToMillis("2h"));
        map.put("caesar", parseDurationToMillis("12h"));
        map.put("server", parseDurationToMillis("1d"));
        map.put("helper", parseDurationToMillis("7d"));
        map.put("hype", Long.MAX_VALUE);
        map.put("staff", Long.MAX_VALUE);
        map.put("leader", Long.MAX_VALUE);
        map.put("sponsor", Long.MAX_VALUE);
        return map;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "ban":
            case "tban":
                return handleBan(sender, args);
            case "banuuid":
                return handleBanUuid(sender, args);
            case "mute":
            case "tmute":
                return handleMute(sender, args);
            case "unmute":
            case "tunmute":
                return handleUnmute(sender, args);
            case "unban":
            case "tunban":
                return handleUnban(sender, args);
            case "unbanip":
                return handleUnbanIp(sender, args);
            case "kick":
            case "tkick":
                return handleKick(sender, args);
            case "warn":
            case "twarn":
                return handleWarn(sender, args);
            case "unwarn":
            case "tunwarn":
                return handleUnwarn(sender, args);
            case "bc":
            case "tbc":
                return handleBroadcast(sender, args);
            case "logs":
            case "tlogs":
                return handleLogs(sender, args);
            case "dupeip":
            case "tdupeip":
                return handleDupeip(sender, args);
            case "seen":
            case "tseen":
                return handleSeen(sender, args);
            case "pex":
            case "tpex":
                return handlePex(sender, args);
            default:
                return false;
        }
    }

    private Permission getPermissionProvider() {
        try {
            var reg = Bukkit.getServicesManager().getRegistration(Permission.class);
            return reg == null ? null : reg.getProvider();
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeGroup(String group) {
        if (group == null || group.trim().isEmpty()) {
            return "default";
        }
        return group.trim().toLowerCase(Locale.ROOT);
    }

    private String getSenderGroup(CommandSender sender) {
        if (sender instanceof Player player) {
            Permission permission = getPermissionProvider();
            if (permission != null) {
                return normalizeGroup(permission.getPrimaryGroup(player));
            }
        }
        return "console";
    }

    private String getTargetGroup(String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            return "default";
        }
        Permission permission = getPermissionProvider();
        if (permission == null) {
            return "default";
        }
        return normalizeGroup(permission.getPrimaryGroup(player));
    }

    private int getGroupPriority(String group) {
        String normalized = normalizeGroup(group);
        Integer priority = GROUP_PRIORITY.get(normalized);
        return priority == null ? -1 : priority;
    }

    private boolean canUseAction(CommandSender sender, String action) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player) || sender.hasPermission("telegramconsolebot.admin")) {
            return true;
        }

        String permission = "telegramconsolebot." + action.toLowerCase(Locale.ROOT);
        return sender.hasPermission(permission);
    }

    private boolean canUseHiddenFlag(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission("telegramconsolebot.admin") || sender.hasPermission("telegramconsolebot.staff");
    }

    private boolean canPunishTarget(CommandSender sender, String playerName, String action) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player) || sender.hasPermission("telegramconsolebot.admin")) {
            return true;
        }
        return canUseAction(sender, action);
    }

    private String clampDurationByGroup(CommandSender sender, String action, String duration) {
        if (duration == null || duration.isEmpty() || duration.equalsIgnoreCase("навсегда")) {
            return "навсегда";
        }
        if (!punishmentManager.isValidTime(duration)) {
            return duration;
        }

        String group = getSenderGroup(sender);
        long maxMillis = getMaxDurationForGroup(group, action);
        if (maxMillis <= 0) {
            return duration;
        }

        long parsed = parseDurationToMillis(duration);
        if (parsed > maxMillis) {
            return millisToDuration(maxMillis);
        }
        return duration;
    }

    private long getMaxDurationForGroup(String group, String action) {
        String normalizedGroup = normalizeGroup(group);
        switch (action.toLowerCase(Locale.ROOT)) {
            case "ban":
                return MAX_BAN_TIME.getOrDefault(normalizedGroup, 0L);
            case "mute":
                return MAX_MUTE_TIME.getOrDefault(normalizedGroup, 0L);
            case "kick":
                return MAX_KICK_TIME.getOrDefault(normalizedGroup, 0L);
            default:
                return 0L;
        }
    }

    private static String millisToDuration(long millis) {
        if (millis == Long.MAX_VALUE) {
            return "навсегда";
        }
        long seconds = millis / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h";
        }
        long days = hours / 24;
        if (days < 7) {
            return days + "d";
        }
        return "7d";
    }

    private static long parseDurationToMillis(String time) {
        if (time == null || time.equalsIgnoreCase("навсегда")) {
            return Long.MAX_VALUE;
        }
        String trimmed = time.trim();
        if (trimmed.isEmpty()) {
            return Long.MAX_VALUE;
        }
        char unit = Character.toLowerCase(trimmed.charAt(trimmed.length() - 1));
        long value;
        try {
            value = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }

        switch (unit) {
            case 's':
                return value * 1000L;
            case 'm':
                return value * 60L * 1000L;
            case 'h':
                return value * 60L * 60L * 1000L;
            case 'd':
                return value * 24L * 60L * 60L * 1000L;
            case 'w':
                return value * 7L * 24L * 60L * 60L * 1000L;
            case 'y':
                return value * 365L * 24L * 60L * 60L * 1000L;
            default:
                return Long.MAX_VALUE;
        }
    }

    private boolean shouldAppendHiddenFlag(CommandSender sender, boolean hidden) {
        if (!hidden) {
            return false;
        }
        return canUseHiddenFlag(sender);
    }

    private String safeReason(String baseReason) {
        return baseReason == null || baseReason.trim().isEmpty() ? "Без причины" : baseReason;
    }

    private boolean handleBan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telegramconsolebot.ban")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /ban <ник> [время] <причина> [-s]");
            return true;
        }

        boolean hidden = false;
        String lastArg = args[args.length - 1];
        if (lastArg.equalsIgnoreCase("-s")) {
            hidden = true;
            args = Arrays.copyOf(args, args.length - 1);
        }

        String playerName = args[0];
        String duration = "навсегда";
        String reason;
        int start = 1;

        if (args.length > 1 && punishmentManager.isValidTime(args[1]) && !args[1].equalsIgnoreCase("-s")) {
            duration = args[1];
            start = 2;
        }

        if (args.length > start) {
            reason = String.join(" ", Arrays.copyOfRange(args, start, args.length));
        } else {
            reason = "Без причины";
        }

        if (!canPunishTarget(sender, playerName, "ban")) {
            return true;
        }

        duration = clampDurationByGroup(sender, "ban", duration);

        if (hidden && !canUseHiddenFlag(sender)) {
            reason = reason + " -s";
            hidden = false;
            sender.sendMessage("§c§l-s доступен только для staff и выше! Флаг добавлен в причину.");
        }

        String issuer = sender instanceof Player ? ((Player) sender).getName() : "Console";

        boolean success = punishmentManager.banPlayer(playerName, issuer, reason, duration, hidden, !hidden);
        if (success) {
            sender.sendMessage("§c§lВы успешно забанили игрока!");
            commandLogger.logCommand(sender.getName(), "ban " + playerName + " " + duration + " " + reason + (hidden ? " -s" : ""));
        } else {
            sender.sendMessage("§c" + playerName + " уже забанен!");
        }
        return true;
    }

    private boolean handleBanUuid(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telegramconsolebot.ban")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /banuuid <uuid> [время] <причина> [-s]");
            return true;
        }

        boolean hidden = false;
        String lastArg = args[args.length - 1];
        if (lastArg.equalsIgnoreCase("-s")) {
            hidden = true;
            args = Arrays.copyOf(args, args.length - 1);
        }

        String uuidStr = args[0];
        String duration = "навсегда";
        String reason;
        int start = 1;

        try {
            UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cНеверный формат UUID!");
            return true;
        }

        if (args.length > 1 && punishmentManager.isValidTime(args[1]) && !args[1].equalsIgnoreCase("-s")) {
            duration = args[1];
            start = 2;
        }

        if (args.length > start) {
            reason = String.join(" ", Arrays.copyOfRange(args, start, args.length));
        } else {
            reason = "Без причины";
        }

        String playerName = uuidStr;
        if (!canPunishTarget(sender, playerName, "ban")) {
            return true;
        }

        duration = clampDurationByGroup(sender, "ban", duration);

        if (hidden && !canUseHiddenFlag(sender)) {
            reason = reason + " -s";
            hidden = false;
            sender.sendMessage("§c§l-s доступен только для staff и выше! Флаг добавлен в причину.");
        }

        String issuer = sender instanceof Player ? ((Player) sender).getName() : "Console";

        boolean success = punishmentManager.banUuid(uuidStr, issuer, reason, duration, hidden, !hidden);
        if (success) {
            sender.sendMessage("§c§lВы успешно забанили игрока!");
            commandLogger.logCommand(sender.getName(), "banuuid " + uuidStr + " " + duration + " " + reason + (hidden ? " -s" : ""));
        } else {
            sender.sendMessage("§cИгрок с UUID " + uuidStr + " уже забанен или не найден!");
        }
        return true;
    }

    private boolean handleMute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telegramconsolebot.mute")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /mute <ник> [время] <причина> [-s]");
            return true;
        }

        boolean hidden = false;
        String lastArg = args[args.length - 1];
        if (lastArg.equalsIgnoreCase("-s")) {
            hidden = true;
            args = Arrays.copyOf(args, args.length - 1);
        }

        String playerName = args[0];
        String duration = "навсегда";
        String reason;
        int start = 1;

        if (args.length > 1 && punishmentManager.isValidTime(args[1]) && !args[1].equalsIgnoreCase("-s")) {
            duration = args[1];
            start = 2;
        }

        if (args.length > start) {
            reason = String.join(" ", Arrays.copyOfRange(args, start, args.length));
        } else {
            reason = "Без причины";
        }

        if (!canPunishTarget(sender, playerName, "mute")) {
            return true;
        }

        duration = clampDurationByGroup(sender, "mute", duration);

        if (hidden && !canUseHiddenFlag(sender)) {
            reason = reason + " -s";
            hidden = false;
            sender.sendMessage("§c§l-s доступен только для staff и выше! Флаг добавлен в причину.");
        }

        String issuer = sender instanceof Player ? ((Player) sender).getName() : "Console";

        boolean success = punishmentManager.mutePlayer(playerName, issuer, reason, duration, hidden, !hidden);
        if (success) {
            sender.sendMessage("§c§lВы успешно замутили игрока!");
            commandLogger.logCommand(sender.getName(), "mute " + playerName + " " + duration + " " + reason + (hidden ? " -s" : ""));
        } else {
            sender.sendMessage("§c" + playerName + " уже замучен!");
        }
        return true;
    }

    private boolean handleUnmute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telegramconsolebot.unmute")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /unmute <ник> <причина>");
            return true;
        }

        String playerName = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String issuer = sender instanceof Player ? ((Player) sender).getName() : "Console";

        boolean success = punishmentManager.unmutePlayer(playerName, issuer, reason, true);
        if (success) {
            sender.sendMessage("§aИгрок " + playerName + " размучен по причине: " + reason);
            commandLogger.logCommand(sender.getName(), "unmute " + playerName + " " + reason);
        } else {
            sender.sendMessage("§cИгрок " + playerName + " не замучен!");
        }
        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telegramconsolebot.unban")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /unban <ник> <причина>");
            return true;
        }

        String playerName = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String issuer = sender instanceof Player ? ((Player) sender).getName() : "Console";

        boolean success = punishmentManager.unbanPlayer(playerName, issuer, reason, true);
        if (success) {
            sender.sendMessage("§aИгрок " + playerName + " разбанен по причине: " + reason);
            commandLogger.logCommand(sender.getName(), "unban " + playerName + " " + reason);
        } else {
            sender.sendMessage("§cИгрок " + playerName + " не забанен!");
        }
        return true;
    }

    private boolean handleUnbanIp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telegramconsolebot.unbanip")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /unbanip <ник> <причина>");
            return true;
        }

        String playerName = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String issuer = sender instanceof Player ? ((Player) sender).getName() : "Console";

        boolean success = punishmentManager.unbanIp(playerName, issuer, reason);
        if (success) {
            sender.sendMessage("§aIP игрока " + playerName + " разбанен по причине: " + reason);
            commandLogger.logCommand(sender.getName(), "unbanip " + playerName + " " + reason);
        } else {
            sender.sendMessage("§cIP игрока " + playerName + " не забанен!");
        }
        return true;
    }

    private boolean handleKick(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telegramconsolebot.kick")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /kick <ник> <причина> [-s]");
            return true;
        }

        boolean hidden = false;
        String lastArg = args[args.length - 1];
        if (lastArg.equalsIgnoreCase("-s")) {
            hidden = true;
            args = Arrays.copyOf(args, args.length - 1);
        }

        String playerName = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (!canPunishTarget(sender, playerName, "kick")) {
            return true;
        }

        if (hidden && !canUseHiddenFlag(sender)) {
            reason = reason + " -s";
            hidden = false;
            sender.sendMessage("§c§l-s доступен только для staff и выше! Флаг добавлен в причину.");
        }

        String issuer = sender instanceof Player ? ((Player) sender).getName() : "Console";

        boolean success = punishmentManager.kickPlayer(playerName, issuer, reason, hidden, !hidden);
        if (success) {
            sender.sendMessage("§c§lВы успешно кикнули игрока!");
            commandLogger.logCommand(sender.getName(), "kick " + playerName + " " + reason + (hidden ? " -s" : ""));
        } else {
            sender.sendMessage("§c" + playerName + " не найден!");
        }
        return true;
    }

    private boolean handleWarn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telegramconsolebot.warn")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /warn <ник> <причина> [-s]");
            return true;
        }

        boolean hidden = false;
        String lastArg = args[args.length - 1];
        if (lastArg.equalsIgnoreCase("-s")) {
            hidden = true;
            args = Arrays.copyOf(args, args.length - 1);
        }

        String playerName = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (!canPunishTarget(sender, playerName, "warn")) {
            return true;
        }

        if (hidden && !canUseHiddenFlag(sender)) {
            reason = reason + " -s";
            hidden = false;
            sender.sendMessage("§c§l-s доступен только для staff и выше! Флаг добавлен в причину.");
        }

        String issuer = sender instanceof Player ? ((Player) sender).getName() : "Console";

        boolean success = punishmentManager.warnPlayer(playerName, issuer, reason, hidden, !hidden);
        if (success) {
            sender.sendMessage("§c§lВы успешно выдали предупреждение игроку!");
            commandLogger.logCommand(sender.getName(), "warn " + playerName + " " + reason + (hidden ? " -s" : ""));
        } else {
            sender.sendMessage("§cНе удалось выдать предупреждение!");
        }
        return true;
    }

    private boolean handleUnwarn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telegramconsolebot.warn")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /unwarn <ник> <причина>");
            return true;
        }

        String playerName = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String issuer = sender instanceof Player ? ((Player) sender).getName() : "Console";

        boolean success = punishmentManager.unwarnPlayer(playerName, issuer, reason, true);
        if (success) {
            sender.sendMessage("§aУ игрока " + playerName + " снято предупреждение по причине: " + reason);
            commandLogger.logCommand(sender.getName(), "unwarn " + playerName + " " + reason);
        } else {
            sender.sendMessage("§cУ игрока " + playerName + " нет предупреждений!");
        }
        return true;
    }

    private boolean handleBroadcast(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telegramconsolebot.bc")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cИспользование: /bc <сообщение>");
            return true;
        }

        String message = String.join(" ", args);
        String senderName = sender instanceof Player ? ((Player) sender).getName() : "Console";

        String chatMessage = "§e[Объявление] §f" + message + " §7(пишет: " + senderName + "§7)";
        Bukkit.broadcastMessage(chatMessage);
        commandLogger.logCommand(sender.getName(), "bc " + message);
        return true;
    }

    private boolean handleLogs(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telegramconsolebot.logs")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cИспользование: /logs <ник> [кол-во]");
            return true;
        }

        String playerName = args[0];
        int limit = 10;
        if (args.length >= 2) {
            try {
                limit = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        String response = commandLogger.getFormattedLogs(playerName, limit);
        sender.sendMessage(response.replace("[БОТ] Ответ сервера:\n", ""));
        commandLogger.logCommand(sender.getName(), "logs " + playerName);
        return true;
    }

    private boolean handleDupeip(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telegramconsolebot.dupeip")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cИспользование: /dupeip <ник>");
            return true;
        }

        String playerName = args[0];
        String targetIp = playerManager.getPlayerIp(playerName);

        if (targetIp == null || targetIp.equals("—") || targetIp.equals("0.0.0.0")) {
            sender.sendMessage("§cНе удалось определить IP игрока " + playerName);
            return true;
        }

        List<String> playersWithSameIp = playerManager.getPlayersByIp(targetIp);
        playersWithSameIp.remove(playerName);

        StringBuilder response = new StringBuilder();
        response.append("§6Сканируем по нику: ").append(playerName).append("\n");
        response.append("§7");

        if (playersWithSameIp.isEmpty()) {
            response.append("Нет других аккаунтов с этим IP");
        } else {
            response.append(String.join(", ", playersWithSameIp));
        }

        sender.sendMessage(response.toString());
        commandLogger.logCommand(sender.getName(), "dupeip " + playerName);
        return true;
    }

    private boolean handleSeen(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telegramconsolebot.seen")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cИспользование: /seen <ник>");
            return true;
        }

        String playerName = args[0];
        Player target = Bukkit.getPlayer(playerName);
        boolean isOnline = target != null && target.isOnline();

        if (sender instanceof Player) {
            Player senderPlayer = (Player) sender;
            try {
                Permission permission = Bukkit.getServicesManager().getRegistration(Permission.class).getProvider();
                if (permission != null) {
                    String senderGroup = permission.getPrimaryGroup(senderPlayer);
                    String targetGroup = null;

                    if (isOnline) {
                        targetGroup = permission.getPrimaryGroup(target);
                    } else {
                        targetGroup = "default";
                    }

                    if (senderGroup != null && targetGroup != null) {
                        int senderWeight = getGroupPriority(senderGroup);
                        int targetWeight = getGroupPriority(targetGroup);

                        if (senderWeight < targetWeight) {
                            sender.sendMessage("§cВы не можете узнать информацию об этом игроке!");
                            return true;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        String response = punishmentManager.getSeenInfo(playerName, isOnline);
        sender.sendMessage(response);
        commandLogger.logCommand(sender.getName(), "seen " + playerName);
        return true;
    }

    private boolean handlePex(CommandSender sender, String[] args) {
        if (!sender.hasPermission("telegramconsolebot.pex")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cИспользование: /pex user <ник> | /pex group");
            return true;
        }

        String subCmd = args[0].toLowerCase();

        if (subCmd.equals("user") && args.length >= 2) {
            return handlePexUser(sender, args);
        } else if (subCmd.equals("group")) {
            return handlePexGroup(sender);
        } else {
            sender.sendMessage("§cИспользование: /pex user <ник> | /pex group");
            return true;
        }
    }

    private boolean handlePexUser(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /pex user <ник>");
            return true;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);
        boolean isOnline = target != null && target.isOnline();

        String group = "default";
        String uuid = "—";

        try {
            if (isOnline) {
                uuid = target.getUniqueId().toString();
                Permission permission = Bukkit.getServicesManager().getRegistration(Permission.class).getProvider();
                if (permission != null) {
                    group = permission.getPrimaryGroup(target);
                }
            } else {
                group = "офлайн";
                uuid = "—";
            }
        } catch (Exception e) {
            sender.sendMessage("§cОшибка: " + e.getMessage());
            return true;
        }

        String response = "§6📋 Сканируем по нику §f" + playerName + "\n" +
                "\n" +
                "§6🆔 UUID: §f" + uuid + "\n" +
                "§6📌 Группа: §f" + group;

        sender.sendMessage(response);
        return true;
    }

    private boolean handlePexGroup(CommandSender sender) {
        try {
            Permission permission = Bukkit.getServicesManager().getRegistration(Permission.class).getProvider();
            if (permission == null) {
                sender.sendMessage("§cVault не найден!");
                return true;
            }

            Map<String, Integer> groupCounts = new HashMap<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                String group = permission.getPrimaryGroup(player);
                if (group != null) {
                    groupCounts.put(group, groupCounts.getOrDefault(group, 0) + 1);
                }
            }

            List<String> sortedGroups = Arrays.asList("owner", "admin", "mod", "helper", "builder", "default");

            StringBuilder response = new StringBuilder();
            response.append("§6Доступные группы:\n");
            response.append("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

            for (String group : sortedGroups) {
                int count = groupCounts.getOrDefault(group, 0);
                response.append("§f - ").append(group).append(" §7(").append(count).append(" игроков)\n");
            }

            response.append("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            response.append("§7Всего групп: §f").append(sortedGroups.size());

            sender.sendMessage(response.toString());

        } catch (Exception e) {
            sender.sendMessage("§cОшибка: " + e.getMessage());
        }
        return true;
    }
}
