package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

class ScoreboardPreferencesStore(private val plugin: Diawars) {
    private val storeFile = File(plugin.dataFolder, "scoreboard_preferences.yml")
    private val cache = mutableMapOf<UUID, Boolean>()

    init { load() }

    fun isSidebarEnabled(playerId: UUID): Boolean = cache.getOrDefault(playerId, true)

    fun setSidebarEnabled(playerId: UUID, enabled: Boolean) {
        cache[playerId] = enabled
        save()
    }

    private fun load() {
        if (!storeFile.exists()) { storeFile.parentFile.mkdirs(); storeFile.createNewFile() }
        val yaml = YamlConfiguration.loadConfiguration(storeFile)
        for (key in yaml.getKeys(false)) {
            try { cache[UUID.fromString(key)] = yaml.getBoolean(key) } catch (_: Exception) {}
        }
    }

    private fun save() {
        val config = YamlConfiguration()
        for ((uuid, enabled) in cache) config.set(uuid.toString(), enabled)
        try { config.save(storeFile) } catch (e: Exception) {
            plugin.logger.severe("Could not save scoreboard preferences: ${e.message}")
        }
    }
}