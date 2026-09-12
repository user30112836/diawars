package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.DiamondAction
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent

class VaultListener(private val plugin: Diawars): Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (event.block.type != Material.DIAMOND_BLOCK) return
        val player = event.player

        val vault = plugin.vaultManager.isValidPlacementSpot(event.block.location)
        if (vault == null) {
            event.isCancelled = true
            player.sendMessage(mm("<red>Du kannst Diamantblöcke nur in einem Vault platzieren!</red>"))
            return
        }

        if (!plugin.store.vaultClaimStore.canPlace(vault.id, player.uniqueId)) {
            event.isCancelled = true
            if (!plugin.store.vaultClaimStore.isVaultClaimed(vault.id)) {
                player.sendMessage(mm("<red>Dieses Vault wurde noch nicht beansprucht! Nutze <yellow>/vault claim</yellow>.</red>"))
            } else {
                player.sendMessage(mm("<red>Du wurdest nicht zu diesem Vault eingeladen!</red>"))
            }
            return
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onVaultBlockPlace(event: BlockPlaceEvent) {
        if (event.block.type != Material.DIAMOND_BLOCK) return
        val vault = plugin.vaultManager.isValidPlacementSpot(event.block.location) ?: return
        if (!plugin.store.vaultClaimStore.canPlace(vault.id, event.player.uniqueId)) return

        plugin.store.vaultDiamondStore.addDiamonds(vault.id, 9)
        plugin.diamondLogManager.log(DiamondAction.PLACE, Material.DIAMOND_BLOCK, 1, event.player, location = event.block.location)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.block.type != Material.DIAMOND_BLOCK) return
        val vault = plugin.vaultManager.getVaultAt(event.block.location) ?: return
        val player = event.player
        val playerTeam = plugin.teamManager.getPlayerTeam(player.uniqueId)

        if (playerTeam == vault.team && !plugin.store.vaultClaimStore.canPlace(vault.id, player.uniqueId)) {
            event.isCancelled = true
            player.sendMessage(mm("<red>Du darfst dieses Vault nicht bearbeiten!</red>"))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onVaultBlockBreak(event: BlockBreakEvent) {
        if (event.block.type != Material.DIAMOND_BLOCK) return
        val vault = plugin.vaultManager.getVaultAt(event.block.location) ?: return

        plugin.store.vaultDiamondStore.removeDiamonds(vault.id, 9)
        plugin.diamondLogManager.log(DiamondAction.BREAK, Material.DIAMOND_BLOCK, 1, event.player, location = event.block.location)
    }
}