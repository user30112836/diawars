package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.SqlOp
import de.davidsw.diawars.managers.Team
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection

class VaultDiamondStore(plugin: Diawars) : SqlStore(plugin, "vault_diamonds") {
    private val cache = mutableMapOf<String, Int>()
    private val teamTotals = mutableMapOf<Team, Int>()
    private var teamTotalsInitialized = false
    private val dirty = DirtyKeys<String>()

    init {
        migrateLegacyYaml("vault_diamonds.yml", "vault_diamonds") { yaml -> legacyRows(yaml) }
        load()
    }

    fun getVaultCount(vaultId: String): Int = cache.getOrDefault(vaultId, 0)

    fun addDiamonds(vaultId: String, amount: Int) {
        cache[vaultId] = (cache.getOrDefault(vaultId, 0) + amount).coerceAtLeast(0)
        teamTotalsInitialized = false
        dirty.touch(vaultId)
        markDirty()
    }

    fun removeDiamonds(vaultId: String, amount: Int) {
        cache[vaultId] = (cache.getOrDefault(vaultId, 0) - amount).coerceAtLeast(0)
        teamTotalsInitialized = false
        dirty.touch(vaultId)
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

    override fun loadFrom(connection: Connection) {
        cache.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT vault, diamonds FROM vault_diamonds").use { rs ->
                while (rs.next()) {
                    cache[rs.getString("vault")] = rs.getInt("diamonds")
                }
            }
        }
        teamTotalsInitialized = false
        plugin.logger.info("Loaded diamond counts for ${cache.size} vault(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (vaultId in dirty.drain()) {
            val count = cache[vaultId]
            if (count == null || count == 0) {
                ops += SqlOp("DELETE FROM vault_diamonds WHERE vault = ?", listOf(vaultId))
            } else {
                ops += SqlOp(
                    "INSERT INTO vault_diamonds (vault, diamonds) VALUES (?, ?) " +
                        "ON CONFLICT(vault) DO UPDATE SET diamonds = excluded.diamonds",
                    listOf(vaultId, count),
                )
            }
        }
    }

    /** Parses the pre-SQLite `vault_diamonds.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        for (vaultId in yaml.getKeys(false)) {
            val count = yaml.getInt(vaultId, 0)
            if (count == 0) continue
            ops += SqlOp("INSERT INTO vault_diamonds (vault, diamonds) VALUES (?, ?)", listOf(vaultId, count))
        }
        return ops
    }
}
