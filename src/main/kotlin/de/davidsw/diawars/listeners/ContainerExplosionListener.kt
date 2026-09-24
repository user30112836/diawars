package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.Material
import org.bukkit.block.DoubleChest
import org.bukkit.entity.Entity
import org.bukkit.entity.HumanEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.BlockInventoryHolder

class ContainerExplosionListener(private val plugin: Diawars): Listener {
    private val diamondMaterials = setOf(
        Material.DIAMOND_BLOCK,
        Material.DIAMOND,
    )

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        plugin.shulkerAccessManager.handleClick(event)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        plugin.shulkerAccessManager.handleDrag(event)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        plugin.shulkerAccessManager.handleClose(event)
        val inventory = event.inventory
        val holder = inventory.holder ?: return
        val hasDiamond = inventory.contents.any { it != null && it.type in diamondMaterials }

        if (!hasDiamond) return
        if (inventory.type == InventoryType.ENDER_CHEST) {
            plugin.containerExplosionManager.explodeEnderChest(event.player)
            return
        }
        // A double chest reports a DoubleChest holder (not BlockInventoryHolder),
        // so it must be accepted explicitly. Non-player entities (chest minecarts,
        // mules, ...) are containers too; player holders must never explode.
        val isExplodable = holder is BlockInventoryHolder ||
            holder is DoubleChest ||
            (holder is Entity && holder !is HumanEntity)
        if (!isExplodable) return

        val target = holder
        plugin.server.scheduler.runTask(plugin, Runnable {
            plugin.containerExplosionManager.explodeHolder(target)
        })
    }

    @EventHandler
    fun onInventoryMove(event: InventoryMoveItemEvent) {
        val movedItem = event.item
        if (movedItem.type !in diamondMaterials) return
        val destination = event.destination

        plugin.server.scheduler.runTask(plugin, Runnable {
            plugin.containerExplosionManager.explodeHolder(destination.holder ?: return@Runnable)
        })
    }

    @EventHandler
    fun onHopperPickup(event: InventoryPickupItemEvent) {
        if (event.inventory.type != InventoryType.HOPPER) return
        if (event.item.itemStack.type !in diamondMaterials) return
        val inventory = event.inventory

        plugin.server.scheduler.runTask(plugin, Runnable {
            plugin.containerExplosionManager.explodeHolder(inventory.holder ?: return@Runnable)
        })
    }
}