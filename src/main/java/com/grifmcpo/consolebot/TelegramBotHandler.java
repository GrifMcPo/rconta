// ==== ПАГИНАЦИЯ ДЛЯ BANLIST ====
if (command.equalsIgnoreCase("banlist") || command.startsWith("banlist ")) {
    int page = 1;
    int pageSize = 10;
    String[] parts = command.split(" ");
    if (parts.length > 1) {
        try { page = Integer.parseInt(parts[1]); if (page < 1) page = 1; } catch (NumberFormatException e) {}
    }

    sendMessage(chatId, "[БОТ] Выполняю команду..");

    List<String> allBans = punishmentManager.getBanList(1, Integer.MAX_VALUE);
    List<String> bans = punishmentManager.getBanList(page, pageSize);
    int totalPages = punishmentManager.getTotalPages(allBans.size(), pageSize);

    if (bans.isEmpty()) {
        sendFormattedResponse(chatId, "📋 Список банов пуст.");
    } else {
        StringBuilder response = new StringBuilder();
        response.append("📋 Список банов (Страница ").append(page).append("/").append(totalPages).append(")\n");
        for (String entry : bans) {
            response.append(entry).append("\n");
        }
        if (totalPages > 1) {
            response.append("\n📌 Используй: !rcon banlist ").append(page + 1).append(" для следующей страницы");
        }
        sendFormattedResponse(chatId, response.toString());
    }
    return;
}

// ==== ПАГИНАЦИЯ ДЛЯ MUTELIST ====
if (command.equalsIgnoreCase("mutelist") || command.startsWith("mutelist ")) {
    int page = 1;
    int pageSize = 10;
    String[] parts = command.split(" ");
    if (parts.length > 1) {
        try { page = Integer.parseInt(parts[1]); if (page < 1) page = 1; } catch (NumberFormatException e) {}
    }

    sendMessage(chatId, "[БОТ] Выполняю команду..");

    List<String> allMutes = punishmentManager.getMuteList(1, Integer.MAX_VALUE);
    List<String> mutes = punishmentManager.getMuteList(page, pageSize);
    int totalPages = punishmentManager.getTotalPages(allMutes.size(), pageSize);

    if (mutes.isEmpty()) {
        sendFormattedResponse(chatId, "📋 Список мутов пуст.");
    } else {
        StringBuilder response = new StringBuilder();
        response.append("📋 Список мутов (Страница ").append(page).append("/").append(totalPages).append(")\n");
        for (String entry : mutes) {
            response.append(entry).append("\n");
        }
        if (totalPages > 1) {
            response.append("\n📌 Используй: !rcon mutelist ").append(page + 1).append(" для следующей страницы");
        }
        sendFormattedResponse(chatId, response.toString());
    }
    return;
}

// ==== ПАГИНАЦИЯ ДЛЯ SHIST ====
if (command.startsWith("shist ") || command.startsWith("hist ")) {
    String[] parts = command.split(" ");
    if (parts.length < 2) {
        sendMessage(chatId, "❌ Используй: !rcon shist <ник> [страница]");
        return;
    }
    String playerName = parts[1];
    int page = 1;
    int pageSize = 10;
    if (parts.length > 2) {
        try { page = Integer.parseInt(parts[2]); if (page < 1) page = 1; } catch (NumberFormatException e) {}
    }

    sendMessage(chatId, "[БОТ] Выполняю команду..");

    List<com.grifmcpo.consolebot.PunishmentManager.HistoryEntry> allHistory = punishmentManager.getHistory(playerName);
    List<String> formattedHistory = new ArrayList<>();
    for (com.grifmcpo.consolebot.PunishmentManager.HistoryEntry entry : allHistory) {
        String timeAgo = punishmentManager.getTimeAgo(entry.timestamp);
        String formattedDate = punishmentManager.formatDate(entry.timestamp);
        String status = "❓";
        if (entry.type.equals("ban")) status = punishmentManager.isBanned(playerName) ? "[Активен]" : "[Истек]";
        else if (entry.type.equals("mute")) status = punishmentManager.isMuted(playerName) ? "[Активен]" : "[Истек]";
        else status = "[Истек]";
        formattedHistory.add(" - " + timeAgo + " -\n   " + playerName + " был " + entry.getActionName() +
                " на " + entry.duration + " " +
                entry.issuer + ": " + entry.reason + " " + status);
    }

    List<String> pageItems = paginate(formattedHistory, page, pageSize);
    int totalPages = (int) Math.ceil((double) formattedHistory.size() / pageSize);

    if (pageItems.isEmpty()) {
        sendFormattedResponse(chatId, "📋 История наказаний для " + playerName + " пуста.");
    } else {
        StringBuilder response = new StringBuilder();
        response.append("📋 История наказаний для ").append(playerName).append(" (Страница ").append(page).append("/").append(totalPages).append(")\n");
        response.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        for (String entry : pageItems) {
            response.append(entry).append("\n");
        }
        response.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        response.append("📊 Всего записей: ").append(formattedHistory.size());
        if (totalPages > 1) {
            response.append("\n📌 Используй: !rcon shist ").append(playerName).append("
