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

        if (abs(playerX - ZoneManager.ZONE_BOUNDARY) > renderDistance) return

        val particleType = plugin.store.borderPreferencesStore.parseParticleType(pref.particleType) ?: Particle.DUST
        val color = pref.color
        val horizontalStep = pref.density.horizontal.coerceAtLeast(1)
        val verticalStep = pref.density.vertical.coerceAtLeast(1)
        val startZ = playerZ - renderDistance
        val endZ = playerZ + renderDistance

        val below = plugin.config.getInt("border.vertical-below", 10).coerceIn(0, 64)
        val above = plugin.config.getInt("border.vertical-above", 24).coerceIn(0, 128)
        val minY = maxOf(world.minHeight, playerLocation.blockY - below)
        val maxY = minOf(world.maxHeight - 1, playerLocation.blockY + above)
        val location = Location(world, ZoneManager.ZONE_BOUNDARY, minY.toDouble(), startZ.toDouble())

        for (z in startZ..endZ step horizontalStep) {
            if (!world.isChunkLoaded(location.blockX shr 4, z shr 4)) continue
            location.z = z.toDouble()

            var y = minY
            while (y <= maxY) {
                location.y = y.toDouble()

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

                y += verticalStep
            }
        }
    }
}