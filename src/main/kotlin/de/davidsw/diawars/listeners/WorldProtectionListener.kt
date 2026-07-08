package de.davidsw.diawars.listeners

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.world.LootGenerateEvent

class WorldProtectionListener: Listener {
    private val diamondOreMaterials = setOf(
        Material.DIAMOND_ORE,
        Material.DEEPSLATE_DIAMOND_ORE,
    )

    private val diamondLootMaterials = setOf(
        Material.DIAMOND,
        Material.DIAMOND_BLOCK,
        Material.DIAMOND_ORE,
        Material.DEEPSLATE_DIAMOND_ORE,
    )

    @EventHandler(priority = EventPriority.HIGH)
    fun onLootGenerate(event: LootGenerateEvent) {
        event.loot.removeIf { it.type in diamondLootMaterials }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockDrop(event: BlockDropItemEvent) {
        if (event.blockState.type !in diamondOreMaterials) return
        event.items.clear()
    }
}