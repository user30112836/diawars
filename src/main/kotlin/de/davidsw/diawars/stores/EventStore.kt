package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.SqlOp
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID

enum class EventState {
    BUILDING,
    SUBMITTED,
    REJECTED,
    ACCEPTED,
    ACTIVE,
    ENDED,
}

data class GameEvent(
    val id: String,
    var name: String,
    val creator: UUID,
    var state: EventState,
    val worldName: String,
    var startTime: Long = 0L, // epoch seconds, only meaningful once ACCEPTED/ACTIVE
    var endTime: Long = 0L,   // epoch seconds, only meaningful once ACCEPTED/ACTIVE
)

class EventStore(plugin: Diawars) : SqlStore(plugin, "events") {
    private val cache = mutableMapOf<String, GameEvent>()
    private val byWorld = mutableMapOf<String, String>() // worldName -> eventId
    private val usedIds = mutableSetOf<String>()
    private val newUsedIds = mutableSetOf<String>()
    private val removedIds = mutableSetOf<String>()

    init {
        migrateLegacyYaml("events.yml", "events") { yaml -> legacyRows(yaml) }
        load()
    }

    fun getEvent(id: String): GameEvent? = cache[id]

    fun getAll(): Collection<GameEvent> = cache.values

    fun getByCreator(playerId: UUID): List<GameEvent> = cache.values.filter { it.creator == playerId }

    fun getByState(state: EventState): List<GameEvent> = cache.values.filter { it.state == state }

    fun getByWorld(worldName: String): GameEvent? = byWorld[worldName]?.let { cache[it] }

    fun generateId(name: String): String {
        val id = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { throw IllegalArgumentException("Das Event braucht einen Namen!") }
        if (id in usedIds) throw IllegalArgumentException("Der Name des Events ist bereits vergeben!")
        return id
    }

    fun addEvent(event: GameEvent) {
        cache[event.id] = event
        usedIds.add(event.id)
        newUsedIds.add(event.id)
        byWorld[event.worldName] = event.id
        saveImmediately()
    }

    fun removeEvent(id: String) {
        val ev = cache.remove(id)
        if (ev != null) byWorld.remove(ev.worldName)
        removedIds.add(id)
        saveImmediately()
    }

    override fun loadFrom(connection: Connection) {
        cache.clear()
        byWorld.clear()
        usedIds.clear()

        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id FROM used_event_ids").use { rs ->
                while (rs.next()) usedIds += rs.getString("id")
            }
        }
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT id, name, creator, state, world, start_time, end_time FROM events",
            ).use { rs ->
                while (rs.next()) {
                    try {
                        val id = rs.getString("id")
                        val worldName = rs.getString("world") ?: "event_$id"
                        cache[id] = GameEvent(
                            id = id,
                            name = rs.getString("name") ?: id,
                            creator = UUID.fromString(rs.getString("creator")),
                            state = EventState.valueOf(rs.getString("state") ?: "BUILDING"),
                            worldName = worldName,
                            startTime = rs.getLong("start_time"),
                            endTime = rs.getLong("end_time"),
                        )
                        usedIds.add(id)
                        byWorld[worldName] = id
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load event ${rs.getString("id")}: ${e.message}")
                    }
                }
            }
        }
        plugin.logger.info("Loaded ${cache.size} event(s).")
    }

    /**
     * Events are few and their fields are mutated in place by
     * [de.davidsw.diawars.managers.EventManager] (state, name, timeframe), so the
     * whole cache is rewritten — same contract as the old YAML store, where
     * `markDirty()` also persisted every event.
     */
    override fun writeTo(ops: MutableList<SqlOp>) {
        for (id in newUsedIds.toList()) {
            ops += SqlOp("INSERT INTO used_event_ids (id) VALUES (?) ON CONFLICT(id) DO NOTHING", listOf(id))
        }
        newUsedIds.clear()

        for (id in removedIds) {
            ops += SqlOp("DELETE FROM events WHERE id = ?", listOf(id))
        }
        removedIds.clear()

        for (event in cache.values) {
            ops += SqlOp(
                "INSERT INTO events (id, name, creator, state, world, start_time, end_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(id) DO UPDATE SET name = excluded.name, creator = excluded.creator, " +
                    "state = excluded.state, world = excluded.world, start_time = excluded.start_time, " +
                    "end_time = excluded.end_time",
                listOf(
                    event.id, event.name, event.creator, event.state,
                    event.worldName, event.startTime, event.endTime,
                ),
            )
        }
    }

    /** Parses the pre-SQLite `events.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        val legacyUsedIds = yaml.getStringList("_used-ids").toMutableSet()

        for (id in yaml.getKeys(false)) {
            if (id == "_used-ids") continue
            try {
                val section = yaml.getConfigurationSection(id) ?: continue
                val creator = section.getString("creator") ?: continue
                val worldName = section.getString("world") ?: "event_$id"
                ops += SqlOp(
                    "INSERT INTO events (id, name, creator, state, world, start_time, end_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    listOf(
                        id,
                        section.getString("name") ?: id,
                        UUID.fromString(creator),
                        EventState.valueOf(section.getString("state") ?: "BUILDING"),
                        worldName,
                        section.getLong("start-time", 0L),
                        section.getLong("end-time", 0L),
                    ),
                )
                legacyUsedIds.add(id)
            } catch (e: Exception) {
                plugin.logger.warning("Could not import event $id: ${e.message}")
            }
        }

        for (id in legacyUsedIds) {
            ops += SqlOp("INSERT INTO used_event_ids (id) VALUES (?) ON CONFLICT(id) DO NOTHING", listOf(id))
        }
        return ops
    }
}
