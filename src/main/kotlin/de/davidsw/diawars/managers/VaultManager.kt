package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MaterialSets.VAULT_UNDERGROUND
import org.bukkit.Location
import org.bukkit.Material

data class VaultRegion(
    val id: String,
    val world: String,
    val team: Team,
    val minX: Int, val minY: Int, val minZ: Int,
    val maxX: Int, val maxZ: Int,
) {
    fun contains(location: Location): Boolean {
        val world = location.world ?: return false
        if (world.name != this.world) return false
        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ
        return x in minX..maxX && y >= minY && z in minZ..maxZ
    }
}

class VaultManager(private val plugin: Diawars) {
    private var regions: List<VaultRegion> = emptyList()

    init {
        loadFromConfig()
    }

    fun loadFromConfig() {
        val list = mutableListOf<VaultRegion>()
        val section = plugin.config.getConfigurationSection("vaults")

        if (section != null) {
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
                    val c1 = vaultSection.getConfigurationSection("corner1") ?: continue
                    val c2 = vaultSection.getConfigurationSection("corner2") ?: continue

                    val x1 = c1.getInt("x"); val y1 = c1.getInt("y"); val z1 = c1.getInt("z")
                    val x2 = c2.getInt("x"); val y2 = c2.getInt("y"); val z2 = c2.getInt("z")

                    list += VaultRegion(
                        id = id,
                        world = world,
                        team = team,
                        minX = minOf(x1, x2), maxX = maxOf(x1, x2),
                        minY = minOf(y1, y2),
                        minZ = minOf(z1, z2), maxZ = maxOf(z1, z2),
                    )
                } catch (e: Exception) {
                    plugin.logger.warning("Could not load vault '$id': ${e.message}")
                }
            }
        }

        regions = list
        plugin.logger.info("Loaded ${regions.size} vault region(s).")
    }

    fun getVaultAt(location: Location): VaultRegion? = regions.firstOrNull { it.contains(location) }

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
        val y = location.y.coerceIn(region.minY.toDouble(), Double.POSITIVE_INFINITY)
        val z = location.z.coerceIn(region.minZ.toDouble(), region.maxZ + 1.0)
        return location.distance(Location(location.world, x, y, z))
    }
}