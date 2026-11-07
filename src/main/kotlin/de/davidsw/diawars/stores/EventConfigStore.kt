package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.ItemBlob
import de.davidsw.diawars.util.JsonColumn
import de.davidsw.diawars.util.SqlOp
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.GameRules
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.sql.Connection

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

class EventConfigStore(plugin: Diawars) : SqlStore(plugin, "event_configs") {
    private val cache = mutableMapOf<String, EventConfig>()
    private val dirty = DirtyKeys<String>()

    init {
        migrateLegacyYaml("event_configs.yml", "event_configs") { yaml -> legacyRows(yaml) }
        load()
    }

    fun getConfig(eventId: String): EventConfig = cache[eventId] ?: EventConfig()

    fun update(eventId: String, transform: (EventConfig) -> EventConfig) {
        cache[eventId] = transform(getConfig(eventId))
        dirty.touch(eventId)
        markDirty()
    }

    fun clearEvent(eventId: String) {
        if (cache.remove(eventId) != null) {
            dirty.touch(eventId)
            saveImmediately()
        }
    }

    override fun loadFrom(connection: Connection) {
        cache.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT event, gamemode, advance_time, fixed_time, gamerules, effects, " +
                    "starting_inventory, starting_armor, starting_offhand FROM event_configs",
            ).use { rs ->
                while (rs.next()) {
                    try {
                        val eventId = rs.getString("event")
                        cache[eventId] = EventConfig(
                            gameMode = GameMode.entries.firstOrNull { it.name == rs.getString("gamemode") }
                                ?: GameMode.SURVIVAL,
                            advanceTime = rs.getInt("advance_time") != 0,
                            fixedTime = rs.getLong("fixed_time"),
                            gameRules = normalizedRules(JsonColumn.booleanMap(rs.getString("gamerules"))),
                            effects = parseEffects(JsonColumn.strings(rs.getString("effects"))),
                            startingInventory = ItemBlob.decodeItems(rs.getBytes("starting_inventory")),
                            startingArmor = ItemBlob.decodeItems(rs.getBytes("starting_armor")),
                            startingOffHand = ItemBlob.decodeItem(rs.getBytes("starting_offhand")),
                        )
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load event config for ${rs.getString("event")}: ${e.message}")
                    }
                }
            }
        }
        plugin.logger.info("Loaded event config(s) for ${cache.size} event(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (eventId in dirty.drain()) {
            val cfg = cache[eventId]
            if (cfg == null) {
                ops += SqlOp("DELETE FROM event_configs WHERE event = ?", listOf(eventId))
                continue
            }
            ops += SqlOp(
                "INSERT INTO event_configs (event, gamemode, advance_time, fixed_time, gamerules, effects, " +
                    "starting_inventory, starting_armor, starting_offhand) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(event) DO UPDATE SET gamemode = excluded.gamemode, " +
                    "advance_time = excluded.advance_time, fixed_time = excluded.fixed_time, " +
                    "gamerules = excluded.gamerules, effects = excluded.effects, " +
                    "starting_inventory = excluded.starting_inventory, starting_armor = excluded.starting_armor, " +
                    "starting_offhand = excluded.starting_offhand",
                listOf(
                    eventId, cfg.gameMode, cfg.advanceTime, cfg.fixedTime,
                    JsonColumn.encode(normalizedRules(cfg.gameRules)),
                    JsonColumn.encode(cfg.effects.map { "${it.type}:${it.amplifier}" }),
                    ItemBlob.encodeItems(cfg.startingInventory),
                    ItemBlob.encodeItems(cfg.startingArmor),
                    ItemBlob.encodeItem(cfg.startingOffHand),
                ),
            )
        }
    }

    private fun normalizedRules(raw: Map<String, Boolean>): Map<String, Boolean> =
        EventGameRules.CONFIGURABLE.keys.associateWith { key ->
            raw[key] ?: EventGameRules.DEFAULTS[key] ?: true
        }

    private fun parseEffects(entries: List<String>): List<EventPotionEffect> = entries.mapNotNull { entry ->
        val parts = entry.split(":")
        val type = parts.getOrNull(0) ?: return@mapNotNull null
        val amplifier = parts.getOrNull(1)?.toIntOrNull() ?: 0
        EventPotionEffect(type, amplifier)
    }

    /** Parses the pre-SQLite `event_configs.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        for (eventId in yaml.getKeys(false)) {
            try {
                val section = yaml.getConfigurationSection(eventId) ?: continue
                val gameRules = EventGameRules.CONFIGURABLE.keys.associateWith { key ->
                    section.getBoolean("gamerules.$key", EventGameRules.DEFAULTS[key] ?: true)
                }

                @Suppress("UNCHECKED_CAST")
                val startingInventory = (section.getList("starting-inventory") as? List<ItemStack?>) ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val startingArmor = (section.getList("starting-armor") as? List<ItemStack?>) ?: emptyList()

                ops += SqlOp(
                    "INSERT INTO event_configs (event, gamemode, advance_time, fixed_time, gamerules, effects, " +
                        "starting_inventory, starting_armor, starting_offhand) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    listOf(
                        eventId,
                        GameMode.entries.firstOrNull { it.name == section.getString("gamemode") }
                            ?: GameMode.SURVIVAL,
                        section.getBoolean("advance-time", true),
                        section.getLong("fixed-time", 6000L),
                        JsonColumn.encode(gameRules),
                        JsonColumn.encode(section.getStringList("effects")),
                        ItemBlob.encodeItems(startingInventory),
                        ItemBlob.encodeItems(startingArmor),
                        ItemBlob.encodeItem(section.getItemStack("starting-offhand")),
                    ),
                )
            } catch (e: Exception) {
                plugin.logger.warning("Could not import event config for $eventId: ${e.message}")
            }
        }
        return ops
    }
}
