package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.DiamondGlow
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.CraftItemEvent

class DiamondListener(private val plugin: Diawars): Listener {
    @EventHandler
    fun onPickup(event: EntityPickupItemEvent) {
        val stack = event.item.itemStack
        if (stack.type != Material.DIAMOND) return
        DiamondGlow.applyGlow(stack)
        event.item.itemStack = stack
    }

    @EventHandler
    fun onCraft(event: CraftItemEvent) {
        val stack = event.currentItem ?: return
        if (stack.type !in setOf(Material.DIAMOND, Material.DIAMOND_BLOCK)) return

        val player = event.whoClicked as Player

        if (event.click.isShiftClick) {
            plugin.server.scheduler.runTask(plugin, Runnable {
                player.inventory.contents.forEach { item ->
                    if (item == null || item.type !in setOf(Material.DIAMOND, Material.DIAMOND_BLOCK)) return@forEach
                    DiamondGlow.applyGlow(item)
                }
            })
        } else {
            DiamondGlow.applyGlow(stack)
            event.currentItem = stack
        }
    }
}