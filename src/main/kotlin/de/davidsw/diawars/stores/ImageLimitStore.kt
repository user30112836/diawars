package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.SqlOp
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID

class ImageLimitStore(plugin: Diawars) : SqlStore(plugin, "image_limits") {
    companion object {
        const val DEFAULT_LIMIT = 20
        const val LIMIT_STEP = 10
    }

    private val limits = mutableMapOf<UUID, Int>()
    private val dirty = DirtyKeys<UUID>()

    init {
        migrateLegacyYaml("image_limits.yml", "image_limits") { yaml -> legacyRows(yaml) }
        load()
    }

    fun getLimit(playerId: UUID): Int = limits.getOrDefault(playerId, DEFAULT_LIMIT)

    fun setLimit(playerId: UUID, limit: Int) {
        val clamped = limit.coerceAtLeast(0)
        limits[playerId] = clamped
        dirty.touch(playerId)
        markDirty()
    }

    /** Increases the limit by [LIMIT_STEP], returns the new limit. */
    fun increaseLimit(playerId: UUID): Int {
        val newLimit = getLimit(playerId) + LIMIT_STEP
        limits[playerId] = newLimit
        dirty.touch(playerId)
        markDirty()
        return newLimit
    }

    /** Decreases the limit by [LIMIT_STEP] (min 0), returns the new limit. */
    fun decreaseLimit(playerId: UUID): Int {
        val newLimit = (getLimit(playerId) - LIMIT_STEP).coerceAtLeast(0)
        limits[playerId] = newLimit
        dirty.touch(playerId)
        markDirty()
        return newLimit
    }

    fun hasCustomLimit(playerId: UUID): Boolean = limits.containsKey(playerId)

    fun getAllLimits(): Map<UUID, Int> = limits.toMap()

    override fun loadFrom(connection: Connection) {
        limits.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT player, limit_value FROM image_limits").use { rs ->
                while (rs.next()) {
                    try {
                        limits[UUID.fromString(rs.getString("player"))] = rs.getInt("limit_value").coerceAtLeast(0)
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load image limit from ${rs.getString("player")}: ${e.message}")
                    }
                }
            }
        }
        plugin.logger.info("Loaded image limits for ${limits.size} player(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (playerId in dirty.drain()) {
            val limit = limits[playerId]
            if (limit == null) {
                ops += SqlOp("DELETE FROM image_limits WHERE player = ?", listOf(playerId))
            } else {
                ops += SqlOp(
                    "INSERT INTO image_limits (player, limit_value) VALUES (?, ?) " +
                        "ON CONFLICT(player) DO UPDATE SET limit_value = excluded.limit_value",
                    listOf(playerId, limit),
                )
            }
        }
    }

    /** Parses the pre-SQLite `image_limits.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        for (key in yaml.getKeys(false)) {
            try {
                ops += SqlOp(
                    "INSERT INTO image_limits (player, limit_value) VALUES (?, ?)",
                    listOf(UUID.fromString(key), yaml.getInt(key, DEFAULT_LIMIT).coerceAtLeast(0)),
                )
            } catch (e: Exception) {
                plugin.logger.warning("Could not import image limit from $key: ${e.message}")
            }
        }
        return ops
    }
}
