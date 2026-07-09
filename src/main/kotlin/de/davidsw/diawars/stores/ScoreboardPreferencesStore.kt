package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

enum class ScoreboardComponent(val configKey: String, val label: String) {
    TEAM_DIAMONDS("team-diamonds", "Team-Diamanten"),
    OPPONENTS_DIAMONDS("opponents-diamonds", "Gegner-Diamanten"),
    PLAYER_DIAMONDS("player-diamonds", "Deine Diamanten"),
    ZONE_STATUS("zone-status", "Zonen-Status"),
}

data class ScoreboardPreference(
    val sidebarEnabled: Boolean = true,
    val enabledComponents: Set<ScoreboardComponent> = ScoreboardComponent.entries.toSet(),
)

class ScoreboardPreferencesStore(private val plugin: Diawars) {
    private val storeFile = File(plugin.dataFolder, "scoreboard_preferences.yml")
    private val preferences = mutableMapOf<UUID, ScoreboardPreference>()

    init { load() }

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

    fun setComponentEnabled(playerId: UUID, component: ScoreboardComponent, enabled: Boolean) {
        update(playerId) { pref ->
            val next = if (enabled) pref.enabledComponents + component else pref.enabledComponents - component
            pref.copy(enabledComponents = next)
        }
    }

    fun resetToDefault(playerId: UUID) {
        preferences.remove(playerId)
        save()
    }

    private fun update(playerId: UUID, transform: (ScoreboardPreference) -> ScoreboardPreference) {
        preferences[playerId] = transform(getPreference(playerId))
        save()
    }

    private fun load() {
        if (!storeFile.exists()) {
            storeFile.parentFile.mkdirs()
            storeFile.createNewFile()
        }

        val yaml = YamlConfiguration.loadConfiguration(storeFile)

        for (key in yaml.getKeys(false)) {
            try {
                val uuid = UUID.fromString(key)
                val section = yaml.getConfigurationSection(key)

                if (section == null) {
                    // Legacy format: key -> boolean (only stored whether the sidebar was on)
                    preferences[uuid] = ScoreboardPreference(sidebarEnabled = yaml.getBoolean(key, true))
                    continue
                }

                val sidebarEnabled = section.getBoolean("sidebar-enabled", true)
                val components = ScoreboardComponent.entries
                    .filter { section.getBoolean("components.${it.configKey}", true) }
                    .toSet()

                preferences[uuid] = ScoreboardPreference(sidebarEnabled, components)
            } catch (e: Exception) {
                plugin.logger.warning("Could not load scoreboard preferences for $key: ${e.message}")
            }
        }
    }

    private fun save() {
        val config = YamlConfiguration()
        for ((uuid, pref) in preferences) {
            val key = uuid.toString()
            config.set("$key.sidebar-enabled", pref.sidebarEnabled)
            ScoreboardComponent.entries.forEach {
                config.set("$key.components.${it.configKey}", it in pref.enabledComponents)
            }
        }
        try {
            config.save(storeFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save scoreboard preferences: ${e.message}")
        }
    }
}