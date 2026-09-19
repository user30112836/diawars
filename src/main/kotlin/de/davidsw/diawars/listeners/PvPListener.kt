package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.pmm
import org.bukkit.Material
import org.bukkit.entity.AreaEffectCloud
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

class PvPListener(private val plugin: Diawars): Listener {
    private val store = plugin.store.pvpStatusStore
    private val manager = plugin.pvpManager

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        manager.startActionbar(event.player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val attacker = resolveAttacker(event.damager) ?: return
        val victim = event.entity as? Player ?: return

        // Arrows bouncing back at the shooter are not PvP and must not start a fight timer.
        if (attacker.uniqueId == victim.uniqueId) return

        // No fight detection in the lobby (LobbyListener cancels the damage separately).
        // Check both the lobby session and the world: the session covers players tracked as
        // in-lobby, the world check covers anyone standing in the lobby world regardless
        // of session state (e.g. after restarts or admin teleports).
        if (plugin.lobbyManager.isInLobby(attacker.uniqueId) || plugin.lobbyManager.isInLobby(victim.uniqueId)) return
        if (plugin.lobbyManager.isLobbyWorld(attacker.world.name) || plugin.lobbyManager.isLobbyWorld(victim.world.name)) return

        if (pvpAllowed(attacker.uniqueId, victim.uniqueId)) {
            manager.storeFight(victim)
            manager.storeFight(attacker)
        } else {
            event.isCancelled = true
        }
    }

    private fun pvpAllowed(attackerId: UUID, victimId: UUID): Boolean =
        store.isPvPEnabled(attackerId) && store.isPvPEnabled(victimId)

    private fun resolveAttacker(damager: Entity): Player? = when (damager) {
        is Player -> damager
        is Projectile -> damager.shooter as? Player
        is AreaEffectCloud -> damager.source as? Player
        else -> null
    }

    @EventHandler(ignoreCancelled = true)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
        if (event.bucket != Material.LAVA_BUCKET) return

        val placer = event.player
        val targetBlock = event.blockClicked.getRelative(event.blockFace)
        val target = targetBlock.location.toCenterLocation()
        val radius = plugin.config.getDouble("pvp-toggle.lava-protection-radius", 5.0)
        val radiusSquared = radius * radius

        val shielded = targetBlock.world.players.any { nearby ->
            nearby.uniqueId != placer.uniqueId &&
                    nearby.location.distanceSquared(target) <= radiusSquared &&
                    !pvpAllowed(placer.uniqueId, nearby.uniqueId)
        }

        if (shielded) {
            event.isCancelled = true
            placer.sendMessage(pmm("<red>Du kannst hier keine Lava platzieren, solange ein Spieler in der Nähe ist, gegen den dein PvP nicht aktiv ist!</red>"))
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        // Combat logging must drop dias BEFORE cleanupPlayer erases the fight tag:
        // DiamondLimitListener runs afterwards and would otherwise see no fight.
        if (manager.isInFight(player.uniqueId)) {
            plugin.diamondLimitManager.dropAll(player)
        }
        manager.cleanupPlayer(player.uniqueId)
        plugin.diamondScoreboardManager.clearPlayer(event.player)
        plugin.afkManager.cleanupPlayer(event.player.uniqueId)
    }

    @EventHandler
    fun onPlayerKill(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer ?: return
        if (plugin.teamManager.arePlayersInSameTeam(killer.uniqueId, victim.uniqueId)) return
        plugin.rewardManager.grantDiamondReward(killer, plugin.config.getInt("diamonds-per-kill", 2))
    }
}