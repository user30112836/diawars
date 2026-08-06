package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.StoreFiles
import org.bukkit.Bukkit.getWorld
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

private data class SpawnPoint(
    val worldName: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
)

class PlayerSpawnStore(private val plugin: Diawars) {
    private val storeFile = StoreFiles.resolve(plugin, "player_spawns.yml")
    private val mainSpawns = mutableMapOf<UUID, SpawnPoint>()
    private val worldSpawns = mutableMapOf<String, MutableMap<UUID, SpawnPoint>>()

    init {
        load()
    }

    fun getMainSpawn(playerId: UUID): Location? = mainSpawns[playerId]?.toLocation()
    fun getWorldSpawn(worldName: String, playerId: UUID): Location? = worldSpawns[worldName]?.get(playerId)?.toLocation()

    fun setMainSpawn(playerId: UUID, location: Location) {
        mainSpawns[playerId] = location.toSpawnPoint()
        save()
    }

    fun setWorldSpawn(worldName: String, playerId: UUID, location: Location) {
        worldSpawns.getOrPut(worldName) { mutableMapOf() }[playerId] = location.toSpawnPoint()
        save()
    }

    fun clearWorldSpawns(worldName: String) {
        if (worldSpawns.remove(worldName) != null) save()
    }

    private fun Location.toSpawnPoint() = SpawnPoint(world?.name ?: plugin.server.worlds.first().name, x, y, z, yaw, pitch)

    private fun SpawnPoint.toLocation(): Location? {
        val world = getWorld(worldName) ?: return null
        return Location(world, x, y, z, yaw, pitch)
    }

    private fun save() {
        val config = YamlConfiguration()
        for ((uuid, point) in mainSpawns) {
            val key = "main.$uuid"
            config.set("$key.world", point.worldName)
            config.set("$key.x", point.x)
            config.set("$key.y", point.y)
            config.set("$key.z", point.z)
            config.set("$key.yaw", point.yaw)
            config.set("$key.pitch", point.pitch)
        }
        for ((worldName, players) in worldSpawns) {
            for ((uuid, point) in players) {
                val key = "worlds.$worldName.$uuid"
                config.set("$key.world", point.worldName)
                config.set("$key.x", point.x)
                config.set("$key.y", point.y)
                config.set("$key.z", point.z)
                config.set("$key.yaw", point.yaw)
                config.set("$key.pitch", point.pitch)
            }
        }
        try {
            config.save(storeFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save player spawns to $storeFile: ${e.message}")
        }
    }

    private fun load() {
        if (!storeFile.exists()) {
            storeFile.parentFile.mkdirs()
            storeFile.createNewFile()
        }

        val yaml = YamlConfiguration.loadConfiguration(storeFile)

        yaml.getConfigurationSection("main")?.let { section ->
            for (key in section.getKeys(false)) {
                try {
                    val pointSection = section.getConfigurationSection(key) ?: continue
                    mainSpawns[UUID.fromString(key)] = SpawnPoint(
                        worldName = pointSection.getString("world") ?: continue,
                        x = pointSection.getDouble("x"),
                        y = pointSection.getDouble("y"),
                        z = pointSection.getDouble("z"),
                        yaw = pointSection.getDouble("yaw").toFloat(),
                        pitch = pointSection.getDouble("pitch").toFloat(),
                    )
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
                        val pointSection = playersSection.getConfigurationSection(key) ?: continue
                        perWorld[UUID.fromString(key)] = SpawnPoint(
                            worldName = pointSection.getString("world") ?: continue,
                            x = pointSection.getDouble("x"),
                            y = pointSection.getDouble("y"),
                            z = pointSection.getDouble("z"),
                            yaw = pointSection.getDouble("yaw").toFloat(),
                            pitch = pointSection.getDouble("pitch").toFloat(),
                        )
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