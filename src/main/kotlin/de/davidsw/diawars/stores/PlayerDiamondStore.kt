package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.DiamondCounter
import de.davidsw.diawars.util.SqlOp
import de.davidsw.diawars.managers.Team
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.sql.Connection
import java.util.UUID
import kotlin.collections.iterator

class PlayerDiamondStore(plugin: Diawars) : SqlStore(plugin, "diamond_scores") {
    private val cache = mutableMapOf<UUID, Int>()
    private val names = mutableMapOf<UUID, String>()
    private val teamTotals = mutableMapOf<Team, Int>()
    private var teamTotalsInitialized = false
    private val dirty = DirtyKeys<UUID>()

    init {
        migrateLegacyYaml("diamond_scores.yml", "diamond_scores") { yaml -> legacyRows(yaml) }
        load()
    }

    fun getStoredCount(playerId: UUID): Int = cache.getOrDefault(playerId, 0)

    fun snapshot(player: Player) {
        val count = DiamondCounter.countForPlayer(player)
        updateCache(player.uniqueId, player.name, count)
        saveImmediately()
    }

    fun snapshotIfChanged(player: Player) {
        val count = DiamondCounter.countForPlayer(player)
        if (cache[player.uniqueId] == count) return
        updateCache(player.uniqueId, player.name, count)
        markDirty()
    }

    fun saveCount(player: Player, count: Int) {
        updateCache(player.uniqueId, player.name, count)
        markDirty()
    }

    fun getOfflineTeamCount(team: Team): Int {
        val onlineIds = plugin.server.onlinePlayers.mapTo(HashSet()) { it.uniqueId }
        return plugin.teamManager.getTeamMembers(team)
            .filter { it !in onlineIds }
            .sumOf { getStoredCount(it) }
    }

    fun getOnlineTeamCount(team: Team): Int {
        val onlineIds = plugin.server.onlinePlayers.mapTo(HashSet()) { it.uniqueId }
        return plugin.teamManager.getTeamMembers(team)
            .filter { it in onlineIds }
            .sumOf { getStoredCount(it) }
    }

    fun getTotalTeamCount(team: Team): Int {
        if (!teamTotalsInitialized) rebuildTeamTotals()
        return teamTotals.getOrDefault(team, 0)
    }

    private fun rebuildTeamTotals() {
        teamTotals.clear()
        teamTotalsInitialized = true
        for (team in Team.entries) {
            teamTotals[team] = plugin.teamManager.getTeamMembers(team).sumOf { getStoredCount(it) }
        }
    }

    override fun loadFrom(connection: Connection) {
        cache.clear()
        names.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT player, diamonds, name FROM diamond_scores").use { rs ->
                while (rs.next()) {
                    try {
                        val player = UUID.fromString(rs.getString("player"))
                        cache[player] = rs.getInt("diamonds")
                        names[player] = rs.getString("name") ?: "unknown"
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load diamond score for ${rs.getString("player")}: ${e.message}")
                    }
                }
            }
        }
        rebuildTeamTotals()
        plugin.logger.info("Loaded diamond scores for ${cache.size} player(s).")
    }

    private fun updateCache(playerId: UUID, playerName: String, count: Int) {
        val old = cache.getOrDefault(playerId, 0)
        cache[playerId] = count
        names[playerId] = playerName
        dirty.touch(playerId)
        markDirty()
        val team = plugin.teamManager.getPlayerTeam(playerId)
        if (team != null && old != count) {
            if (!teamTotalsInitialized) rebuildTeamTotals()
            teamTotals[team] = (teamTotals.getOrDefault(team, 0) + (count - old))
        }
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (player in dirty.drain()) {
            // Zero balances sum to nothing: drop them instead of keeping a row per
            // player who ever held a diamond. Memory retains the zeros so
            // snapshotIfChanged doesn't re-dirty on every refresh.
            val count = cache[player]
            if (count == null || count == 0) {
                ops += SqlOp("DELETE FROM diamond_scores WHERE player = ?", listOf(player))
            } else {
                ops += SqlOp(
                    "INSERT INTO diamond_scores (player, diamonds, name) VALUES (?, ?, ?) " +
                        "ON CONFLICT(player) DO UPDATE SET diamonds = excluded.diamonds, name = excluded.name",
                    listOf(player, count, names[player] ?: "unknown"),
                )
            }
        }
    }

    /** Parses the pre-SQLite `diamond_scores.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        for (key in yaml.getKeys(false)) {
            try {
                val player = UUID.fromString(key)
                val section = yaml.getConfigurationSection(key) ?: continue
                val count = section.getInt("diamonds", 0)
                if (count == 0) continue
                ops += SqlOp(
                    "INSERT INTO diamond_scores (player, diamonds, name) VALUES (?, ?, ?)",
                    listOf(player, count, section.getString("name") ?: "unknown"),
                )
            } catch (e: Exception) {
                plugin.logger.warning("Could not import diamond score for $key: ${e.message}")
            }
        }
        return ops
    }
}
