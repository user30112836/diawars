package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.GameRule
import org.bukkit.Material
import org.bukkit.entity.Item
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.ItemMergeEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.ChunkLoadEvent

class DiamondLimitListener(private val plugin: Diawars): Listener {
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val limit = if (plugin.pvpManager.isInFight(player.uniqueId)) 0 else plugin.config.getInt("diamond-limit", 32)
        plugin.diamondLimitManager.enforceLimit(player, limit)
        plugin.store.playerDiamondStore.snapshot(player)
    }

    @EventHandler
    fun onItemSpawn(event: ItemSpawnEvent) {
        val item = event.entity.itemStack
        if (item.type == Material.DIAMOND) {
            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.diamondLimitManager.resetTicksLived(event.entity)
                plugin.diamondLimitManager.trackDiamond(event.entity)
            })
        }
        if (item.type == Material.DIAMOND_BLOCK) {
            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.diamondLimitManager.convertDiamondBlocks(event.entity)
            })
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity
        if (entity is Item && entity.uniqueId in plugin.diamondLimitManager.trackedDiamonds) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onItemMerge(event: ItemMergeEvent) {
        val item = event.entity
        val target = event.target

        if (target.itemStack.type != Material.DIAMOND && item.itemStack.type != Material.DIAMOND) return

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.diamondLimitManager.reTrackDiamond(target)
        }, 2L)
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        event.chunk.entities
            .filterIsInstance<Item>()
            .filter { it.itemStack.type == Material.DIAMOND }
            .filter { it.uniqueId !in plugin.diamondLimitManager.trackedDiamonds }
            .forEach { plugin.diamondLimitManager.trackDiamond(it) }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!(event.player.world.getGameRuleValue(GameRule.KEEP_INVENTORY) ?: false)) return
        val player = event.entity
        plugin.diamondLimitManager.dropAll(player)
        plugin.store.playerDiamondStore.saveCount(player, 0)
    }
}