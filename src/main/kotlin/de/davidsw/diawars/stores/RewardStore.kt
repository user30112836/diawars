package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.SqlOp
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID

class RewardStore(plugin: Diawars) : SqlStore(plugin, "rewards") {
    private val cache = mutableMapOf<UUID, Int>()
    private val dirty = DirtyKeys<UUID>()

    init {
        migrateLegacyYaml("rewards.yml", "rewards") { yaml -> legacyRows(yaml) }
        load()
    }

    fun getPending(playerId: UUID): Int = cache.getOrDefault(playerId, 0)

    fun addPending(playerId: UUID, amount: Int) {
        cache[playerId] = getPending(playerId) + amount
        // Small store, infrequent writes: persist synchronously so a crash cannot
        // lose owed diamonds or (after clear-then-grant) pay them out twice.
        dirty.touch(playerId)
        flushNow()
    }

    fun clearPending(playerId: UUID) {
        // Durable removal BEFORE the payout in checkPendingPlayer: crash after this
        // point must not find the balance on disk again (second payout).
        if (cache.remove(playerId) != null) {
            dirty.touch(playerId)
            flushNow()
        }
    }

    override fun loadFrom(connection: Connection) {
        cache.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT player, amount FROM rewards").use { rs ->
                while (rs.next()) {
                    try {
                        cache[UUID.fromString(rs.getString("player"))] = rs.getInt("amount")
                    } catch (e: Exception) {
                        plugin.logger.severe("Could not load event reward from ${rs.getString("player")}: ${e.message}")
                    }
                }
            }
        }
        plugin.logger.info("Loaded ${cache.size} pending reward(s)")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (playerId in dirty.drain()) {
            val amount = cache[playerId]
            if (amount == null || amount == 0) {
                ops += SqlOp("DELETE FROM rewards WHERE player = ?", listOf(playerId))
            } else {
                ops += SqlOp(
                    "INSERT INTO rewards (player, amount) VALUES (?, ?) " +
                        "ON CONFLICT(player) DO UPDATE SET amount = excluded.amount",
                    listOf(playerId, amount),
                )
            }
        }
    }

    /** Parses the pre-SQLite `rewards.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        for (key in yaml.getKeys(false)) {
            try {
                val amount = yaml.getInt(key)
                if (amount == 0) continue
                ops += SqlOp(
                    "INSERT INTO rewards (player, amount) VALUES (?, ?)",
                    listOf(UUID.fromString(key), amount),
                )
            } catch (e: Exception) {
                plugin.logger.severe("Could not import event reward from $key: ${e.message}")
            }
        }
        return ops
    }
}
