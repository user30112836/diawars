package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import de.davidsw.diawars.util.MiniMessageHelper.pmm
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
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
            event.player.kick(mm("<red>Du bist kein Teammitglied!</red> <yellow>Melde dich bei einem Admin um einem Team beizutreten.</yellow>"))
            return
        }
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.diamondScoreboardManager.update()
        }, 5L)
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        plugin.afkManager.recordActivity(player.uniqueId)

        if (!plugin.teamManager.isPlayerInTeam(player.uniqueId)) {
            return
        }

        val from = event.from
        val to = event.to

        if (plugin.zoneManager.hasCrossedBoundary(from, to)) {
            if (!plugin.zoneManager.isInPlayerZone(player, event.to)) {
                player.sendMessage(pmm("<yellow>Du betrittst jetzt den generischen Bereich!</yellow>"))
                player.playSound(player, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f)
            } else {
                player.sendMessage(pmm("<green>Du bist wieder in deiner Zone!</green>"))
                player.playSound(player, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f)
            }
        }
    }

    @EventHandler
    fun onInteraction(event: PlayerInteractEvent) {
        val player = event.player
        plugin.afkManager.recordActivity(player.uniqueId)

        if (!plugin.teamManager.isPlayerInTeam(player.uniqueId) || plugin.zoneManager.isInOwnZone(player)) {
            return
        }

        if (isEnemyVaultSteal(player, event)) return

        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.ALLOW)
    }

    private fun isEnemyVaultSteal(player: Player, event: PlayerInteractEvent): Boolean {
        val block = event.clickedBlock ?: return false
        if (block.type != Material.DIAMOND_BLOCK) return false

        val vault = plugin.vaultManager.getVaultAt(block.location) ?: return false
        val playerTeam = plugin.teamManager.getPlayerTeam(player.uniqueId) ?: return false

        return vault.team != playerTeam
    }
}