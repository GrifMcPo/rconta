// ... внутри onUpdateReceived, после проверки на !rcon добавляем:

// --- КОМАНДЫ ДЛЯ ИГРОКОВ ---
if (messageText.startsWith("/register ")) {
    String code = messageText.substring(10).trim();
    if (playerManager.registerPlayer(code, String.valueOf(userId))) {
        sendMessage(chatId, "✅ Аккаунт успешно привязан!");
    } else {
        sendMessage(chatId, "❌ Неверный код или код уже использован.");
    }
    return;
}

if (messageText.equalsIgnoreCase("/kick my account")) {
    // Найти игрока по Telegram ID
    String playerName = playerManager.getPlayerNameByTelegram(String.valueOf(userId));
    if (playerName != null) {
        playerManager.kickAccount(playerName);
        sendMessage(chatId, "✅ Игрок " + playerName + " был кикнут.");
    } else {
        sendMessage(chatId, "❌ Вы не привязали аккаунт.");
    }
    return;
}

if (messageText.equalsIgnoreCase("/unreg")) {
    String playerName = playerManager.getPlayerNameByTelegram(String.valueOf(userId));
    if (playerName != null) {
        playerManager.unregister(playerName);
        sendMessage(chatId, "✅ Аккаунт " + playerName + " отвязан.");
    } else {
        sendMessage(chatId, "❌ Вы не привязали аккаунт.");
    }
    return;
}

// --- ОБРАБОТКА КНОПОК (для 2FA) ---
if (update.hasCallbackQuery()) {
    String data = update.getCallbackQuery().getData();
    String chatId = update.getCallbackQuery().getMessage().getChatId().toString();
    if (data.startsWith("auth_allow_")) {
        String playerName = data.substring(11);
        playerManager.refreshSession(playerName);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null && player.isOnline()) {
                player.sendMessage("§aВход разрешён через Telegram!");
            }
        });
        sendMessage(Long.parseLong(chatId), "✅ Вход для " + playerName + " разрешён.");
    } else if (data.startsWith("auth_deny_")) {
        String playerName = data.substring(10);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null && player.isOnline()) {
                player.kickPlayer("§cВход запрещён через Telegram!");
            }
        });
        sendMessage(Long.parseLong(chatId), "❌ Вход для " + playerName + " запрещён.");
    }
    return;
}
