package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.StoreFiles
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

class PlayerSpawnStore(private val plugin: Diawars) {
    private val storeFile = StoreFiles.resolve(plugin, "player_spawns.yml")
    private val mainSpawns = mutableMapOf<UUID, Location>()
    private val worldSpawns = mutableMapOf<String, MutableMap<UUID, Location>>()

    init {
        load()
    }

    fun getMainSpawn(playerId: UUID): Location? = mainSpawns[playerId]
    fun getWorldSpawn(worldName: String, playerId: UUID): Location? = worldSpawns[worldName]?.get(playerId)

    fun setMainSpawn(playerId: UUID, location: Location) {
        mainSpawns[playerId] = location
        save()
    }

    fun setWorldSpawn(worldName: String, playerId: UUID, location: Location) {
        worldSpawns.getOrPut(worldName) { mutableMapOf() }[playerId] = location
        save()
    }

    fun clearWorldSpawns(worldName: String) {
        if (worldSpawns.remove(worldName) != null) save()
    }

    private fun save() {
        val config = YamlConfiguration()
        for ((uuid, loc) in mainSpawns) config.set("main.$uuid", loc)
        for ((worldName, players) in worldSpawns) {
            for ((uuid, loc) in players) config.set("worlds.$worldName.$uuid", loc)
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
                    section.getLocation(key)?.let { mainSpawns[UUID.fromString(key)] = it }
                } catch (e: Exception) {
                    plugin.logger.warning("Could not load main spawn for $key: ${e.message}")
                }
            }
        }

        yaml.getConfigurationSection("worlds")?.let { worldsSection ->
            for (worldName in worldsSection.getKeys(false)) {
                val playersSection = worldsSection.getConfigurationSection(worldName) ?: continue
                val perWorld = mutableMapOf<UUID, Location>()
                for (key in playersSection.getKeys(false)) {
                    try {
                        playersSection.getLocation(key)?.let { perWorld[UUID.fromString(key)] = it }
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