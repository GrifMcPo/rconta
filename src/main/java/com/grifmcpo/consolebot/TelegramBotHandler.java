// --- !rcon ban ---
if (command.startsWith("ban ")) {
    String[] parts = command.split(" ");
    if (parts.length < 3) {
        sendMessage(chatId, "❌ Используй: !rcon ban <ник> [время] <причина>\n" +
                "Пример: !rcon ban pley1657 1d читы");
        return;
    }

    String playerName = parts[1];
    String duration = "навсегда";
    String reason = "";
    int startIndex = 2;

    if (parts.length > 2 && punishmentManager.isValidTime(parts[2])) {
        duration = parts[2];
        startIndex = 3;
    }

    if (parts.length > startIndex) {
        reason = String.join(" ", Arrays.copyOfRange(parts, startIndex, parts.length));
    } else {
        reason = "Без причины";
    }

    String issuer = plugin.getCustomSender(userId);
    if (issuer == null && userId == plugin.getOwnerId()) {
        issuer = "RCON@Grif_Mo";
    }

    if (punishmentManager.banPlayer(playerName, issuer, reason, duration)) {
        sendMessage(chatId, "✅ " + playerName + " забанен на " + duration);
        sendCustomMessage("ban " + playerName + " " + duration + " " + reason, issuer);
    } else {
        sendMessage(chatId, "❌ " + playerName + " уже забанен!");
    }
    return;
}

// --- !rcon unban ---
if (command.startsWith("unban ")) {
    String[] parts = command.split(" ");
    if (parts.length < 2) {
        sendMessage(chatId, "❌ Используй: !rcon unban <ник>");
        return;
    }
    String playerName = parts[1];
    String issuer = plugin.getCustomSender(userId);
    if (issuer == null && userId == plugin.getOwnerId()) {
        issuer = "RCON@Grif_Mo";
    }

    if (punishmentManager.unbanPlayer(playerName, issuer)) {
        sendMessage(chatId, "✅ " + playerName + " разбанен!");
    } else {
        sendMessage(chatId, "❌ " + playerName + " не забанен!");
    }
    return;
}

// --- !rcon kick ---
if (command.startsWith("kick ")) {
    String[] parts = command.split(" ");
    if (parts.length < 3) {
        sendMessage(chatId, "❌ Используй: !rcon kick <ник> <причина>");
        return;
    }
    String playerName = parts[1];
    String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));

    String issuer = plugin.getCustomSender(userId);
    if (issuer == null && userId == plugin.getOwnerId()) {
        issuer = "RCON@Grif_Mo";
    }

    if (punishmentManager.kickPlayer(playerName, issuer, reason)) {
        sendMessage(chatId, "✅ " + playerName + " кикнут!");
        sendCustomMessage("kick " + playerName + " " + reason, issuer);
    } else {
        sendMessage(chatId, "❌ " + playerName + " не найден на сервере!");
    }
    return;
}

// --- !rcon banlist ---
if (command.equalsIgnoreCase("banlist")) {
    List<String> bans = punishmentManager.getBanList();
    if (bans.isEmpty()) {
        sendMessage(chatId, "📋 Список банов:\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n✅ Банов нет.");
    } else {
        StringBuilder response = new StringBuilder();
        response.append("📋 Список банов (Всего: ").append(bans.size()).append(")\n");
        response.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        for (String entry : bans) {
            response.append(entry).append("\n");
        }
        sendMessage(chatId, response.toString());
    }
    return;
}
