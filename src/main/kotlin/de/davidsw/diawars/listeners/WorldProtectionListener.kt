package de.davidsw.diawars.listeners

import de.davidsw.diawars.util.MaterialSets
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.world.LootGenerateEvent

class WorldProtectionListener: Listener {
    @EventHandler(priority = EventPriority.HIGH)
    fun onLootGenerate(event: LootGenerateEvent) {
        event.loot.removeIf { it.type == Material.DIAMOND }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockDrop(event: BlockDropItemEvent) {
        if (event.blockState.type !in MaterialSets.DIAMOND_ORES) return
        event.items.clear()
    }
}