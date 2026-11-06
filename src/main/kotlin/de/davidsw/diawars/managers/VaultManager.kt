package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.ConfigFiles
import de.davidsw.diawars.util.MaterialSets.VAULT_UNDERGROUND
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player

/**
 * A vault is a 2D (X/Z) area spanning the full world height; Y is not part of
 * the model any more. Old vaults.yml entries may still carry corner Y values,
 * they are simply ignored.
 */
data class VaultRegion(
    val id: String,
    val displayName: String,
    val world: String,
    val team: Team,
    val minX: Int, val minZ: Int,
    val maxX: Int, val maxZ: Int,
) {
    fun contains(location: Location): Boolean {
        val world = location.world ?: return false
        if (world.name != this.world) return false
        return location.blockX in minX..maxX && location.blockZ in minZ..maxZ
    }
}

class VaultManager(private val plugin: Diawars) {
    private var regions: List<VaultRegion> = emptyList()
    private val vaultsFile = ConfigFiles.resolve(plugin, "vaults.yml")

    init {
        loadFromConfig()
    }

    fun loadFromConfig() {
        val list = mutableListOf<VaultRegion>()
        val section = YamlConfiguration.loadConfiguration(vaultsFile)

        for (id in section.getKeys(false)) {
            try {
                val vaultSection = section.getConfigurationSection(id) ?: continue
                val world = vaultSection.getString("world") ?: "world"
                val teamKey = vaultSection.getString("team") ?: continue
                val team = Team.entries.firstOrNull { it.configKey == teamKey }
                    ?: run {
                        plugin.logger.warning("Vault '$id' has unknown team '$teamKey'")
                        continue
                    }
                val displayName = vaultSection.getString("display-name") ?: id
                val c1 = vaultSection.getConfigurationSection("corner1") ?: continue
                val c2 = vaultSection.getConfigurationSection("corner2") ?: continue

                val x1 = c1.getInt("x"); val z1 = c1.getInt("z")
                val x2 = c2.getInt("x"); val z2 = c2.getInt("z")

                list += VaultRegion(
                    id = id,
                    displayName = displayName,
                    world = world,
                    team = team,
                    minX = minOf(x1, x2), maxX = maxOf(x1, x2),
                    minZ = minOf(z1, z2), maxZ = maxOf(z1, z2),
                )
            } catch (e: Exception) {
                plugin.logger.warning("Could not load vault '$id': ${e.message}")
            }
        }

        regions = list
        sortedVaultsCache = null
        plugin.logger.info("Loaded ${regions.size} vault region(s).")
    }

    private var sortedVaultsCache: List<VaultRegion>? = null
    fun getAllVaults(): List<VaultRegion> = regions
    fun getSortedVaults(): List<VaultRegion> {
        sortedVaultsCache?.let { return it }
        val sorted = regions.sortedWith(compareBy({ it.team.configKey }, { it.id }))
        sortedVaultsCache = sorted
        return sorted
    }
    fun getVaultAt(location: Location): VaultRegion? = regions.firstOrNull { it.contains(location) }
    fun getVaultsForTeam(team: Team): List<VaultRegion> = regions.filter { it.team == team }

    fun isValidPlacementSpot(location: Location): VaultRegion? {
        val vault = getVaultAt(location) ?: return null
        val below = location.clone().add(0.0, -1.0, 0.0).block
        if (!VAULT_UNDERGROUND.contains(below.type)) return null
        return vault
    }

    fun getVaultById(id: String): VaultRegion? = regions.firstOrNull { it.id == id }

    fun findNearbyVault(location: Location): VaultRegion? {
        val maxDistance = plugin.config.getDouble("vault.claim-distance", 5.0)
        val world = location.world?.name ?: return null
        return regions
            .filter { it.world == world }
            .firstOrNull { distanceToRegion(it, location) <= maxDistance }
    }

