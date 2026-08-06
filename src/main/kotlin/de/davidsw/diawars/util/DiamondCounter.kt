package de.davidsw.diawars.util

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object DiamondCounter {
    fun countForPlayer(player: Player): Int {
        return player.inventory.contents.toList().sumOf { item -> if (item != null) countInItem(item) else 0 }
    }

    fun countInItem(item: ItemStack): Int = when (item.type) {
        Material.DIAMOND -> item.amount
        Material.DIAMOND_BLOCK -> item.amount * 9
        else -> 0
    }
}