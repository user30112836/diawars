package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
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

class EventStore(private val plugin: Diawars) {
    private val storeFile = File(plugin.dataFolder, "events.yml")
    private val cache = mutableMapOf<String, GameEvent>()

    init {
        load()
    }

    fun getEvent(id: String): GameEvent? = cache[id]

    fun getAll(): Collection<GameEvent> = cache.values

    fun getByCreator(playerId: UUID): List<GameEvent> = cache.values.filter { it.creator == playerId }

    fun getByState(state: EventState): List<GameEvent> = cache.values.filter { it.state == state }

    fun getByWorld(worldName: String): GameEvent? = cache.values.firstOrNull { it.worldName == worldName }

    fun generateId(name: String): String {
        val id = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { throw Error("Das Event braucht einen Namen!") }
        if (cache.containsKey(id)) throw Error("Der Name des Events ist bereits vergeben!")
        return id
    }

    fun addEvent(event: GameEvent) {
        cache[event.id] = event
        save()
    }

    fun removeEvent(id: String) {
        cache.remove(id)
        save()
    }

    fun save() {
        val config = YamlConfiguration()
        for ((id, event) in cache) {
            config.set("$id.name", event.name)
            config.set("$id.creator", event.creator.toString())
            config.set("$id.state", event.state.name)
            config.set("$id.world", event.worldName)
            config.set("$id.start-time", event.startTime)
            config.set("$id.end-time", event.endTime)
        }
        try {
            config.save(storeFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save events to $storeFile: ${e.message}")
        }
    }

    private fun load() {
        if (!storeFile.exists()) {
            storeFile.parentFile.mkdirs()
            storeFile.createNewFile()
        }

        val yaml = YamlConfiguration.loadConfiguration(storeFile)

        for (id in yaml.getKeys(false)) {
            try {
                val section = yaml.getConfigurationSection(id) ?: continue
                val creatorString = section.getString("creator") ?: continue
                cache[id] = GameEvent(
                    id = id,
                    name = section.getString("name") ?: id,
                    creator = UUID.fromString(creatorString),
                    state = EventState.valueOf(section.getString("state") ?: "BUILDING"),
                    worldName = section.getString("world") ?: "event_$id",
                    startTime = section.getLong("start-time", 0L),
                    endTime = section.getLong("end-time", 0L),
                )
            } catch (e: Exception) {
                plugin.logger.warning("Could not load event $id: ${e.message}")
            }
        }

        plugin.logger.info("Loaded ${cache.size} event(s).")
    }
}