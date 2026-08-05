package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.StoreFiles
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

class OnboardingStore(private val plugin: Diawars) {
    private val storeFile = StoreFiles.resolve(plugin, "onboarding.yml")
    private val cache = mutableMapOf<UUID, Boolean>()

    init {
        load()
    }

    fun hasEnteredMainWorld(playerId: UUID): Boolean = cache.getOrDefault(playerId, false)

    fun markEnteredMainWorld(playerId: UUID) {
        cache[playerId] = true
        save()
    }

    private fun save() {
        val config = YamlConfiguration()
        for ((uuid, value) in cache) {
            config.set(uuid.toString(), value)
        }
        try {
            config.save(storeFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save onboarding state to $storeFile: ${e.message}")
        }
    }

    private fun load() {
        if (!storeFile.exists()) {
            storeFile.parentFile.mkdirs()
            storeFile.createNewFile()
        }

        val yaml = YamlConfiguration.loadConfiguration(storeFile)

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