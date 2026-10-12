package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class RewardListener(private val plugin: Diawars): Listener {
    val manager = plugin.rewardManager

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        manager.checkPendingPlayer(event.player)
        manager.startPlaytimeReward(event.player)
    }

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        // Pending diamonds accumulate while inside event worlds (grantDiamondReward
        // defers to the pending balance there). Pay them out as soon as the player
        // is back in a non-event world (main world or lobby) instead of waiting
        // for the next server rejoin.
        if (plugin.eventManager.isEventWorld(event.player.world.name)) return
        manager.checkPendingPlayer(event.player)
    }

    @EventHandler
    fun onPlayerItemDrop(event: PlayerDropItemEvent) {
        manager.checkPendingPlayer(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        manager.stopPlaytimeReward(event.player)
    }
}