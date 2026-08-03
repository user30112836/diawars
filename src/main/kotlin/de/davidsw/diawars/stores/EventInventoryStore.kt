package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.StoreFiles
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

data class EventPlayerInventory(
    val inventory: List<ItemStack?>,
    val armor: List<ItemStack?>,
    val offHand: ItemStack?,
    val enderChest: List<ItemStack?>,
)

class EventInventoryStore(private val plugin: Diawars) {
    private val storeFile = StoreFiles.resolve(plugin, "event_player_inventories.yml")
    private val cache = mutableMapOf<String, MutableMap<UUID, EventPlayerInventory>>()

    init {
        load()
    }

    fun saveInventory(eventId: String, player: Player) {
        val perEvent = cache.getOrPut(eventId) { mutableMapOf() }
        perEvent[player.uniqueId] = EventPlayerInventory(
            inventory = player.inventory.storageContents.toList(),
            armor = player.inventory.armorContents.toList(),
            offHand = player.inventory.itemInOffHand.clone(),
            enderChest = player.enderChest.contents.toList(),
        )
        flushToDisk()
    }

    fun restoreInventory(eventId: String, player: Player): Boolean {
        val saved = cache[eventId]?.get(player.uniqueId) ?: return false
        player.inventory.clear()
        player.inventory.storageContents = saved.inventory.toTypedArray()
        player.inventory.armorContents = saved.armor.toTypedArray()
        player.inventory.setItemInOffHand(saved.offHand ?: ItemStack(Material.AIR))
        player.enderChest.clear()
        player.enderChest.contents = saved.enderChest.toTypedArray()
        return true
    }

    fun clearEvent(eventId: String) {
        if (cache.remove(eventId) != null) flushToDisk()
    }

    private fun flushToDisk() {
        val config = YamlConfiguration()
        for ((eventId, players) in cache) {
            for ((uuid, inv) in players) {
                val key = "$eventId.$uuid"
                config.set("$key.inventory", inv.inventory)
                config.set("$key.armor", inv.armor)
                config.set("$key.offhand", inv.offHand)
                config.set("$key.enderchest", inv.enderChest)
            }
        }
        try {
            config.save(storeFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save event player inventories to $storeFile: ${e.message}")
        }
    }

    private fun load() {
        if (!storeFile.exists()) {
            storeFile.parentFile.mkdirs()
            storeFile.createNewFile()
        }

        val yaml = YamlConfiguration.loadConfiguration(storeFile)

        for (eventId in yaml.getKeys(false)) {
            val eventSection = yaml.getConfigurationSection(eventId) ?: continue
            val perEvent = mutableMapOf<UUID, EventPlayerInventory>()

            for (key in eventSection.getKeys(false)) {
                try {
                    val uuid = UUID.fromString(key)
                    val section = eventSection.getConfigurationSection(key) ?: continue

                    @Suppress("UNCHECKED_CAST")
                    val inventory = (section.getList("inventory") as? List<ItemStack?>) ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val armor = (section.getList("armor") as? List<ItemStack?>) ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val enderChest = (section.getList("enderchest") as? List<ItemStack?>) ?: emptyList()

                    perEvent[uuid] = EventPlayerInventory(
                        inventory = inventory,
                        armor = armor,
                        offHand = section.getItemStack("offhand"),
                        enderChest = enderChest,
                    )
                } catch (e: Exception) {
                    plugin.logger.warning("Could not load event inventory for $eventId/$key: ${e.message}")
                }
            }

            cache[eventId] = perEvent
        }

        plugin.logger.info("Loaded event inventories for ${cache.size} event(s).")
    }
}