package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit.getPlayer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

class RewardManager(private val plugin: Diawars) {
    fun handlePlayerJoin(player: Player) {
        val pendingReward = plugin.store.rewardStore.getPending(player.uniqueId)
        if (pendingReward > 0) {
            plugin.store.rewardStore.clearPending(player.uniqueId)
            giveDiamonds(player, pendingReward)
        }
    }

    fun grantDiamondReward(playerId: UUID, amount: Int) {
        val player = getPlayer(playerId)
        if (player != null && player.isOnline) {
            giveDiamonds(player, amount)
        } else {
            plugin.store.rewardStore.addPending(playerId, amount)
        }
    }

    private fun giveDiamonds(player: Player, amount: Int) {
        if (amount <= 0) return
        val leftover = player.inventory.addItem(ItemStack(Material.DIAMOND, amount)).values.sumOf { it.amount }
        val given = amount - leftover
        if (given > 0) {
            player.sendMessage(mm("<green>Du hast <gold>$given Diamant(en)</gold> erhalten!</green>"))
        }
        if (leftover > 0) {
            plugin.store.rewardStore.addPending(player.uniqueId, leftover)
            player.sendMessage(mm("<yellow>Dein Inventar war voll - <gold>$leftover Diamant(en)</gold> werden dir nachgeliefert, sobald Platz frei ist.</yellow>"))
        }
    }
}