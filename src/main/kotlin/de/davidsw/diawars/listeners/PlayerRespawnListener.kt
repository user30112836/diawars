package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRespawnEvent

class PlayerRespawnListener(private val plugin: Diawars): Listener {
    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val deathWorld = player.world
        val spawnStore = plugin.store.playerSpawnStore

        if (plugin.lobbyManager.isLobbyWorld(deathWorld.name) || plugin.eventManager.isEventWorld(deathWorld.name)) {
            val worldBed = spawnStore.getWorldSpawn(deathWorld.name, player.uniqueId)
            event.respawnLocation = worldBed ?: deathWorld.spawnLocation
            return
        }

        val mainBed = spawnStore.getMainSpawn(player.uniqueId)
        if (mainBed != null) {
            event.respawnLocation = mainBed
            return
        }

        val team = plugin.teamManager.getPlayerTeam(player.uniqueId) ?: return
        val location = plugin.teamManager.getSpawnLocation(team) ?: return

        event.respawnLocation = location
    }
}