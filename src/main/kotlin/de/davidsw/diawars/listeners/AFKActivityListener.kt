package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerToggleSprintEvent

class AfkActivityListener(private val plugin: Diawars): Listener {
    @EventHandler
    fun onAnimation(event: PlayerAnimationEvent) {
        plugin.afkManager.recordActivity(event.player.uniqueId)
    }

    @EventHandler
    fun onToggleSprint(event: PlayerToggleSprintEvent) {
        plugin.afkManager.recordActivity(event.player.uniqueId)
    }

    @EventHandler
    fun onToggleSneak(event: PlayerToggleSneakEvent) {
        plugin.afkManager.recordActivity(event.player.uniqueId)
    }

    @EventHandler
    fun onItemHeld(event: PlayerItemHeldEvent) {
        plugin.afkManager.recordActivity(event.player.uniqueId)
    }

    @EventHandler
    fun onSwapHands(event: PlayerSwapHandItemsEvent) {
        plugin.afkManager.recordActivity(event.player.uniqueId)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        plugin.afkManager.recordActivity(player.uniqueId)
    }

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        plugin.afkManager.recordActivity(event.player.uniqueId)
    }

    @EventHandler
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        plugin.afkManager.recordActivity(event.player.uniqueId)
    }
}