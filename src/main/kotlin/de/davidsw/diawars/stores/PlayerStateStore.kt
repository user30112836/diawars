package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.ItemBlob
import de.davidsw.diawars.util.SqlOp
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.sql.Connection
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

class PlayerStateStore(plugin: Diawars, fileName: String = "player_states.yml") :
    SqlStore(plugin, fileName.removeSuffix(".yml")) {
    private val cache = mutableMapOf<UUID, PlayerSavedState>()
    private val dirty = DirtyKeys<UUID>()

    init {
        migrateLegacyYaml(fileName, "player_states") { yaml -> legacyRows(yaml) }
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
        // Custody transfer: the live inventory is cleared/teleported right after this
        // call, so persist synchronously. An async write could still be in flight on
        // crash, resurrecting the snapshot and duplicating items on restore.
        dirty.touch(player.uniqueId)
        flushNow()
    }

    fun getState(playerId: UUID) = cache[playerId]

    fun restoreState(player: Player, minimal: Boolean = false): Boolean {
        val state = cache.remove(player.uniqueId) ?: return false
        // Durable removal BEFORE granting items back: a crash after this point must
        // not find the snapshot on disk again (second restore = duplicated items).
        dirty.touch(player.uniqueId)
        flushNow()

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

    override fun loadFrom(connection: Connection) {
        cache.clear()
        connection.prepareStatement(
            "SELECT player, inventory, armor, offhand, enderchest, gamemode, world, " +
                "x, y, z, yaw, pitch, health, food, saturation, exp, level FROM player_states WHERE store = ?",
        ).use { statement ->
            statement.setString(1, storeName)
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    try {
                        cache[UUID.fromString(rs.getString("player"))] = PlayerSavedState(
                            inventory = ItemBlob.decodeItems(rs.getBytes("inventory")),
                            armor = ItemBlob.decodeItems(rs.getBytes("armor")),
                            offHand = ItemBlob.decodeItem(rs.getBytes("offhand")),
                            enderChest = ItemBlob.decodeItems(rs.getBytes("enderchest")),
                            gameMode = GameMode.valueOf(rs.getString("gamemode") ?: "SURVIVAL"),
                            worldName = rs.getString("world") ?: plugin.server.worlds.first().name,
                            x = rs.getDouble("x"),
                            y = rs.getDouble("y"),
                            z = rs.getDouble("z"),
                            yaw = rs.getDouble("yaw").toFloat(),
                            pitch = rs.getDouble("pitch").toFloat(),
                            health = rs.getDouble("health"),
                            foodLevel = rs.getInt("food"),
                            saturation = rs.getDouble("saturation").toFloat(),
                            exp = rs.getDouble("exp").toFloat(),
                            level = rs.getInt("level"),
                        )
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load player state for ${rs.getString("player")}: ${e.message}")
                    }
                }
            }
        }
        plugin.logger.info("Loaded ${cache.size} saved $storeName state(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (playerId in dirty.drain()) {
            val state = cache[playerId]
            if (state == null) {
                ops += SqlOp("DELETE FROM player_states WHERE store = ? AND player = ?", listOf(storeName, playerId))
                continue
            }
            ops += SqlOp(
                "INSERT INTO player_states (store, player, inventory, armor, offhand, enderchest, gamemode, world, " +
                    "x, y, z, yaw, pitch, health, food, saturation, exp, level) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(store, player) DO UPDATE SET inventory = excluded.inventory, armor = excluded.armor, " +
                    "offhand = excluded.offhand, enderchest = excluded.enderchest, gamemode = excluded.gamemode, " +
                    "world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw, " +
                    "pitch = excluded.pitch, health = excluded.health, food = excluded.food, " +
                    "saturation = excluded.saturation, exp = excluded.exp, level = excluded.level",
                listOf(
                    storeName, playerId,
                    ItemBlob.encodeItems(state.inventory),
                    ItemBlob.encodeItems(state.armor),
                    ItemBlob.encodeItem(state.offHand),
                    ItemBlob.encodeItems(state.enderChest),
                    state.gameMode, state.worldName,
                    state.x, state.y, state.z, state.yaw, state.pitch,
                    state.health, state.foodLevel, state.saturation, state.exp, state.level,
                ),
            )
        }
    }

    /** Parses the legacy YAML file into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        for (key in yaml.getKeys(false)) {
            try {
                val playerId = UUID.fromString(key)
                val section = yaml.getConfigurationSection(key) ?: continue

                @Suppress("UNCHECKED_CAST")
                val inventory = (section.getList("inventory") as? List<ItemStack?>) ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val enderChest = section.getList("enderchest") as? List<ItemStack?> ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val armor = (section.getList("armor") as? List<ItemStack?>) ?: emptyList()

                ops += SqlOp(
                    "INSERT INTO player_states (store, player, inventory, armor, offhand, enderchest, gamemode, world, " +
                        "x, y, z, yaw, pitch, health, food, saturation, exp, level) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    listOf(
                        storeName, playerId,
                        ItemBlob.encodeItems(inventory),
                        ItemBlob.encodeItems(armor),
                        ItemBlob.encodeItem(section.getItemStack("offhand")),
                        ItemBlob.encodeItems(enderChest),
                        GameMode.valueOf(section.getString("gamemode") ?: "SURVIVAL"),
                        section.getString("world") ?: plugin.server.worlds.first().name,
                        section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                        section.getDouble("yaw").toFloat(), section.getDouble("pitch").toFloat(),
                        section.getDouble("health", 20.0),
                        section.getInt("food", 20),
                        section.getDouble("saturation", 5.0).toFloat(),
                        section.getDouble("exp", 0.0).toFloat(),
                        section.getInt("level", 0),
                    ),
                )
            } catch (e: Exception) {
                plugin.logger.warning("Could not import player state for $key: ${e.message}")
            }
        }
        return ops
    }
}
