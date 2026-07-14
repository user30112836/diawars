package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

data class VaultClaim(
    val vaultId: String,
    val owner: UUID,
    val invited: MutableSet<UUID> = mutableSetOf(),
)

class VaultClaimStore(private val plugin: Diawars) {
    private val storeFile = File(plugin.dataFolder, "vault_claims.yml")
    private val claimsByVault = mutableMapOf<String, VaultClaim>()

    init {
        load()
    }

    fun getClaim(vaultId: String): VaultClaim? = claimsByVault[vaultId]

    fun getClaimByOwner(playerId: UUID): VaultClaim? = claimsByVault.values.firstOrNull { it.owner == playerId }

    fun isVaultClaimed(vaultId: String): Boolean = claimsByVault.containsKey(vaultId)

    fun hasClaimedAnyVault(playerId: UUID): Boolean = getClaimByOwner(playerId) != null

    fun claim(vaultId: String, playerId: UUID) {
        claimsByVault[vaultId] = VaultClaim(vaultId, playerId)
        save()
    }

    fun unclaim(vaultId: String) {
        claimsByVault.remove(vaultId)
        save()
    }

    fun invite(vaultId: String, playerId: UUID) {
        claimsByVault[vaultId]?.invited?.add(playerId)
        save()
    }

    fun ban(vaultId: String, playerId: UUID) {
        claimsByVault[vaultId]?.invited?.remove(playerId)
        save()
    }

    fun canPlace(vaultId: String, playerId: UUID): Boolean {
        val claim = claimsByVault[vaultId] ?: return false
        return claim.owner == playerId || playerId in claim.invited
    }

    private fun load() {
        if (!storeFile.exists()) {
            storeFile.parentFile.mkdirs()
            storeFile.createNewFile()
        }

        val yaml = YamlConfiguration.loadConfiguration(storeFile)

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

    private fun save() {
        val yaml = YamlConfiguration()
        for ((vaultId, claim) in claimsByVault) {
            yaml.set("$vaultId.owner", claim.owner.toString())
            yaml.set("$vaultId.invited", claim.invited.map { it.toString() })
        }
        try {
            yaml.save(storeFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save vault claims to $storeFile: ${e.message}")
        }
    }
}