package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack

class PvPListener(private val plugin: Diawars): Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.pvpManager.startActionbar(event.player)
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        val attackerPvPEnabled = plugin.pvpManager.isPvPEnabled(attacker.uniqueId)
        val victimPvPEnabled = plugin.pvpManager.isPvPEnabled(victim.uniqueId)

        if (!attackerPvPEnabled || !victimPvPEnabled) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        plugin.pvpManager.cleanupPlayer(event.player.uniqueId)
        plugin.diamondScoreboardManager.clearPlayer(event.player)
    }

    @EventHandler
    fun onPlayerKill(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer ?: return
        if (plugin.teamManager.arePlayersInSameTeam(killer.uniqueId, victim.uniqueId)) return
        victim.location.world.dropItemNaturally(victim.location, ItemStack(Material.DIAMOND, plugin.config.getInt("diamonds-per-kill", 2)))
    }
}