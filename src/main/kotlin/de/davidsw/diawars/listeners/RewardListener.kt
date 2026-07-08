package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class RewardListener(plugin: Diawars): Listener {
    val manager = plugin.rewardManager

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        manager.checkPendingPlayer(event.player)
        manager.startPlaytimeReward(event.player)
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