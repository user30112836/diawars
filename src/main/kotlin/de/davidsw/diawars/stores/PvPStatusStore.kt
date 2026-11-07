package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import de.davidsw.diawars.util.SqlOp
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getCurrentTick
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

data class PvPStatus(
    val pvpEnabled: Boolean,
    val toggleActive: Boolean,
    val toggleStartTime: Int,
    val toggleDestination: Boolean,
    val oldTimeRemaining: Int,
)

private const val TICKS_PER_SECOND = 20

class PvPStatusStore(plugin: Diawars) : SqlStore(plugin, "pvp_status") {
    private val cache = mutableMapOf<UUID, PvPStatus>()
    private val dirty = DirtyKeys<UUID>()
    private val toggleDelaySeconds get() = plugin.config.getInt("pvp-toggle.delay-seconds", 300)

    init {
        migrateLegacyYaml("pvp_status.yml", "pvp_status") { yaml -> legacyRows(yaml) }
        load()
    }

    fun getAll(): MutableMap<UUID, PvPStatus> {
        return cache
    }

    fun isPvPEnabled(playerId: UUID): Boolean {
        return cache[playerId]?.pvpEnabled ?: true
    }

    fun hasPendingToggle(playerId: UUID): Boolean {
        return cache[playerId]?.toggleActive ?: false
    }

    fun getToggleDestination(playerId: UUID): Boolean? {
        return cache[playerId]?.toggleDestination
    }

    fun getRemainingTime(playerId: UUID): Long {
        val startTime = cache[playerId]?.toggleStartTime ?: return 0
        val elapsed = (getCurrentTick() - startTime) / TICKS_PER_SECOND
        return (toggleDelaySeconds.toLong() - elapsed).coerceAtLeast(0)
    }

    fun setRemainingTimeSeconds(playerId: UUID, seconds: Int) {
        val elapsedTicks = (toggleDelaySeconds - seconds.coerceIn(0, toggleDelaySeconds)) * TICKS_PER_SECOND
        cache[playerId] = PvPStatus(
            pvpEnabled = cache[playerId]?.pvpEnabled ?: true,
            toggleActive = true,
            toggleStartTime = getCurrentTick() - elapsedTicks,
            toggleDestination = cache[playerId]?.toggleDestination ?: true,
            oldTimeRemaining = 0,
        )
        touch(playerId)
    }

    fun applyPvPStatus(playerId: UUID, status: Boolean) {
        cache[playerId] = PvPStatus(
            pvpEnabled = status,
            toggleActive = false,
            toggleStartTime = cache[playerId]?.toggleStartTime ?: getCurrentTick(),
            toggleDestination = status,
            oldTimeRemaining = 0,
        )
        touch(playerId)
        val statusText = if (status) "aktiviert" else "deaktiviert"
        Bukkit.getPlayer(playerId)?.sendMessage(mm("<green>Dein PvP-Status wurde <yellow>$statusText</yellow>!</green>"))
    }

    fun applyToggle(playerId: UUID, destination: Boolean) {
        cache[playerId] = PvPStatus(
            pvpEnabled = cache[playerId]?.pvpEnabled ?: !destination,
            toggleActive = true,
            toggleStartTime = getCurrentTick(),
            toggleDestination = destination,
            oldTimeRemaining = 0,
        )
        touch(playerId)
    }

    fun removeToggle(playerId: UUID) {
        cache[playerId] = PvPStatus(
            pvpEnabled = cache[playerId]?.pvpEnabled ?: true,
            toggleActive = false,
            toggleStartTime = cache[playerId]?.toggleStartTime ?: getCurrentTick(),
            toggleDestination = cache[playerId]?.toggleDestination ?: true,
            oldTimeRemaining = 0,
        )
        touch(playerId)
    }

    private fun touch(playerId: UUID) {
        dirty.touch(playerId)
        markDirty()
    }

    override fun loadFrom(connection: Connection) {
        cache.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT player, enabled, toggle_active, toggle_start, toggle_destination, old_time FROM pvp_status",
            ).use { rs ->
                while (rs.next()) {
                    try {
                        cache[UUID.fromString(rs.getString("player"))] = PvPStatus(
                            rs.getInt("enabled") != 0,
                            rs.getInt("toggle_active") != 0,
                            rs.getInt("toggle_start"),
                            rs.getInt("toggle_destination") != 0,
                            rs.getInt("old_time"),
                        )
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load pvp status for ${rs.getString("player")}: ${e.message}")
                    }
                }
            }
        }
        plugin.logger.info("Loaded pvp status for ${cache.size} player(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (playerId in dirty.drain()) {
            val status = cache[playerId]
            if (status == null) {
                ops += SqlOp("DELETE FROM pvp_status WHERE player = ?", listOf(playerId))
                continue
            }
            // A running toggle is stored as remaining time so a crash/restart does
            // not silently shorten (or restart) the countdown.
            val oldTime = if (finalFlush && status.toggleActive) {
                getRemainingTime(playerId).toInt()
            } else {
                status.oldTimeRemaining
            }
            ops += SqlOp(
                "INSERT INTO pvp_status (player, enabled, toggle_active, toggle_start, toggle_destination, old_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(player) DO UPDATE SET enabled = excluded.enabled, " +
                    "toggle_active = excluded.toggle_active, toggle_start = excluded.toggle_start, " +
                    "toggle_destination = excluded.toggle_destination, old_time = excluded.old_time",
                listOf(
                    playerId, status.pvpEnabled, status.toggleActive, status.toggleStartTime,
                    status.toggleDestination, oldTime,
                ),
            )
        }
    }

    /** Parses the pre-SQLite `pvp_status.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        val defaultEnabled = plugin.config.getBoolean("pvp-toggle.default-enabled", true)
        for (key in yaml.getKeys(false)) {
            try {
                val playerId = UUID.fromString(key)
                val section = yaml.getConfigurationSection(key) ?: continue
                ops += SqlOp(
                    "INSERT INTO pvp_status (player, enabled, toggle_active, toggle_start, toggle_destination, old_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                    listOf(
                        playerId,
                        section.getBoolean("enabled", defaultEnabled),
                        section.getBoolean("toggle", false),
                        section.getInt("startTime", 0),
                        section.getBoolean("destination", true),
                        section.getInt("old-time", 0),
                    ),
                )
            } catch (e: Exception) {
                plugin.logger.warning("Could not import pvp status for $key: ${e.message}")
            }
        }
        return ops
    }
}
