package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

data class StoredImageMap(
    var owner: UUID,
    var file: String,
)

class ImageMapStore(plugin: Diawars) : YamlStore(plugin, "image_maps.yml") {
    private val maps = mutableMapOf<Int, StoredImageMap>()

    init {
        load()
    }

    fun putMap(mapId: Int, owner: UUID, file: String) {
        maps[mapId] = StoredImageMap(owner, file)
        markDirty()
    }

    fun removeMap(mapId: Int) {
        if (maps.remove(mapId) != null) markDirty()
    }

    fun get(mapId: Int): StoredImageMap? = maps[mapId]

    fun getAll(): Map<Int, StoredImageMap> = maps.toMap()

    override fun writeTo(yaml: YamlConfiguration) {
        for ((mapId, ref) in maps) {
            yaml.set("$mapId.owner", ref.owner.toString())
            yaml.set("$mapId.file", ref.file)
        }
    }

    override fun readFrom(yaml: YamlConfiguration) {
        maps.clear()
        for (key in yaml.getKeys(false)) {
            val mapId = key.toIntOrNull()
            if (mapId == null) {
                plugin.logger.warning("Ignoring invalid map id in ${storeFile.name}: $key")
                continue
            }
            try {
                val owner = UUID.fromString(yaml.getString("$key.owner") ?: continue)
                val file = yaml.getString("$key.file") ?: continue
                maps[mapId] = StoredImageMap(owner, file)
            } catch (e: Exception) {
                plugin.logger.warning("Could not load image map $key: ${e.message}")
            }
        }
        plugin.logger.info("Loaded ${maps.size} image map(s).")
    }
}
