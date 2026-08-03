package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.Team
import de.davidsw.diawars.util.StoreFiles
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class VaultDiamondStore(private val plugin: Diawars) {
    private val storeFile = StoreFiles.resolve(plugin, "vault_diamonds.yml")
    private val cache = mutableMapOf<String, Int>()

    init {
        load()
    }

    fun getVaultCount(vaultId: String): Int = cache.getOrDefault(vaultId, 0)

    fun addDiamonds(vaultId: String, amount: Int) {
        cache[vaultId] = (cache.getOrDefault(vaultId, 0) + amount).coerceAtLeast(0)
        flushToDisk()
    }

    fun removeDiamonds(vaultId: String, amount: Int) {
        cache[vaultId] = (cache.getOrDefault(vaultId, 0) - amount).coerceAtLeast(0)
        flushToDisk()
    }

    fun getTeamTotal(team: Team): Int {
        return plugin.vaultManager.getVaultsForTeam(team).sumOf { getVaultCount(it.id) }
    }

    private fun load() {
        if (!storeFile.exists()) {
            storeFile.parentFile.mkdirs()
            storeFile.createNewFile()
        }

        val yaml = YamlConfiguration.loadConfiguration(storeFile)
        for (vaultId in yaml.getKeys(false)) {
            cache[vaultId] = yaml.getInt(vaultId, 0)
        }

        plugin.logger.info("Loaded diamond counts for ${cache.size} vault(s).")
    }

    private fun flushToDisk() {
        val yaml = YamlConfiguration()
        for ((vaultId, count) in cache) {
            yaml.set(vaultId, count)
        }
        try {
            yaml.save(storeFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save vault diamonds to $storeFile: ${e.message}")
        }
    }
}