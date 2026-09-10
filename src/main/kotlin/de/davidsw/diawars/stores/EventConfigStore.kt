package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.StoreFiles
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.GameRules
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack

object EventGameRules {
    val CONFIGURABLE: Map<String, GameRule<Boolean>> = linkedMapOf(
        "keep-inventory" to GameRules.KEEP_INVENTORY,
        "mob-griefing" to GameRules.MOB_GRIEFING,
        "mob-drops" to GameRules.MOB_DROPS,
        "advance-weather" to GameRules.ADVANCE_WEATHER,
        "block-drops" to GameRules.BLOCK_DROPS,
    )

    val DEFAULTS: Map<String, Boolean> = mapOf(
        "keep-inventory" to false,
        "mob-griefing" to true,
        "mob-drops" to true,
        "advance-weather" to true,
        "block-drops" to true,
    )
}

data class EventPotionEffect(
    val type: String,
    val amplifier: Int, // 0 = level 1
)

data class EventConfig(
    val gameMode: GameMode = GameMode.SURVIVAL,
    val advanceTime: Boolean = true,
    val fixedTime: Long = 6000L,
    val gameRules: Map<String, Boolean> = EventGameRules.DEFAULTS,
    val effects: List<EventPotionEffect> = emptyList(),
    val startingInventory: List<ItemStack?> = emptyList(),
    val startingArmor: List<ItemStack?> = emptyList(),
    val startingOffHand: ItemStack? = null,
)

class EventConfigStore(private val plugin: Diawars) {
    private val storeFile = StoreFiles.resolve(plugin, "event_configs.yml")
    private val cache = mutableMapOf<String, EventConfig>()

    init {
        load()
    }

    fun getConfig(eventId: String): EventConfig = cache[eventId] ?: EventConfig()

    fun update(eventId: String, transform: (EventConfig) -> EventConfig) {
        cache[eventId] = transform(getConfig(eventId))
        save()
    }

    fun clearEvent(eventId: String) {
        if (cache.remove(eventId) != null) save()
    }

    private fun save() {
        val config = YamlConfiguration()
        for ((eventId, cfg) in cache) {
            config.set("$eventId.gamemode", cfg.gameMode.name)
            config.set("$eventId.advance-time", cfg.advanceTime)
            config.set("$eventId.fixed-time", cfg.fixedTime)
            EventGameRules.CONFIGURABLE.keys.forEach { key ->
                config.set("$eventId.gamerules.$key", cfg.gameRules[key] ?: EventGameRules.DEFAULTS[key])
            }
            config.set("$eventId.effects", cfg.effects.map { "${it.type}:${it.amplifier}" })
            config.set("$eventId.starting-inventory", cfg.startingInventory)
            config.set("$eventId.starting-armor", cfg.startingArmor)
            config.set("$eventId.starting-offhand", cfg.startingOffHand)
        }
        try {
            config.save(storeFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save event configs to $storeFile: ${e.message}")
        }
    }

    private fun load() {
        if (!storeFile.exists()) {
            storeFile.parentFile.mkdirs()
            storeFile.createNewFile()
        }

        val yaml = YamlConfiguration.loadConfiguration(storeFile)

        for (eventId in yaml.getKeys(false)) {
            try {
                val section = yaml.getConfigurationSection(eventId) ?: continue

                val gameRules = EventGameRules.CONFIGURABLE.keys.associateWith { key ->
                    section.getBoolean("gamerules.$key", EventGameRules.DEFAULTS[key] ?: true)
                }

                val effects = section.getStringList("effects").mapNotNull { entry ->
                    val parts = entry.split(":")
                    val type = parts.getOrNull(0) ?: return@mapNotNull null
                    val amplifier = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    EventPotionEffect(type, amplifier)
                }

                @Suppress("UNCHECKED_CAST")
                val startingInventory = (section.getList("starting-inventory") as? List<ItemStack?>) ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val startingArmor = (section.getList("starting-armor") as? List<ItemStack?>) ?: emptyList()

                cache[eventId] = EventConfig(
                    gameMode = GameMode.entries.firstOrNull { it.name == section.getString("gamemode") } ?: GameMode.SURVIVAL,
                    advanceTime = section.getBoolean("advance-time", true),
                    fixedTime = section.getLong("fixed-time", 6000L),
                    gameRules = gameRules,
                    effects = effects,
                    startingInventory = startingInventory,
                    startingArmor = startingArmor,
                    startingOffHand = section.getItemStack("starting-offhand"),
                )
            } catch (e: Exception) {
                plugin.logger.warning("Could not load event config for $eventId: ${e.message}")
            }
        }

        plugin.logger.info("Loaded event config(s) for ${cache.size} event(s).")
    }
}