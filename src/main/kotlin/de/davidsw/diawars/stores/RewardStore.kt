package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

class RewardStore(plugin: Diawars) : YamlStore(plugin, "rewards.yml") {
    private val cache = mutableMapOf<UUID, Int>()

    init {
        load()
    }

    fun getPending(playerId: UUID): Int = cache.getOrDefault(playerId, 0)

    fun addPending(playerId: UUID, amount: Int) {
        cache[playerId] = getPending(playerId) + amount
        saveImmediately()
    }

    fun clearPending(playerId: UUID) {
        cache.remove(playerId)
        saveImmediately()
    }

    override fun writeTo(yaml: YamlConfiguration) {
        for ((uuid, amount) in cache) {
            yaml.set(uuid.toString(), amount)
        }
    }

    override fun readFrom(yaml: YamlConfiguration) {
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
