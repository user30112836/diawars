package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
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

class PlayerStateStore(plugin: Diawars, fileName: String = "player_states.yml") : YamlStore(plugin, fileName) {
    private val cache = mutableMapOf<UUID, PlayerSavedState>()

    init {
        load()
    }

    fun hasSavedState(playerId: UUID): Boolean = cache.containsKey(playerId)

    fun saveState(player: Player) {
        val loc = player.location
        cache[player.uniqueId] = PlayerSavedState(
            inventory = player.inventory.storageContents.map { it?.clone() },
            armor = player.inventory.armorContents.map { it?.clone() },
            offHand = player.inventory.itemInOffHand.clone(),
            enderChest = player.enderChest.contents.map { it?.clone() },
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
        saveImmediately()
    }

    fun getState(playerId: UUID) = cache[playerId]

    fun restoreState(player: Player, minimal: Boolean = false): Boolean {
        val state = cache.remove(player.uniqueId) ?: return false
        saveImmediately()

        if (!minimal) {
            player.inventory.clear()
            player.inventory.storageContents = state.inventory.map { it?.clone() }.toTypedArray()
            player.inventory.armorContents = state.armor.map { it?.clone() }.toTypedArray()
            player.inventory.setItemInOffHand(state.offHand?.clone() ?: ItemStack(Material.AIR))
            player.enderChest.clear()
            player.enderChest.contents = state.enderChest.map { it?.clone() }.toTypedArray()
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
        saveImmediately()
    }

    override fun writeTo(yaml: YamlConfiguration) {
        for ((uuid, state) in cache) {
            val key = uuid.toString()
            yaml.set("$key.inventory", state.inventory)
            yaml.set("$key.armor", state.armor)
            yaml.set("$key.offhand", state.offHand)
            yaml.set("$key.enderchest", state.enderChest)
            yaml.set("$key.gamemode", state.gameMode.name)
            yaml.set("$key.world", state.worldName)
            yaml.set("$key.x", state.x)
            yaml.set("$key.y", state.y)
            yaml.set("$key.z", state.z)
            yaml.set("$key.yaw", state.yaw)
            yaml.set("$key.pitch", state.pitch)
            yaml.set("$key.health", state.health)
            yaml.set("$key.food", state.foodLevel)
            yaml.set("$key.saturation", state.saturation)
            yaml.set("$key.exp", state.exp)
            yaml.set("$key.level", state.level)
        }
    }

    override fun readFrom(yaml: YamlConfiguration) {
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
