package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

class MessageStore(plugin: Diawars) : YamlStore(plugin, "pending_messages.yml") {
    private val cache = mutableMapOf<UUID, MutableList<String>>()

    init {
        load()
    }

    fun getPending(playerId: UUID): List<String> = cache[playerId] ?: emptyList()

    fun hasPending(playerId: UUID): Boolean = !cache[playerId].isNullOrEmpty()

    fun addPending(playerId: UUID, message: String) {
        val messages = cache.getOrPut(playerId) { mutableListOf() }
        messages.add(message)
        saveImmediately()
    }

    fun clearPending(playerId: UUID) {
        if (cache.remove(playerId) != null) {
            saveImmediately()
        }
    }

    override fun writeTo(yaml: YamlConfiguration) {
        for ((uuid, messages) in cache) {
            if (messages.isEmpty()) continue
            yaml.set(uuid.toString(), messages)
        }
    }

    override fun readFrom(yaml: YamlConfiguration) {
        for (key in yaml.getKeys(false)) {
            try {
                val uuid = UUID.fromString(key)
                val messages = yaml.getStringList(key).toMutableList()
                if (messages.isNotEmpty()) {
                    cache[uuid] = messages
                }
            } catch (e: Exception) {
                plugin.logger.warning("Could not load pending messages for $key: ${e.message}")
            }
        }

        plugin.logger.info("Loaded pending messages for ${cache.size} player(s).")
    }
}
