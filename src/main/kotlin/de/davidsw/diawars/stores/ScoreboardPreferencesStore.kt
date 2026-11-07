package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.JsonColumn
import de.davidsw.diawars.util.SqlOp
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID

enum class ScoreboardComponent(val configKey: String, val label: String) {
    TEAM_DIAMONDS("team-diamonds", "Team-Diamanten"),
    OPPONENTS_DIAMONDS("opponents-diamonds", "Gegner-Diamanten"),
    PLAYER_DIAMONDS("player-diamonds", "Deine Diamanten"),
    ZONE_STATUS("zone-status", "Zonen-Status"),
    OWN_VAULT("own-vault", "Dein Vault"),
}

data class ScoreboardPreference(
    val sidebarEnabled: Boolean = true,
    val enabledComponents: Set<ScoreboardComponent> = ScoreboardComponent.entries.toSet(),
)

class ScoreboardPreferencesStore(plugin: Diawars) : SqlStore(plugin, "scoreboard_preferences") {
    private val preferences = mutableMapOf<UUID, ScoreboardPreference>()
    private val dirty = DirtyKeys<UUID>()

    init {
        migrateLegacyYaml("scoreboard_preferences.yml", "scoreboard_preferences") { yaml -> legacyRows(yaml) }
        load()
    }

    fun getPreference(playerId: UUID): ScoreboardPreference = preferences[playerId] ?: ScoreboardPreference()

    fun isSidebarEnabled(playerId: UUID): Boolean = getPreference(playerId).sidebarEnabled

    fun setSidebarEnabled(playerId: UUID, enabled: Boolean) {
        update(playerId) { it.copy(sidebarEnabled = enabled) }
    }

    fun isComponentEnabled(playerId: UUID, component: ScoreboardComponent): Boolean =
        component in getPreference(playerId).enabledComponents

    fun toggleComponent(playerId: UUID, component: ScoreboardComponent) {
        update(playerId) { pref ->
            val current = pref.enabledComponents
            val next = if (component in current) current - component else current + component
            pref.copy(enabledComponents = next)
        }
    }

    fun resetToDefault(playerId: UUID) {
        preferences.remove(playerId)
        dirty.touch(playerId)
        markDirty()
    }

    private fun update(playerId: UUID, transform: (ScoreboardPreference) -> ScoreboardPreference) {
        preferences[playerId] = transform(getPreference(playerId))
        dirty.touch(playerId)
        markDirty()
    }

    override fun loadFrom(connection: Connection) {
        preferences.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT player, sidebar_enabled, components FROM scoreboard_preferences",
            ).use { rs ->
                while (rs.next()) {
                    try {
                        preferences[UUID.fromString(rs.getString("player"))] = ScoreboardPreference(
                            sidebarEnabled = rs.getInt("sidebar_enabled") != 0,
                            enabledComponents = JsonColumn.strings(rs.getString("components"))
                                .mapNotNull { name -> ScoreboardComponent.entries.firstOrNull { it.name == name } }
                                .toSet(),
                        )
                    } catch (e: Exception) {
                        plugin.logger.warning(
                            "Could not load scoreboard preferences for ${rs.getString("player")}: ${e.message}",
                        )
                    }
                }
            }
        }
        plugin.logger.info("Loaded scoreboard preferences for ${preferences.size} player(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (playerId in dirty.drain()) {
            val pref = preferences[playerId]
            if (pref == null) {
                ops += SqlOp("DELETE FROM scoreboard_preferences WHERE player = ?", listOf(playerId))
                continue
            }
            ops += SqlOp(
                "INSERT INTO scoreboard_preferences (player, sidebar_enabled, components) VALUES (?, ?, ?) " +
                    "ON CONFLICT(player) DO UPDATE SET sidebar_enabled = excluded.sidebar_enabled, " +
                    "components = excluded.components",
                listOf(
                    playerId,
                    pref.sidebarEnabled,
                    JsonColumn.encode(ScoreboardComponent.entries.filter { it in pref.enabledComponents }.map { it.name }),
                ),
            )
        }
    }

    /** Parses the pre-SQLite `scoreboard_preferences.yml` (both legacy layouts). */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        for (key in yaml.getKeys(false)) {
            try {
                val playerId = UUID.fromString(key)
                val section = yaml.getConfigurationSection(key)

                val pref = if (section == null) {
                    // Legacy format: key -> boolean (only stored whether the sidebar was on)
                    ScoreboardPreference(sidebarEnabled = yaml.getBoolean(key, true))
                } else {
                    ScoreboardPreference(
                        sidebarEnabled = section.getBoolean("sidebar-enabled", true),
                        enabledComponents = ScoreboardComponent.entries
                            .filter { section.getBoolean("components.${it.configKey}", true) }
                            .toSet(),
                    )
                }

                ops += SqlOp(
                    "INSERT INTO scoreboard_preferences (player, sidebar_enabled, components) VALUES (?, ?, ?)",
                    listOf(
                        playerId,
                        pref.sidebarEnabled,
                        JsonColumn.encode(ScoreboardComponent.entries.filter { it in pref.enabledComponents }.map { it.name }),
                    ),
                )
            } catch (e: Exception) {
                plugin.logger.warning("Could not import scoreboard preferences for $key: ${e.message}")
            }
        }
        return ops
    }
}
