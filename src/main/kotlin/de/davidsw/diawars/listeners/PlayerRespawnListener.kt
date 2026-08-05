package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRespawnEvent

class PlayerRespawnListener(private val plugin: Diawars): Listener {
    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        if (event.isBedSpawn || event.isAnchorSpawn) return

        val player = event.player
        val world = player.world

        if (plugin.lobbyManager.isLobbyWorld(world.name)) return
        if (plugin.eventManager.isEventWorld(world.name)) {
            event.respawnLocation = world.spawnLocation
            return
        }

        val team = plugin.teamManager.getPlayerTeam(player.uniqueId) ?: return
        val location = plugin.teamManager.getSpawnLocation(team) ?: return

        event.respawnLocation = location
    }
}