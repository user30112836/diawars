package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.Bukkit.getWorld
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.configuration.ConfigurationSection
import java.util.UUID

private data class SpawnPoint(
    val worldName: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
)

class PlayerSpawnStore(plugin: Diawars) : YamlStore(plugin, "player_spawns.yml") {
    private val mainSpawns = mutableMapOf<UUID, SpawnPoint>()
    private val worldSpawns = mutableMapOf<String, MutableMap<UUID, SpawnPoint>>()

    init {
        load()
    }

    fun getMainSpawn(playerId: UUID): Location? = mainSpawns[playerId]?.toLocation()
    fun getWorldSpawn(worldName: String, playerId: UUID): Location? = worldSpawns[worldName]?.get(playerId)?.toLocation()

    fun setMainSpawn(playerId: UUID, location: Location) {
        mainSpawns[playerId] = location.toSpawnPoint()
        markDirty()
    }

    fun setWorldSpawn(worldName: String, playerId: UUID, location: Location) {
        worldSpawns.getOrPut(worldName) { mutableMapOf() }[playerId] = location.toSpawnPoint()
        markDirty()
    }

    fun clearWorldSpawns(worldName: String) {
        if (worldSpawns.remove(worldName) != null) markDirty()
    }

    private fun Location.toSpawnPoint() = SpawnPoint(world?.name ?: plugin.server.worlds.first().name, x, y, z, yaw, pitch)

    private fun SpawnPoint.toLocation(): Location? {
        val world = getWorld(worldName) ?: return null
        return Location(world, x, y, z, yaw, pitch)
    }

    override fun writeTo(yaml: YamlConfiguration) {
        for ((uuid, point) in mainSpawns) {
            point.writeTo(yaml, "main.$uuid")
        }
        for ((worldName, players) in worldSpawns) {
            for ((uuid, point) in players) {
                point.writeTo(yaml, "worlds.$worldName.$uuid")
            }
        }
    }

    private fun SpawnPoint.writeTo(yaml: YamlConfiguration, key: String) {
        yaml.set("$key.world", worldName)
        yaml.set("$key.x", x)
        yaml.set("$key.y", y)
        yaml.set("$key.z", z)
        yaml.set("$key.yaw", yaw)
        yaml.set("$key.pitch", pitch)
    }

    private fun readSpawnPoint(section: ConfigurationSection, key: String): SpawnPoint? {
        val pointSection = section.getConfigurationSection(key) ?: return null
        return SpawnPoint(
            worldName = pointSection.getString("world") ?: return null,
            x = pointSection.getDouble("x"),
            y = pointSection.getDouble("y"),
            z = pointSection.getDouble("z"),
            yaw = pointSection.getDouble("yaw").toFloat(),
            pitch = pointSection.getDouble("pitch").toFloat(),
        )
    }

    override fun readFrom(yaml: YamlConfiguration) {
        yaml.getConfigurationSection("main")?.let { section ->
            for (key in section.getKeys(false)) {
                try {
                    mainSpawns[UUID.fromString(key)] = readSpawnPoint(section, key) ?: continue
                } catch (e: Exception) {
                    plugin.logger.warning("Could not load main spawn for $key: ${e.message}")
                }
            }
        }

        yaml.getConfigurationSection("worlds")?.let { worldsSection ->
            for (worldName in worldsSection.getKeys(false)) {
                val playersSection = worldsSection.getConfigurationSection(worldName) ?: continue
                val perWorld = mutableMapOf<UUID, SpawnPoint>()
                for (key in playersSection.getKeys(false)) {
                    try {
                        perWorld[UUID.fromString(key)] = readSpawnPoint(playersSection, key) ?: continue
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load spawn for $worldName/$key: ${e.message}")
                    }
                }
                worldSpawns[worldName] = perWorld
            }
        }

        plugin.logger.info("Loaded ${mainSpawns.size} main spawn(s) across ${worldSpawns.size} non-main world(s).")
    }
}
