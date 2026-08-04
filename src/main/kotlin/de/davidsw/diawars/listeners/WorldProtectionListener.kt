package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.DiamondAction
import de.davidsw.diawars.util.MaterialSets
import org.bukkit.Material
import org.bukkit.entity.Enderman
import org.bukkit.entity.Wither
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.world.LootGenerateEvent

class WorldProtectionListener(private val plugin: Diawars): Listener {
    @EventHandler(priority = EventPriority.HIGH)
    fun onLootGenerate(event: LootGenerateEvent) {
        event.loot.removeIf { it.type == Material.DIAMOND }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockDrop(event: BlockDropItemEvent) {
        if (event.blockState.type !in MaterialSets.DIAMOND_ORES) return
        if (plugin.vaultManager.getVaultAt(event.block.location) != null) return
        event.items.clear()
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList()
            .filter { it.type == Material.DIAMOND_BLOCK }
            .forEach { block ->
                plugin.diamondLogManager.log(
                    DiamondAction.EXPLODE, Material.DIAMOND_BLOCK, 1,
                    event.entity, location = block.location,
                )
            }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList()
            .filter { it.type == Material.DIAMOND_BLOCK }
            .forEach { block ->
                plugin.diamondLogManager.log(
                    DiamondAction.EXPLODE, Material.DIAMOND_BLOCK, 1,
                    playerId = null, playerName = "TNT",
                    location = block.location,
                )
            }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (event.block.type != Material.DIAMOND_BLOCK) return
        val detail = when (event.entity) {
            is Wither -> "Wither hat den Block zerstört"
            is Enderman -> "Enderman hat den Block aufgenommen"
            else -> "Block durch Entity verändert"
        }
        plugin.diamondLogManager.log(
            DiamondAction.BREAK, Material.DIAMOND_BLOCK, 1,
            event.entity, location = event.block.location, details = detail,
        )
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (event.blocks.any { it.type == Material.DIAMOND_BLOCK }) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (event.blocks.any { it.type == Material.DIAMOND_BLOCK }) {
            event.isCancelled = true
        }
    }
}