package de.davidsw.diawars.listeners

import de.davidsw.diawars.managers.InspectInventoryHolder
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

class InventoryInspectListener: Listener {
    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.inventory.holder is InspectInventoryHolder) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is InspectInventoryHolder) {
            event.isCancelled = true
        }
    }
}