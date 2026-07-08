package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.DiamondGlow
import de.davidsw.diawars.util.MiniMessageHelper.pmm
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RewardManager(private val plugin: Diawars) {
    private val store = plugin.store.rewardStore
    private var taskId = ConcurrentHashMap<UUID, Int>()

    fun startPlaytimeReward(player: Player) {
        if (taskId[player.uniqueId] != null) return
        if (!plugin.config.getBoolean("playtime-reward.enabled", false)) return
        if (!plugin.teamManager.isPlayerInTeam(player.uniqueId)) return

        val intervalTicks = plugin.config.getInt("playtime-reward.interval-minutes", 30) * 60 * 20L
        taskId[player.uniqueId] = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val amount = plugin.config.getInt("playtime-reward.amount", 1)
            grantDiamondReward(player, amount)
        }, intervalTicks, intervalTicks).taskId
    }

    fun stopPlaytimeReward(player: Player) {
        if (taskId[player.uniqueId] != null) {
            plugin.server.scheduler.cancelTask(taskId[player.uniqueId]!!)
            taskId.remove(player.uniqueId)
        }
    }

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
            player.sendMessage(pmm("<green>Du hast <gold>$given Diamant(en)</gold> erhalten!</green>"))
        }
        if (leftover > 0) {
            store.addPending(player.uniqueId, leftover)
            player.sendMessage(pmm("<yellow>Dein Inventar war voll - <gold>$leftover Diamant(en)</gold> werden dir nachgeliefert, sobald Platz frei ist.</yellow>"))
        }
    }
}