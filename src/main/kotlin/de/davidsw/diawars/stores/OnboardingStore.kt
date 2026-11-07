package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.SqlOp
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID

class OnboardingStore(plugin: Diawars) : SqlStore(plugin, "onboarding") {
    private val enteredCache = mutableMapOf<UUID, Boolean>()
    private val starterCache = mutableMapOf<UUID, Boolean>()
    private val dirty = DirtyKeys<UUID>()

    init {
        migrateLegacyYaml("onboarding.yml", "onboarding") { yaml -> legacyRows(yaml) }
        load()
    }

    fun hasEnteredMainWorld(playerId: UUID): Boolean = enteredCache.getOrDefault(playerId, false)

    fun markEnteredMainWorld(playerId: UUID) {
        enteredCache[playerId] = true
        dirty.touch(playerId)
        markDirty()
    }

    /** One-time starter diamonds (granted on first join). Persisted separately so a
     * rejoin before leaving the lobby cannot grant them twice. */
    fun hasReceivedStarterDiamonds(playerId: UUID): Boolean = starterCache.getOrDefault(playerId, false)

    fun markStarterDiamondsReceived(playerId: UUID) {
        starterCache[playerId] = true
        dirty.touch(playerId)
        markDirty()
    }

    override fun loadFrom(connection: Connection) {
        enteredCache.clear()
        starterCache.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT player, entered_main_world, starter_diamonds FROM onboarding").use { rs ->
                while (rs.next()) {
                    try {
                        val playerId = UUID.fromString(rs.getString("player"))
                        if (rs.getInt("entered_main_world") != 0) enteredCache[playerId] = true
                        if (rs.getInt("starter_diamonds") != 0) starterCache[playerId] = true
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load onboarding state for ${rs.getString("player")}: ${e.message}")
                    }
                }
            }
        }
        plugin.logger.info(
            "Loaded onboarding state for ${enteredCache.size} player(s), ${starterCache.size} starter reward(s).",
        )
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (playerId in dirty.drain()) {
            val entered = enteredCache.getOrDefault(playerId, false)
            val starter = starterCache.getOrDefault(playerId, false)
            if (!entered && !starter) {
                ops += SqlOp("DELETE FROM onboarding WHERE player = ?", listOf(playerId))
            } else {
                ops += SqlOp(
                    "INSERT INTO onboarding (player, entered_main_world, starter_diamonds) VALUES (?, ?, ?) " +
                        "ON CONFLICT(player) DO UPDATE SET entered_main_world = excluded.entered_main_world, " +
                        "starter_diamonds = excluded.starter_diamonds",
                    listOf(playerId, entered, starter),
                )
            }
        }
    }

    /** Parses the pre-SQLite `onboarding.yml` (two legacy layouts) into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val entered = mutableMapOf<UUID, Boolean>()
        val starter = mutableMapOf<UUID, Boolean>()

        for (key in yaml.getKeys(false)) {
            if (key == "starter-reward") continue
            try {
                val playerId = UUID.fromString(key)
                if (yaml.isConfigurationSection(key)) {
                    // Forward-compatible: per-player section instead of a plain boolean.
                    val section = yaml.getConfigurationSection(key) ?: continue
                    entered[playerId] = section.getBoolean("entered", section.getBoolean("entered-main-world", false))
                    if (section.getBoolean("starter", section.getBoolean("starter-reward", false))) {
                        starter[playerId] = true
                    }
                } else {
                    entered[playerId] = yaml.getBoolean(key)
                }
            } catch (e: Exception) {
                plugin.logger.warning("Could not import onboarding state for $key: ${e.message}")
            }
        }
        yaml.getConfigurationSection("starter-reward")?.let { section ->
            for (id in section.getKeys(false)) {
                try {
                    if (section.getBoolean(id)) starter[UUID.fromString(id)] = true
                } catch (e: Exception) {
                    plugin.logger.warning("Could not import starter reward state for $id: ${e.message}")
                }
            }
        }

        return (entered.keys + starter.keys).map { playerId ->
            SqlOp(
                "INSERT INTO onboarding (player, entered_main_world, starter_diamonds) VALUES (?, ?, ?)",
                listOf(playerId, entered[playerId] == true, starter[playerId] == true),
            )
        }
    }
}
