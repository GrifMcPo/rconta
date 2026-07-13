package com.grifmcpo.consolebot;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ReportListener implements Listener {

    public ReportListener() {
        // Пустой конструктор
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Можно добавить логику при входе игрока
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Можно добавить логику при выходе игрока
    }
}
