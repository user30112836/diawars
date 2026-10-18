package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

data class StoredImageMap(
    var owner: UUID,
    var file: String,
    /**
     * True once the referenced image was deleted by an admin: the map then
     * shows the placeholder instead of the image, and stays that way across
     * restarts (the mapping is kept on purpose).
     */
    var deleted: Boolean = false,
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

    /** Marks a map's image as deleted; the map keeps showing the placeholder. */
    fun markDeleted(mapId: Int) {
        val ref = maps[mapId] ?: return
        if (!ref.deleted) {
            ref.deleted = true
            markDirty()
        }
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
            if (ref.deleted) yaml.set("$mapId.deleted", true)
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
                maps[mapId] = StoredImageMap(owner, file, yaml.getBoolean("$key.deleted", false))
            } catch (e: Exception) {
                plugin.logger.warning("Could not load image map $key: ${e.message}")
            }
        }
        plugin.logger.info("Loaded ${maps.size} image map(s).")
    }
}
