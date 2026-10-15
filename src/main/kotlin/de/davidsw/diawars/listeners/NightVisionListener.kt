package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent

class NightVisionListener(private val plugin: Diawars) : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.nightVisionManager.reapply(event.player)
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline) plugin.nightVisionManager.reapply(player)
        }, 1L)
    }
}
