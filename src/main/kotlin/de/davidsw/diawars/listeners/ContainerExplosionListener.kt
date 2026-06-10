package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.BanList
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
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
    fun onInventoryClose(event: InventoryCloseEvent) {
        val inventory = event.inventory
        val holder = inventory.holder ?: return
        val hasDiamond = inventory.contents.any { it != null && it.type in diamondMaterials }

        if (!hasDiamond) return
        if (inventory.type == InventoryType.ENDER_CHEST) {
            plugin.containerExplosionManager.explodeEnderChest(event.player)
            return
        }
        if (holder !is BlockInventoryHolder) return

        plugin.server.scheduler.runTask(plugin, Runnable {
            plugin.containerExplosionManager.explodeHolder(inventory.holder ?: return@Runnable)
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