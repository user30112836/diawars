package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

class OnboardingStore(plugin: Diawars) : YamlStore(plugin, "onboarding.yml") {
    private val enteredCache = mutableMapOf<UUID, Boolean>()
    private val starterCache = mutableMapOf<UUID, Boolean>()

    init {
        load()
    }

    fun hasEnteredMainWorld(playerId: UUID): Boolean = enteredCache.getOrDefault(playerId, false)

    fun markEnteredMainWorld(playerId: UUID) {
        enteredCache[playerId] = true
        markDirty()
    }

    /** One-time starter diamonds (granted on first join). Persisted separately so a
     * rejoin before leaving the lobby cannot grant them twice. */
    fun hasReceivedStarterDiamonds(playerId: UUID): Boolean = starterCache.getOrDefault(playerId, false)

    fun markStarterDiamondsReceived(playerId: UUID) {
        starterCache[playerId] = true
        markDirty()
    }

    override fun writeTo(yaml: YamlConfiguration) {
        for ((uuid, value) in enteredCache) {
            yaml.set(uuid.toString(), value)
        }
        for ((uuid, value) in starterCache) {
            yaml.set("starter-reward.$uuid", value)
        }
    }

    override fun readFrom(yaml: YamlConfiguration) {
        for (key in yaml.getKeys(false)) {
            if (key == "starter-reward") continue
            try {
                val uuid = UUID.fromString(key)
                if (yaml.isConfigurationSection(key)) {
                    // Forward-compatible: per-player section instead of a plain boolean.
                    val section = yaml.getConfigurationSection(key) ?: continue
                    enteredCache[uuid] = section.getBoolean("entered", section.getBoolean("entered-main-world", false))
                    if (section.getBoolean("starter", section.getBoolean("starter-reward", false))) {
                        starterCache[uuid] = true
                    }
                } else {
                    enteredCache[uuid] = yaml.getBoolean(key)
                }
            } catch (e: Exception) {
                plugin.logger.warning("Could not load onboarding state for $key: ${e.message}")
            }
        }
        yaml.getConfigurationSection("starter-reward")?.let { section ->
            for (id in section.getKeys(false)) {
                try {
                    if (section.getBoolean(id)) starterCache[UUID.fromString(id)] = true
                } catch (e: Exception) {
                    plugin.logger.warning("Could not load starter reward state for $id: ${e.message}")
                }
            }
        }

        plugin.logger.info("Loaded onboarding state for ${enteredCache.size} player(s), ${starterCache.size} starter reward(s).")
    }
}
