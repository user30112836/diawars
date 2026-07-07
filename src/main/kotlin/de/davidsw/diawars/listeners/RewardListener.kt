package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent

class RewardListener(plugin: Diawars): Listener {
    val manager = plugin.rewardManager

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        manager.checkPendingPlayer(event.player)
    }

    @EventHandler
    fun onPlayerItemDrop(event: PlayerDropItemEvent) {
        manager.checkPendingPlayer(event.player)
    }
}