package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.stores.BorderPreference
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.abs

class BorderManager(private val plugin: Diawars) {
    private var borderTask: BukkitRunnable? = null
    private val particleHeight = 128.0

    fun startBorderDisplay() {
        stopBorderDisplay()

        borderTask = object: BukkitRunnable() {
            override fun run() {
                displayBorderForOnlinePlayers()
            }
        }

        borderTask?.runTaskTimer(plugin, 0L, 20L)
    }

    fun stopBorderDisplay() {
        borderTask?.cancel()
        borderTask = null
    }

    private fun displayBorderForOnlinePlayers() {
        for (player in plugin.server.onlinePlayers) {
            if (!plugin.zoneManager.isZoneWorld(player.world)) continue
            val pref = plugin.store.borderPreferencesStore.getPreference(player.uniqueId)
            if (pref.enabled) {
                displayBorderNearPlayer(player, pref)
            }
        }
    }

    private fun displayBorderNearPlayer(player: Player, pref: BorderPreference) {
        val world = player.world
        val playerLocation = player.location
        val playerX = playerLocation.blockX
        val playerZ = playerLocation.blockZ
        val renderDistance = pref.renderDistance

        if (abs(playerX) > renderDistance) return

        val particleType = plugin.store.borderPreferencesStore.parseParticleType(pref.particleType) ?: Particle.DUST
        val color = pref.color
        val startZ = playerZ - renderDistance
        val endZ = playerZ + renderDistance

        for (z in startZ..endZ step pref.density.horizontal) {
            for (yOffset in 0..particleHeight.toInt() step pref.density.vertical) {
                val location = Location(
                    world,
                    ZoneManager.ZONE_BOUNDARY,
                    playerLocation.y - 10 + yOffset,
                    z.toDouble()
                )

                if (world.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)) {
                    when (particleType) {
                        Particle.DUST -> {
                            val dustOptions = Particle.DustOptions(color, 1.0f)
                            player.spawnParticle(
                                Particle.DUST,
                                location,
                                pref.amount,
                                0.0, 0.0, 0.0,
                                0.0,
                                dustOptions
                            )
                        }
                        else -> {
                            player.spawnParticle(particleType, location, 1, 0.0, 0.0, 0.0, 0.0)
                        }
                    }
                }
            }
        }
    }
}