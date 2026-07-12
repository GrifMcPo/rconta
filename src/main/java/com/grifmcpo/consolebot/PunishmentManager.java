// --- !rcon unban ---
if (command.startsWith("unban ")) {
    String[] parts = command.split(" ");
    if (parts.length < 3) {
        sendMessage(chatId, "❌ Используй: !rcon unban <ник> <причина>");
        return;
    }
    String playerName = parts[1];
    String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
    String issuer = plugin.getCustomSender(userId);
    if (issuer == null && userId == plugin.getOwnerId()) {
        issuer = "RCON@Grif_Mo";
    }

    if (punishmentManager.unbanPlayer(playerName, issuer, reason)) {
        sendMessage(chatId, "✅ " + playerName + " разбанен! Причина: " + reason);
    } else {
        sendMessage(chatId, "❌ " + playerName + " не забанен!");
    }
    return;
}

// --- !rcon unmute ---
if (command.startsWith("unmute ")) {
    String[] parts = command.split(" ");
    if (parts.length < 3) {
        sendMessage(chatId, "❌ Используй: !rcon unmute <ник> <причина>");
        return;
    }
    String playerName = parts[1];
    String reason = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
    String issuer = plugin.getCustomSender(userId);
    if (issuer == null && userId == plugin.getOwnerId()) {
        issuer = "RCON@Grif_Mo";
    }

    if (punishmentManager.unmutePlayer(playerName, issuer, reason)) {
        sendMessage(chatId, "✅ " + playerName + " размучен! Причина: " + reason);
    } else {
        sendMessage(chatId, "❌ " + playerName + " не замучен!");
    }
    return;
}
