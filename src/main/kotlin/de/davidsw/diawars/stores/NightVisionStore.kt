package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.SqlOp
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID

/**
 * Per-player night vision opt-ins. Only `true` entries are stored;
 * everyone else defaults to off.
 */
class NightVisionStore(plugin: Diawars) : SqlStore(plugin, "night_vision") {
    private val enabled = mutableSetOf<UUID>()
    private val dirty = DirtyKeys<UUID>()

    init {
        migrateLegacyYaml("night_vision.yml", "night_vision") { yaml -> legacyRows(yaml) }
        load()
    }

    fun isEnabled(playerId: UUID): Boolean = playerId in enabled

    fun setEnabled(playerId: UUID, on: Boolean) {
        if (on) enabled.add(playerId) else enabled.remove(playerId)
        dirty.touch(playerId)
        markDirty()
    }

    override fun loadFrom(connection: Connection) {
        enabled.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT player FROM night_vision").use { rs ->
                while (rs.next()) {
                    try {
                        enabled += UUID.fromString(rs.getString("player"))
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load night vision state for ${rs.getString("player")}: ${e.message}")
                    }
                }
            }
        }
        plugin.logger.info("Loaded night vision state for ${enabled.size} player(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (playerId in dirty.drain()) {
            if (playerId in enabled) {
                ops += SqlOp("INSERT INTO night_vision (player) VALUES (?) ON CONFLICT(player) DO NOTHING", listOf(playerId))
            } else {
                ops += SqlOp("DELETE FROM night_vision WHERE player = ?", listOf(playerId))
            }
        }
    }

    /** Parses the pre-SQLite `night_vision.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        for (key in yaml.getKeys(false)) {
            try {
                if (!yaml.getBoolean(key, false)) continue
                ops += SqlOp("INSERT INTO night_vision (player) VALUES (?)", listOf(UUID.fromString(key)))
            } catch (e: Exception) {
                plugin.logger.warning("Could not import night vision state for $key: ${e.message}")
            }
        }
        return ops
    }
}
