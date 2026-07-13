package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

class MessageStore(private val plugin: Diawars) {
    private val storeFile = File(plugin.dataFolder, "pending_messages.yml")
    private val cache = mutableMapOf<UUID, MutableList<String>>()

    init {
        load()
    }

    fun getPending(playerId: UUID): List<String> = cache[playerId] ?: emptyList()

    fun hasPending(playerId: UUID): Boolean = !cache[playerId].isNullOrEmpty()

    fun addPending(playerId: UUID, message: String) {
        val messages = cache.getOrPut(playerId) { mutableListOf() }
        messages.add(message)
        save()
    }

    fun clearPending(playerId: UUID) {
        if (cache.remove(playerId) != null) {
            save()
        }
    }

    private fun save() {
        val config = YamlConfiguration()
        for ((uuid, messages) in cache) {
            if (messages.isEmpty()) continue
            config.set(uuid.toString(), messages)
        }
        try {
            config.save(storeFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save pending messages to $storeFile: ${e.message}")
        }
    }

    private fun load() {
        if (!storeFile.exists()) {
            storeFile.parentFile.mkdirs()
            storeFile.createNewFile()
        }

        val yaml = YamlConfiguration.loadConfiguration(storeFile)

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