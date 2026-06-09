package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.Sound
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent

class PlayerEventListener(private val plugin: Diawars): Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (!plugin.teamManager.isPlayerInTeam(event.player.uniqueId) && !event.player.isOp) {
            event.player.kickPlayer("Du bist kein Teammitglied!")
            return
        }
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.diamondScoreboardManager.update()
        }, 5L)
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player

        if (!plugin.teamManager.isPlayerInTeam(player.uniqueId)) {
            return
        }

        val from = event.from
        val to = event.to

        if (plugin.zoneManager.hasCrossedBoundary(from, to)) {
            if (!plugin.zoneManager.isInPlayerZone(player, event.to)) {
                plugin.messageManager.sendEnterGenericArea(player)
                player.playSound(player, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f)
            } else {
                plugin.messageManager.sendReturnToZone(player)
                player.playSound(player, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f)
            }
        }
    }

    @EventHandler
    fun onInteraction(event: PlayerInteractEvent) {
        val player = event.player

        if (!plugin.teamManager.isPlayerInTeam(player.uniqueId) || plugin.zoneManager.isInOwnZone(player)) {
            return
        }

        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.ALLOW)
    }
}