    private fun distanceToRegion(region: VaultRegion, location: Location): Double {
        val x = location.x.coerceIn(region.minX.toDouble(), region.maxX + 1.0)
        val z = location.z.coerceIn(region.minZ.toDouble(), region.maxZ + 1.0)
        return location.distance(Location(location.world, x, location.y, z))
    }


    fun removeVault(id: String): Boolean {
        val config = YamlConfiguration.loadConfiguration(vaultsFile)
        if (!config.contains(id)) return false
        config.set(id, null)
        config.save(vaultsFile)
        loadFromConfig()
        return true
    }

    fun setVaultField(id: String, field: String, raw: String): String {
        val config = YamlConfiguration.loadConfiguration(vaultsFile)
        if (!config.contains(id)) return "Vault '$id' existiert nicht."
        val allowed = setOf("display-name", "world", "team", "corner1.x", "corner1.z", "corner2.x", "corner2.z")
        if (field !in allowed) return "Unbekanntes Feld: $field"
        val value: Any = when {
            field == "team" -> {
                val team = Team.entries.firstOrNull { it.configKey == raw } ?: return "Unbekanntes Team: $raw"
                team.configKey
            }
            field.endsWith(".x") || field.endsWith(".z") -> raw.toIntOrNull() ?: return "Ungueltiger Integer: $raw"
            else -> raw
        }
        config.set("$id.$field", value)
        config.save(vaultsFile)
        loadFromConfig()
        return "OK $id.$field = $value"
    }

    /**
     * Creates a vault for [teamKey] as an 11x11 block area centred on the
     * player's current block position (full blocks, no fractions). The whole
     * 11x11 area one block below the player is replaced with bedrock so the
     * vault has a floor to place diamond blocks on. The id and display name
     * are generated per team: {@code team-a-vault-3} / "Vault A3".
     */
    fun addVaultAt(player: Player, teamKey: String): String {
        val team = Team.entries.firstOrNull { it.configKey == teamKey } ?: return "Unbekanntes Team: $teamKey"
        val config = YamlConfiguration.loadConfiguration(vaultsFile)

        val teamVaults = regions.filter { it.team == team }
        var number = (teamVaults.mapNotNull { trailingNumber(it.id) } + teamVaults.mapNotNull { trailingNumber(it.displayName) })
            .maxOrNull()?.plus(1) ?: 1
        var id = "${team.configKey}-vault-$number"
        while (config.contains(id)) {
            number++
            id = "${team.configKey}-vault-$number"
        }

        val letter = when (team) {
            Team.TEAM_A -> "A"
            Team.TEAM_B -> "B"
        }
        val displayName = "Vault $letter$number"

        val blockX = player.location.blockX
        val blockZ = player.location.blockZ
        val minX = blockX - 5
        val maxX = blockX + 5
        val minZ = blockZ - 5
        val maxZ = blockZ + 5

        config.set("$id.display-name", displayName)
        config.set("$id.world", player.world.name)
        config.set("$id.team", team.configKey)
        config.set("$id.corner1.x", minX)
        config.set("$id.corner1.z", minZ)
        config.set("$id.corner2.x", maxX)
        config.set("$id.corner2.z", maxZ)
        config.save(vaultsFile)

        // Build the vault floor: replace the 11x11 area at player Y - 1 with
        // bedrock so diamond blocks can be placed on it (VAULT_UNDERGROUND).
        val world = player.world
        val floorY = (player.location.blockY - 1).coerceAtLeast(world.minHeight)
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                world.getBlockAt(x, floorY, z).setType(Material.BEDROCK, false)
            }
        }

        loadFromConfig()

        return "OK Vault <white>$id</white> ($displayName) erstellt: ${player.world.name} X $minX..$maxX, Z $minZ..$maxZ, Boden auf Y $floorY aus Bedrock."
    }

    /** Trailing digits of an id/name ("team-a-vault-3" / "Vault A3" -> 3). */
    private fun trailingNumber(value: String): Int? =
        Regex("(\\d+)$").find(value)?.groupValues?.get(1)?.toIntOrNull()
}