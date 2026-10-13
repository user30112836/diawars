package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

class ImageLimitStore(plugin: Diawars) : YamlStore(plugin, "image_limits.yml") {
    companion object {
        const val DEFAULT_LIMIT = 20
        const val LIMIT_STEP = 10
    }

    private val limits = mutableMapOf<UUID, Int>()

    init {
        load()
    }

    fun getLimit(playerId: UUID): Int = limits.getOrDefault(playerId, DEFAULT_LIMIT)

    fun setLimit(playerId: UUID, limit: Int) {
        val clamped = limit.coerceAtLeast(0)
        limits[playerId] = clamped
        markDirty()
    }

    /** Increases the limit by [LIMIT_STEP], returns the new limit. */
    fun increaseLimit(playerId: UUID): Int {
        val newLimit = getLimit(playerId) + LIMIT_STEP
        limits[playerId] = newLimit
        markDirty()
        return newLimit
    }

    /** Decreases the limit by [LIMIT_STEP] (min 0), returns the new limit. */
    fun decreaseLimit(playerId: UUID): Int {
        val newLimit = (getLimit(playerId) - LIMIT_STEP).coerceAtLeast(0)
        limits[playerId] = newLimit
        markDirty()
        return newLimit
    }

    fun hasCustomLimit(playerId: UUID): Boolean = limits.containsKey(playerId)

    fun getAllLimits(): Map<UUID, Int> = limits.toMap()

    override fun writeTo(yaml: YamlConfiguration) {
        for ((uuid, limit) in limits) {
            yaml.set(uuid.toString(), limit)
        }
    }

    override fun readFrom(yaml: YamlConfiguration) {
        limits.clear()
        for (key in yaml.getKeys(false)) {
            try {
                val uuid = UUID.fromString(key)
                limits[uuid] = yaml.getInt(key, DEFAULT_LIMIT).coerceAtLeast(0)
            } catch (e: Exception) {
                plugin.logger.warning("Could not load image limit from $key: ${e.message}")
            }
        }
        plugin.logger.info("Loaded image limits for ${limits.size} player(s).")
    }
}
