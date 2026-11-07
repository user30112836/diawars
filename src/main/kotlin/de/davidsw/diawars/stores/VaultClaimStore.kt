package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.JsonColumn
import de.davidsw.diawars.util.SqlOp
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID

data class VaultClaim(
    val vaultId: String,
    val owner: UUID,
    val invited: MutableSet<UUID> = mutableSetOf(),
)

class VaultClaimStore(plugin: Diawars) : SqlStore(plugin, "vault_claims") {
    private val claimsByVault = mutableMapOf<String, VaultClaim>()
    private val claimByOwner = mutableMapOf<UUID, VaultClaim>()
    private val dirty = DirtyKeys<String>()

    init {
        migrateLegacyYaml("vault_claims.yml", "vault_claims") { yaml -> legacyRows(yaml) }
        load()
    }

    fun getClaim(vaultId: String): VaultClaim? = claimsByVault[vaultId]
    fun getClaimByOwner(playerId: UUID): VaultClaim? = claimByOwner[playerId]
    fun getClaimsInvitedTo(playerId: UUID): List<VaultClaim> = claimsByVault.values.filter { playerId in it.invited }

    fun isVaultClaimed(vaultId: String): Boolean = claimsByVault.containsKey(vaultId)

    fun hasClaimedAnyVault(playerId: UUID): Boolean = getClaimByOwner(playerId) != null

    fun claim(vaultId: String, playerId: UUID) {
        val c = VaultClaim(vaultId, playerId)
        claimsByVault[vaultId] = c
        claimByOwner[playerId] = c
        dirty.touch(vaultId)
        saveImmediately()
    }

    fun unclaim(vaultId: String) {
        val c = claimsByVault.remove(vaultId)
        if (c != null) claimByOwner.remove(c.owner)
        dirty.touch(vaultId)
        saveImmediately()
    }

    fun invite(vaultId: String, playerId: UUID) {
        claimsByVault[vaultId]?.invited?.add(playerId)
        dirty.touch(vaultId)
        saveImmediately()
    }

    fun ban(vaultId: String, playerId: UUID) {
        claimsByVault[vaultId]?.invited?.remove(playerId)
        dirty.touch(vaultId)
        saveImmediately()
    }

    fun canPlace(vaultId: String, playerId: UUID): Boolean {
        val claim = claimsByVault[vaultId] ?: return false
        return claim.owner == playerId || playerId in claim.invited
    }

    override fun loadFrom(connection: Connection) {
        claimsByVault.clear()
        claimByOwner.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT vault, owner, invited FROM vault_claims").use { rs ->
                while (rs.next()) {
                    try {
                        val vaultId = rs.getString("vault")
                        val owner = UUID.fromString(rs.getString("owner"))
                        val invited = JsonColumn.strings(rs.getString("invited"))
                            .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
                            .toMutableSet()
                        val claim = VaultClaim(vaultId, owner, invited)
                        claimsByVault[vaultId] = claim
                        claimByOwner[owner] = claim
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load vault claim for ${rs.getString("vault")}: ${e.message}")
                    }
                }
            }
        }
        plugin.logger.info("Loaded ${claimsByVault.size} vault claim(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (vaultId in dirty.drain()) {
            val claim = claimsByVault[vaultId]
            if (claim == null) {
                ops += SqlOp("DELETE FROM vault_claims WHERE vault = ?", listOf(vaultId))
            } else {
                ops += SqlOp(
                    "INSERT INTO vault_claims (vault, owner, invited) VALUES (?, ?, ?) " +
                        "ON CONFLICT(vault) DO UPDATE SET owner = excluded.owner, invited = excluded.invited",
                    listOf(
                        vaultId,
                        claim.owner,
                        JsonColumn.encode(claim.invited.map { it.toString() }),
                    ),
                )
            }
        }
    }

    /** Parses the pre-SQLite `vault_claims.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        for (vaultId in yaml.getKeys(false)) {
            try {
                val section = yaml.getConfigurationSection(vaultId) ?: continue
                val owner = section.getString("owner") ?: continue
                val invited = section.getStringList("invited")
                    .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
                ops += SqlOp(
                    "INSERT INTO vault_claims (vault, owner, invited) VALUES (?, ?, ?)",
                    listOf(vaultId, UUID.fromString(owner), JsonColumn.encode(invited.map { it.toString() })),
                )
            } catch (e: Exception) {
                plugin.logger.warning("Could not import vault claim for $vaultId: ${e.message}")
            }
        }
        return ops
    }
}
