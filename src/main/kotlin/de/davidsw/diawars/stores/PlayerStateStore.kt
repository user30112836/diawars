package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.block.EnderChest
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.UUID

data class PlayerSavedState(
    val inventory: List<ItemStack?>,
    val armor: List<ItemStack?>,
    val offHand: ItemStack?,
    val enderChest: List<ItemStack?>,
    val gameMode: GameMode,
    val worldName: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val health: Double,
    val foodLevel: Int,
    val saturation: Float,
    val exp: Float,
    val level: Int,
)

/**
 * Stores a snapshot of a player's inventory, location and vitals whenever they
 * enter an event world, so it can be fully restored once they leave.
 * Persisted to disk so a server restart while a player is inside an event
 * world can never cause item loss.
 */
class PlayerStateStore(private val plugin: Diawars) {
    private val storeFile = File(plugin.dataFolder, "event_player_states.yml")
    private val cache = mutableMapOf<UUID, PlayerSavedState>()

    init {
        load()
    }

    fun hasSavedState(playerId: UUID): Boolean = cache.containsKey(playerId)

    fun saveState(player: Player) {
        val loc = player.location
        cache[player.uniqueId] = PlayerSavedState(
            inventory = player.inventory.storageContents.toList(),
            armor = player.inventory.armorContents.toList(),
            offHand = player.inventory.itemInOffHand.clone(),
            enderChest = player.enderChest.contents.toList(),
            gameMode = player.gameMode,
            worldName = loc.world?.name ?: plugin.server.worlds.first().name,
            x = loc.x, y = loc.y, z = loc.z,
            yaw = loc.yaw, pitch = loc.pitch,
            health = player.health,
            foodLevel = player.foodLevel,
            saturation = player.saturation,
            exp = player.exp,
            level = player.level,
        )
        flushToDisk()
    }

    fun restoreState(player: Player, minimal: Boolean = false): Boolean {
        val state = cache.remove(player.uniqueId) ?: return false
        flushToDisk()

        if (!minimal) {
            player.inventory.clear()
            player.inventory.storageContents = state.inventory.toTypedArray()
            player.inventory.armorContents = state.armor.toTypedArray()
            player.inventory.setItemInOffHand(state.offHand ?: ItemStack(Material.AIR))
            player.enderChest.clear()
            player.enderChest.contents = state.enderChest.toTypedArray()
            player.gameMode = state.gameMode
        }

        val world = plugin.server.getWorld(state.worldName) ?: plugin.server.worlds.first()
        player.teleport(Location(world, state.x, state.y, state.z, state.yaw, state.pitch))

        if (!minimal) {
            val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
            player.health = state.health.coerceIn(0.0, maxHealth)
            player.foodLevel = state.foodLevel
            player.saturation = state.saturation
            player.exp = state.exp
            player.level = state.level
        }

        return true
    }

    fun clearState(playerId: UUID) {
        cache.remove(playerId)
        flushToDisk()
    }

    private fun flushToDisk() {
        val config = YamlConfiguration()
        for ((uuid, state) in cache) {
            val key = uuid.toString()
            config.set("$key.inventory", state.inventory)
            config.set("$key.armor", state.armor)
            config.set("$key.offhand", state.offHand)
            config.set("$key.enderchest", state.enderChest)
            config.set("$key.gamemode", state.gameMode.name)
            config.set("$key.world", state.worldName)
            config.set("$key.x", state.x)
            config.set("$key.y", state.y)
            config.set("$key.z", state.z)
            config.set("$key.yaw", state.yaw)
            config.set("$key.pitch", state.pitch)
            config.set("$key.health", state.health)
            config.set("$key.food", state.foodLevel)
            config.set("$key.saturation", state.saturation)
            config.set("$key.exp", state.exp)
            config.set("$key.level", state.level)
        }
        try {
            config.save(storeFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save event player states to $storeFile: ${e.message}")
        }
    }

    private fun load() {
        if (!storeFile.exists()) {
            storeFile.parentFile.mkdirs()
            storeFile.createNewFile()
        }

        val yaml = YamlConfiguration.loadConfiguration(storeFile)

        for (key in yaml.getKeys(false)) {
            try {
                val uuid = UUID.fromString(key)
                val section = yaml.getConfigurationSection(key) ?: continue

                @Suppress("UNCHECKED_CAST")
                val inventory = (section.getList("inventory") as? List<ItemStack?>) ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val enderChest = section.getList("enderchest") as? List<ItemStack?> ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val armor = (section.getList("armor") as? List<ItemStack?>) ?: emptyList()

                cache[uuid] = PlayerSavedState(
                    inventory = inventory,
                    armor = armor,
                    offHand = section.getItemStack("offhand"),
                    enderChest = enderChest,
                    gameMode = GameMode.valueOf(section.getString("gamemode") ?: "SURVIVAL"),
                    worldName = section.getString("world") ?: plugin.server.worlds.first().name,
                    x = section.getDouble("x"),
                    y = section.getDouble("y"),
                    z = section.getDouble("z"),
                    yaw = section.getDouble("yaw").toFloat(),
                    pitch = section.getDouble("pitch").toFloat(),
                    health = section.getDouble("health", 20.0),
                    foodLevel = section.getInt("food", 20),
                    saturation = section.getDouble("saturation", 5.0).toFloat(),
                    exp = section.getDouble("exp", 0.0).toFloat(),
                    level = section.getInt("level", 0),
                )
            } catch (e: Exception) {
                plugin.logger.warning("Could not load event player state for $key: ${e.message}")
            }
        }

        plugin.logger.info("Loaded ${cache.size} saved event player state(s).")
    }
}