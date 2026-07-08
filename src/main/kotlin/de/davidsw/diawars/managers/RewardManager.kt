package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.DiamondGlow
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class RewardManager(private val plugin: Diawars) {
    val store = plugin.store.rewardStore
    
    fun checkPendingPlayer(player: Player) {
        val pendingReward = store.getPending(player.uniqueId)
        if (pendingReward > 0) {
            store.clearPending(player.uniqueId)
            grantDiamondReward(player, pendingReward)
        }
    }

    fun grantDiamondReward(player: Player, amount: Int) {
        if (player.isOnline && !plugin.eventManager.isEventWorld(player.world.name)) {
            giveDiamonds(player, amount)
        } else {
            store.addPending(player.uniqueId, amount)
        }
    }

    private fun giveDiamonds(player: Player, amount: Int) {
        if (amount <= 0) return
        val glowingStack = DiamondGlow.applyGlow(ItemStack(Material.DIAMOND, amount))
        val leftover = player.inventory.addItem(glowingStack).values.sumOf { it.amount }
        val given = amount - leftover
        if (given > 0) {
            player.sendMessage(mm("<green>Du hast <gold>$given Diamant(en)</gold> erhalten!</green>"))
        }
        if (leftover > 0) {
            store.addPending(player.uniqueId, leftover)
            player.sendMessage(mm("<yellow>Dein Inventar war voll - <gold>$leftover Diamant(en)</gold> werden dir nachgeliefert, sobald Platz frei ist.</yellow>"))
        }
    }
}