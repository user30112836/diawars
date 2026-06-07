package de.davidsw.diawars.managers

import de.davidsw.diawars.util.DiamondMaterials
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta

object DiamondCounter {
    fun countForPlayer(player: Player): Int {
        return listOf(
            player.inventory.contents.toList(),
            player.enderChest.contents.toList(),
        ).sumOf { items -> items.sumOf { item -> if (item != null) countInItem(item) else 0 } }
    }

    fun countForPlayers(players: Collection<Player>): Int = players.sumOf { countForPlayer(it) }

    fun countForTeam(team: Team, onlinePlayers: Collection<Player>, teamManager: TeamManager): Int =
        countForPlayers(onlinePlayers.filter { teamManager.getPlayerTeam(it.uniqueId) == team })

    private fun countInItem(item: ItemStack): Int = when (item.type) {
        Material.DIAMOND -> item.amount
        Material.DIAMOND_BLOCK -> item.amount * 9
        in DiamondMaterials.SHULKER_BOXES -> {
            val shulker = (item.itemMeta as? BlockStateMeta)?.blockState as? ShulkerBox ?: return 0
            shulker.inventory.contents.sumOf { s -> if (s != null) countInItem(s) else 0 }
        }
        else -> 0
    }
}