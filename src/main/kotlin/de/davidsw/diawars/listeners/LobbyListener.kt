package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
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
import org.bukkit.event.player.PlayerMoveEvent

class LobbyListener(plugin: Diawars): Listener {
    private val manager = plugin.lobbyManager

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        manager.handlePlayerJoin(event.player)
        manager.handleOnboardingJoin(event.player)
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

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        if (!manager.isLobbyWorld(player.world.name)) return
        // Void damage is cancelled in the lobby, so falling players would fall
        // forever: rescue them back to the lobby spawn instead.
        if (event.to.y <= -64) {
            manager.getSpawnLocation()?.let {
                player.teleport(it)
                player.sendMessage(mm("<yellow>Du bist ins Void gefallen und wurdest zurück zum Lobby-Spawn teleportiert!</yellow>"))
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInteraction(event: PlayerInteractEvent) {
        val player = event.player
        if (manager.isLobbyWorld(player.world.name) && !player.hasPermission("diawars.admin")) event.isCancelled = true
    }
}