package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.MenuManager
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent

class MenuListener(private val plugin: Diawars): Listener {
    private val clickHandlers: Map<Component, (InventoryClickEvent, Player, Int) -> Unit> = mapOf(
        MenuManager.TITLE_MAIN to { event, player, slot ->
            plugin.menu.mainMenu.handleMainClick(player, slot, event.inventory)
        },
        MenuManager.TITLE_BORDER to { event, player, slot ->
            plugin.menu.borderMenu.handleBorderClick(player, slot, event.inventory)
        },
        MenuManager.TITLE_SCOREBOARD to { event, player, slot ->
            plugin.menu.scoreboardMenu.handleScoreboardClick(player, slot, event.inventory)
        },
        MenuManager.TITLE_EVENT to { _, player, slot ->
            plugin.menu.eventMenu.handleEventClick(player, slot)
        },
        MenuManager.TITLE_VAULT to { event, player, slot ->
            plugin.menu.vaultMenu.handleVaultClick(player, slot, event.inventory)
        },
        MenuManager.TITLE_VAULT_LIST to { event, player, slot ->
            plugin.menu.vaultListMenu.handleVaultListClick(player, slot, event.inventory)
        },
        MenuManager.TITLE_MANUAL to { _, player, slot ->
            plugin.menu.manualMenu.handleManualClick(player, slot)
        },
    )

    private val managedMenus: Set<Component> = clickHandlers.keys

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val handler = clickHandlers[event.view.title()] ?: return

        event.isCancelled = true

        val slot = event.rawSlot
        if (slot !in 0 until 54) return

        val player = event.whoClicked as Player

        if (slot in 48..50) {
            plugin.menuManager.navigate(player, slot)
            return
        }

        handler(event, player, slot)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.view.title() !in managedMenus) return

        val player = event.player as Player
        plugin.menuManager.stopUpdater(player)
        plugin.menuManager.emptyHistory(player)
    }
}