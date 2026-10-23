package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.Team
import org.bukkit.configuration.file.YamlConfiguration

class VaultDiamondStore(plugin: Diawars) : YamlStore(plugin, "vault_diamonds.yml") {
    private val cache = mutableMapOf<String, Int>()
    private val teamTotals = mutableMapOf<Team, Int>()
    private var teamTotalsInitialized = false

    init {
        load()
    }

    fun getVaultCount(vaultId: String): Int = cache.getOrDefault(vaultId, 0)

    fun addDiamonds(vaultId: String, amount: Int) {
        cache[vaultId] = (cache.getOrDefault(vaultId, 0) + amount).coerceAtLeast(0)
        teamTotalsInitialized = false
        markDirty()
    }

    fun removeDiamonds(vaultId: String, amount: Int) {
        cache[vaultId] = (cache.getOrDefault(vaultId, 0) - amount).coerceAtLeast(0)
        teamTotalsInitialized = false
        markDirty()
    }

    fun getTeamTotal(team: Team): Int {
        if (!teamTotalsInitialized) rebuildTeamTotals()
        return teamTotals.getOrDefault(team, 0)
    }

    private fun rebuildTeamTotals() {
        teamTotals.clear()
        teamTotalsInitialized = true
        for (team in Team.entries) {
            teamTotals[team] = plugin.vaultManager.getVaultsForTeam(team).sumOf { getVaultCount(it.id) }
        }
    }

    override fun readFrom(yaml: YamlConfiguration) {
        for (vaultId in yaml.getKeys(false)) {
            cache[vaultId] = yaml.getInt(vaultId, 0)
        }

        teamTotalsInitialized = false
        plugin.logger.info("Loaded diamond counts for ${cache.size} vault(s).")
    }

    override fun writeTo(yaml: YamlConfiguration) {
        for ((vaultId, count) in cache) {
            yaml.set(vaultId, count)
        }
    }
}
