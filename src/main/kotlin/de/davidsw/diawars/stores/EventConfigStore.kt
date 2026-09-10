package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
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

class EventConfigStore(plugin: Diawars): YamlStore(plugin, "event_configs.yml") {
    private val cache = mutableMapOf<String, EventConfig>()

    init {
        load()
    }

    fun getConfig(eventId: String): EventConfig = cache[eventId] ?: EventConfig()

    fun update(eventId: String, transform: (EventConfig) -> EventConfig) {
        cache[eventId] = transform(getConfig(eventId))
        markDirty()
    }

    fun clearEvent(eventId: String) {
        if (cache.remove(eventId) != null) saveImmediately()
    }

    override fun writeTo(yaml: YamlConfiguration) {
        for ((eventId, cfg) in cache) {
            yaml.set("$eventId.gamemode", cfg.gameMode.name)
            yaml.set("$eventId.advance-time", cfg.advanceTime)
            yaml.set("$eventId.fixed-time", cfg.fixedTime)
            EventGameRules.CONFIGURABLE.keys.forEach { key ->
                yaml.set("$eventId.gamerules.$key", cfg.gameRules[key] ?: EventGameRules.DEFAULTS[key])
            }
            yaml.set("$eventId.effects", cfg.effects.map { "${it.type}:${it.amplifier}" })
            yaml.set("$eventId.starting-inventory", cfg.startingInventory)
            yaml.set("$eventId.starting-armor", cfg.startingArmor)
            yaml.set("$eventId.starting-offhand", cfg.startingOffHand)
        }
    }

    override fun readFrom(yaml: YamlConfiguration) {
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