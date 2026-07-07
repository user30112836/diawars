package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

class RewardStore(private val plugin: Diawars) {
    private val storeFile = File(plugin.dataFolder, "rewards.yml")
    private val cache = mutableMapOf<UUID, Int>()

    init {
        load()
    }

    fun getPending(playerId: UUID): Int = cache.getOrDefault(playerId, 0)

    fun addPending(playerId: UUID, amount: Int) {
        cache[playerId] = getPending(playerId) + amount
        save()
    }

    fun clearPending(playerId: UUID) {
        cache.remove(playerId)
        save()
    }

    private fun save() {
        val config = YamlConfiguration()
        for ((uuid, amount) in cache) {
            config.set(uuid.toString(), amount)
        }
        try {
            config.save(storeFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save event rewards to $storeFile: ${e.message}")
        }
    }

    private fun load() {
        if (!storeFile.exists()) {
            storeFile.parentFile.mkdirs()
            storeFile.createNewFile()
        }

        val yaml = YamlConfiguration.loadConfiguration(storeFile)

        for (key in yaml.getKeys(false)) {
            try {
                cache[UUID.fromString(key)] = yaml.getInt(key)
            } catch (e: Exception) {
                plugin.logger.severe("Could not load event reward from $key: ${e.message}")
            }
        }

        plugin.logger.info("Loaded ${cache.size} pending reward(s)")
    }
}