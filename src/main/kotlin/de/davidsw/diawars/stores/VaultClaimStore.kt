package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

data class VaultClaim(
    val vaultId: String,
    val owner: UUID,
    val invited: MutableSet<UUID> = mutableSetOf(),
)

class VaultClaimStore(plugin: Diawars) : YamlStore(plugin, "vault_claims.yml") {
    private val claimsByVault = mutableMapOf<String, VaultClaim>()

    init {
        load()
    }

    fun getClaim(vaultId: String): VaultClaim? = claimsByVault[vaultId]
    fun getClaimByOwner(playerId: UUID): VaultClaim? = claimsByVault.values.firstOrNull { it.owner == playerId }
    fun getClaimsInvitedTo(playerId: UUID):  List<VaultClaim> = claimsByVault.values.filter { playerId in it.invited }

    fun isVaultClaimed(vaultId: String): Boolean = claimsByVault.containsKey(vaultId)

    fun hasClaimedAnyVault(playerId: UUID): Boolean = getClaimByOwner(playerId) != null

    fun claim(vaultId: String, playerId: UUID) {
        claimsByVault[vaultId] = VaultClaim(vaultId, playerId)
        saveImmediately()
    }

    fun unclaim(vaultId: String) {
        claimsByVault.remove(vaultId)
        saveImmediately()
    }

    fun invite(vaultId: String, playerId: UUID) {
        claimsByVault[vaultId]?.invited?.add(playerId)
        saveImmediately()
    }

    fun ban(vaultId: String, playerId: UUID) {
        claimsByVault[vaultId]?.invited?.remove(playerId)
        saveImmediately()
    }

    fun canPlace(vaultId: String, playerId: UUID): Boolean {
        val claim = claimsByVault[vaultId] ?: return false
        return claim.owner == playerId || playerId in claim.invited
    }

    override fun readFrom(yaml: YamlConfiguration) {
        for (vaultId in yaml.getKeys(false)) {
            try {
                val section = yaml.getConfigurationSection(vaultId) ?: continue
                val ownerString = section.getString("owner") ?: continue
                val owner = UUID.fromString(ownerString)
                val invited = section.getStringList("invited")
                    .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
                    .toMutableSet()
                claimsByVault[vaultId] = VaultClaim(vaultId, owner, invited)
            } catch (e: Exception) {
                plugin.logger.warning("Could not load vault claim for $vaultId: ${e.message}")
            }
        }

        plugin.logger.info("Loaded ${claimsByVault.size} vault claim(s).")
    }

    override fun writeTo(yaml: YamlConfiguration) {
        for ((vaultId, claim) in claimsByVault) {
            yaml.set("$vaultId.owner", claim.owner.toString())
            yaml.set("$vaultId.invited", claim.invited.map { it.toString() })
        }
    }
}
