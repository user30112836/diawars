package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID

/**
 * Per-player night vision toggle (Bedrock players have no fullbright mods).
 * The effect is infinite, non-ambient and particle-free. Toggles are persisted
 * in [de.davidsw.diawars.stores.NightVisionStore] and re-applied on join and
 * respawn, since death, milk and lobby/event state swaps all clear effects.
 *
 * The global `night-vision-toggle` config key gates the feature: when off,
 * every player has night vision disabled and toggling is refused.
 */
class NightVisionManager(private val plugin: Diawars) {
    companion object {
        const val CONFIG_KEY = "night-vision-toggle"
    }

    fun isAvailable(): Boolean = plugin.config.getBoolean(CONFIG_KEY, true)

    fun hasEnabled(playerId: UUID): Boolean = plugin.store.nightVisionStore.isEnabled(playerId)

    /**
     * Flips the player's toggle. Returns the new state, or null when the
     * feature is globally disabled.
     */
    fun toggle(player: Player): Boolean? {
        if (!isAvailable()) return null
        val next = !hasEnabled(player.uniqueId)
        setEnabled(player, next)
        return next
    }

    fun setEnabled(player: Player, enabled: Boolean): Boolean {
        if (!isAvailable()) return false
        plugin.store.nightVisionStore.setEnabled(player.uniqueId, enabled)
        if (enabled) {
            applyEffect(player)
        } else {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION)
        }
        return true
    }

    /** Re-applies the effect for opted-in players (join/respawn). No-op otherwise. */
    fun reapply(player: Player) {
        if (!isAvailable()) return
        if (hasEnabled(player.uniqueId)) applyEffect(player)
    }

    /**
     * Enforces the global config state: strips the effect from everyone when
     * disabled, re-applies it to opted-in players when enabled. Call after
     * config changes and reloads.
     */
    fun refreshGlobalState() {
        for (player in plugin.server.onlinePlayers) {
            if (isAvailable()) {
                reapply(player)
            } else {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION)
            }
        }
    }

    private fun applyEffect(player: Player) {
        player.addPotionEffect(
            PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false),
        )
    }
}
