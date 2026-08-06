package de.davidsw.diawars.listeners

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent
import de.davidsw.diawars.Diawars
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class PlayerSpawnChangeListener(private val plugin: Diawars): Listener {
    @EventHandler
    fun onSpawnChange(event: PlayerSetSpawnEvent) {
        val newSpawn = event.location ?: return
        val worldName = newSpawn.world?.name ?: return
        val playerId = event.player.uniqueId

        if (plugin.lobbyManager.isLobbyWorld(worldName) || plugin.eventManager.isEventWorld(worldName)) {
            plugin.store.playerSpawnStore.setWorldSpawn(worldName, playerId, newSpawn)
        } else {
            plugin.store.playerSpawnStore.setMainSpawn(playerId, newSpawn)
        }
    }
}