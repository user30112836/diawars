package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

/**
 * Per-player night vision opt-ins. Only `true` entries are stored;
 * everyone else defaults to off.
 */
class NightVisionStore(plugin: Diawars) : YamlStore(plugin, "night_vision.yml") {
    private val enabled = mutableSetOf<UUID>()

    init {
        load()
    }

    fun isEnabled(playerId: UUID): Boolean = playerId in enabled

    fun setEnabled(playerId: UUID, on: Boolean) {
        if (on) enabled.add(playerId) else enabled.remove(playerId)
        markDirty()
    }

    override fun readFrom(yaml: YamlConfiguration) {
        for (key in yaml.getKeys(false)) {
            try {
                val uuid = UUID.fromString(key)
                if (yaml.getBoolean(key, false)) enabled.add(uuid)
            } catch (e: Exception) {
                plugin.logger.warning("Could not load night vision state for $key: ${e.message}")
            }
        }
    }

    override fun writeTo(yaml: YamlConfiguration) {
        for (uuid in enabled) {
            yaml.set(uuid.toString(), true)
        }
    }
}
