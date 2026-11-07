package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.ItemBlob
import de.davidsw.diawars.util.SqlOp
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.sql.Connection
import java.util.UUID

data class EventPlayerInventory(
    val inventory: List<ItemStack?>,
    val armor: List<ItemStack?>,
    val offHand: ItemStack?,
    val enderChest: List<ItemStack?>,
)

class EventInventoryStore(plugin: Diawars) : SqlStore(plugin, "event_player_inventories") {
    private val cache = mutableMapOf<String, MutableMap<UUID, EventPlayerInventory>>()
    private val dirty = DirtyKeys<Pair<String, UUID>>()
    private val clearedEvents = mutableSetOf<String>()

    init {
        migrateLegacyYaml("event_player_inventories.yml", "event_inventories") { yaml -> legacyRows(yaml) }
        load()
    }

    fun saveInventory(eventId: String, player: Player) {
        val perEvent = cache.getOrPut(eventId) { mutableMapOf() }
        perEvent[player.uniqueId] = EventPlayerInventory(
            inventory = player.inventory.storageContents.map { it?.clone() },
            armor = player.inventory.armorContents.map { it?.clone() },
            offHand = player.inventory.itemInOffHand.clone(),
            enderChest = player.enderChest.contents.map { it?.clone() },
        )
        // Custody transfer (leaveWorld/saveActiveInventories): persist synchronously
        // so a crash cannot resurrect a stale snapshot and duplicate inventories.
        dirty.touch(eventId to player.uniqueId)
        flushNow()
    }

    fun getInventory(eventId: String, playerId: UUID) = cache[eventId]?.get(playerId)

    fun restoreInventory(eventId: String, player: Player): Boolean {
        val saved = cache[eventId]?.get(player.uniqueId) ?: return false
        player.inventory.clear()
        player.inventory.storageContents = saved.inventory.map { it?.clone() }.toTypedArray()
        player.inventory.armorContents = saved.armor.map { it?.clone() }.toTypedArray()
        player.inventory.setItemInOffHand(saved.offHand?.clone() ?: ItemStack(Material.AIR))
        player.enderChest.clear()
        player.enderChest.contents = saved.enderChest.map { it?.clone() }.toTypedArray()
        return true
    }

    fun clearEvent(eventId: String) {
        if (cache.remove(eventId) != null) {
            clearedEvents += eventId
            markDirty()
            flushNow()
        }
    }

    override fun loadFrom(connection: Connection) {
        cache.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT event, player, inventory, armor, offhand, enderchest FROM event_inventories",
            ).use { rs ->
                while (rs.next()) {
                    try {
                        val eventId = rs.getString("event")
                        cache.getOrPut(eventId) { mutableMapOf() }[UUID.fromString(rs.getString("player"))] =
                            EventPlayerInventory(
                                inventory = ItemBlob.decodeItems(rs.getBytes("inventory")),
                                armor = ItemBlob.decodeItems(rs.getBytes("armor")),
                                offHand = ItemBlob.decodeItem(rs.getBytes("offhand")),
                                enderChest = ItemBlob.decodeItems(rs.getBytes("enderchest")),
                            )
                    } catch (e: Exception) {
                        plugin.logger.warning(
                            "Could not load event inventory for ${rs.getString("event")}/${rs.getString("player")}: ${e.message}",
                        )
                    }
                }
            }
        }
        plugin.logger.info("Loaded event inventories for ${cache.size} event(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (eventId in clearedEvents) {
            ops += SqlOp("DELETE FROM event_inventories WHERE event = ?", listOf(eventId))
        }
        val cleared = clearedEvents.toSet()
        clearedEvents.clear()

        for ((eventId, playerId) in dirty.drain()) {
            if (eventId in cleared) continue
            val saved = cache[eventId]?.get(playerId)
            if (saved == null) {
                ops += SqlOp(
                    "DELETE FROM event_inventories WHERE event = ? AND player = ?",
                    listOf(eventId, playerId),
                )
            } else {
                ops += SqlOp(
                    "INSERT INTO event_inventories (event, player, inventory, armor, offhand, enderchest) " +
                        "VALUES (?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT(event, player) DO UPDATE SET inventory = excluded.inventory, " +
                        "armor = excluded.armor, offhand = excluded.offhand, enderchest = excluded.enderchest",
                    listOf(
                        eventId, playerId,
                        ItemBlob.encodeItems(saved.inventory),
                        ItemBlob.encodeItems(saved.armor),
                        ItemBlob.encodeItem(saved.offHand),
                        ItemBlob.encodeItems(saved.enderChest),
                    ),
                )
            }
        }
    }

    /** Parses the pre-SQLite `event_player_inventories.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        for (eventId in yaml.getKeys(false)) {
            val eventSection = yaml.getConfigurationSection(eventId) ?: continue
            for (key in eventSection.getKeys(false)) {
                try {
                    val playerId = UUID.fromString(key)
                    val section = eventSection.getConfigurationSection(key) ?: continue

                    @Suppress("UNCHECKED_CAST")
                    val inventory = (section.getList("inventory") as? List<ItemStack?>) ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val armor = (section.getList("armor") as? List<ItemStack?>) ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val enderChest = (section.getList("enderchest") as? List<ItemStack?>) ?: emptyList()

                    ops += SqlOp(
                        "INSERT INTO event_inventories (event, player, inventory, armor, offhand, enderchest) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                        listOf(
                            eventId, playerId,
                            ItemBlob.encodeItems(inventory),
                            ItemBlob.encodeItems(armor),
                            ItemBlob.encodeItem(section.getItemStack("offhand")),
                            ItemBlob.encodeItems(enderChest),
                        ),
                    )
                } catch (e: Exception) {
                    plugin.logger.warning("Could not import event inventory for $eventId/$key: ${e.message}")
                }
            }
        }
        return ops
    }
}
