package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class BugListener(private val plugin: Diawars): Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.bugManager.handleAdminJoin(event.player)
    }
}