package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.Team
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class VaultStore(private val plugin: Diawars) {
    private val storeFile = File(plugin.dataFolder, "vault_diamonds.yml")
    private val cache = mutableMapOf<Team, Int>()

    init {
        load()
    }

    fun getVaultCount(team: Team): Int = cache.getOrDefault(team, 0)

    fun addDiamonds(team: Team, amount: Int) {
        cache[team] = (cache.getOrDefault(team, 0) + amount).coerceAtLeast(0)
        flushToDisk()
    }

    fun removeDiamonds(team: Team, amount: Int) {
        cache[team] = (cache.getOrDefault(team, 0) - amount).coerceAtLeast(0)
        flushToDisk()
    }

    private fun load() {
        if (!storeFile.exists()) {
            storeFile.parentFile.mkdirs()
            storeFile.createNewFile()
        }

        val yaml = YamlConfiguration.loadConfiguration(storeFile)
        for (team in Team.entries) {
            cache[team] = yaml.getInt(team.configKey, 0)
        }

        plugin.logger.info("Loaded vault diamond counts.")
    }

    private fun flushToDisk() {
        val yaml = YamlConfiguration()
        for ((team, count) in cache) {
            yaml.set(team.configKey, count)
        }
        try {
            yaml.save(storeFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save vault diamonds to $storeFile: ${e.message}")
        }
    }
}