package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.MenuManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent

class MenuListener(private val plugin: Diawars): Listener {
    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as Player
        val title = event.view.title()

        when (title) {
            MenuManager.TITLE_MAIN -> {
                event.isCancelled = true
                val slot = event.rawSlot
                if (slot !in 0 until 54) return
                if (slot in 48 until 51) plugin.menuManager.navigate(player, slot) else plugin.menu.mainMenu.handleMainClick(event.whoClicked as Player, slot, event.inventory)
            }
            MenuManager.TITLE_BORDER -> {
                event.isCancelled = true
                val slot = event.rawSlot
                if (slot !in 0..53) return
                if (slot in 48 until 51) plugin.menuManager.navigate(player, slot) else plugin.menu.borderMenu.handleBorderClick(event.whoClicked as Player, slot, event.inventory)
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.view.title() == MenuManager.TITLE_BORDER || event.view.title() == MenuManager.TITLE_MAIN) {
            plugin.menuManager.stopUpdater(event.player as Player)
            plugin.menuManager.emptyHistory(event.player as Player)
        }
    }
}