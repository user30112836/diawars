package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent

class LobbyListener(plugin: Diawars): Listener {
    private val manager = plugin.lobbyManager

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        manager.handlePlayerJoin(event.player)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (manager.isLobbyWorld(event.block.world.name) && !event.player.hasPermission("diawars.admin")) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (manager.isLobbyWorld(event.block.world.name) && !event.player.hasPermission("diawars.admin")) event.isCancelled = true
    }

    @EventHandler
    fun onFoodChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        if (manager.isLobbyWorld(player.world.name)) event.isCancelled = true
    }

    @EventHandler
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (manager.isLobbyWorld(player.world.name)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInteraction(event: PlayerInteractEvent) {
        val player = event.player
        if (manager.isLobbyWorld(player.world.name) && !player.hasPermission("diawars.admin")) event.isCancelled = true
    }
}