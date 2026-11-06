package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.JsonColumn
import de.davidsw.diawars.util.SqlOp
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID

class MessageStore(plugin: Diawars) : SqlStore(plugin, "pending_messages") {
    private val cache = mutableMapOf<UUID, MutableList<String>>()
    private val dirty = DirtyKeys<UUID>()

    init {
        migrateLegacyYaml("pending_messages.yml", "pending_messages") { yaml -> legacyRows(yaml) }
        load()
    }

    fun getPending(playerId: UUID): List<String> = cache[playerId] ?: emptyList()

    fun addPending(playerId: UUID, message: String) {
        val messages = cache.getOrPut(playerId) { mutableListOf() }
        messages.add(message)
        // Chat-frequency path: defer persistence to the periodic flush instead of
        // rewriting the store per queued message. Delivery reads memory.
        dirty.touch(playerId)
        markDirty()
    }

    fun clearPending(playerId: UUID) {
        if (cache.remove(playerId) != null) {
            // Deliver-then-clear in deliverPending: persist synchronously so a crash
            // cannot redeliver the same messages on next join.
            dirty.touch(playerId)
            flushNow()
        }
    }

    override fun loadFrom(connection: Connection) {
        cache.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT player, messages FROM pending_messages").use { rs ->
                while (rs.next()) {
                    try {
                        val messages = JsonColumn.strings(rs.getString("messages")).toMutableList()
                        if (messages.isNotEmpty()) {
                            cache[UUID.fromString(rs.getString("player"))] = messages
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load pending messages for ${rs.getString("player")}: ${e.message}")
                    }
                }
            }
        }
        plugin.logger.info("Loaded pending messages for ${cache.size} player(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (playerId in dirty.drain()) {
            val messages = cache[playerId]
            if (messages.isNullOrEmpty()) {
                ops += SqlOp("DELETE FROM pending_messages WHERE player = ?", listOf(playerId))
            } else {
                ops += SqlOp(
                    "INSERT INTO pending_messages (player, messages) VALUES (?, ?) " +
                        "ON CONFLICT(player) DO UPDATE SET messages = excluded.messages",
                    listOf(playerId, JsonColumn.encode(messages)),
                )
            }
        }
    }

    /** Parses the pre-SQLite `pending_messages.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        for (key in yaml.getKeys(false)) {
            try {
                val playerId = UUID.fromString(key)
                val messages = yaml.getStringList(key)
                if (messages.isEmpty()) continue
                ops += SqlOp(
                    "INSERT INTO pending_messages (player, messages) VALUES (?, ?)",
                    listOf(playerId, JsonColumn.encode(messages)),
                )
            } catch (e: Exception) {
                plugin.logger.warning("Could not import pending messages for $key: ${e.message}")
            }
        }
        return ops
    }
}
