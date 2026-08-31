package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

class OnboardingStore(plugin: Diawars) : YamlStore(plugin, "onboarding.yml") {
    private val cache = mutableMapOf<UUID, Boolean>()

    init {
        load()
    }

    fun hasEnteredMainWorld(playerId: UUID): Boolean = cache.getOrDefault(playerId, false)

    fun markEnteredMainWorld(playerId: UUID) {
        cache[playerId] = true
        markDirty()
    }

    override fun writeTo(yaml: YamlConfiguration) {
        for ((uuid, value) in cache) {
            yaml.set(uuid.toString(), value)
        }
    }

    override fun readFrom(yaml: YamlConfiguration) {
        for (key in yaml.getKeys(false)) {
            try {
                cache[UUID.fromString(key)] = yaml.getBoolean(key)
            } catch (e: Exception) {
                plugin.logger.warning("Could not load onboarding state for $key: ${e.message}")
            }
        }

        plugin.logger.info("Loaded onboarding state for ${cache.size} player(s).")
    }
}
