package de.davidsw.diawars.util

import org.bukkit.inventory.ItemStack

object DiamondGlow {
    fun applyGlow(item: ItemStack): ItemStack {
        val meta = item.itemMeta ?: return item
        meta.setEnchantmentGlintOverride(true)
        item.itemMeta = meta
        return item
    }
}