package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRespawnEvent

class PlayerRespawnListener(private val plugin: Diawars): Listener {
    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        if (event.isBedSpawn || event.isAnchorSpawn) return

        val player = event.player
        if (plugin.lobbyManager.isLobbyWorld(player.world.name)) return
        if (plugin.eventManager.isEventWorld(player.world.name)) return

        val team = plugin.teamManager.getPlayerTeam(player.uniqueId) ?: return
        val location = plugin.teamManager.getSpawnLocation(team) ?: return

        event.respawnLocation = location
    }
}