package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.SqlOp
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
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

class ImageMapStore(plugin: Diawars) : SqlStore(plugin, "image_maps") {
    private val maps = mutableMapOf<Int, StoredImageMap>()
    private val dirty = DirtyKeys<Int>()

    init {
        migrateLegacyYaml("image_maps.yml", "image_maps") { yaml -> legacyRows(yaml) }
        load()
    }

    fun putMap(mapId: Int, owner: UUID, file: String) {
        maps[mapId] = StoredImageMap(owner, file)
        dirty.touch(mapId)
        markDirty()
    }

    /** Marks a map's image as deleted; the map keeps showing the placeholder. */
    fun markDeleted(mapId: Int) {
        val ref = maps[mapId] ?: return
        if (!ref.deleted) {
            ref.deleted = true
            dirty.touch(mapId)
            markDirty()
        }
    }

    fun removeMap(mapId: Int) {
        if (maps.remove(mapId) != null) {
            dirty.touch(mapId)
            markDirty()
        }
    }

    fun get(mapId: Int): StoredImageMap? = maps[mapId]

    fun getAll(): Map<Int, StoredImageMap> = maps.toMap()

    override fun loadFrom(connection: Connection) {
        maps.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT map_id, owner, file, deleted FROM image_maps").use { rs ->
                while (rs.next()) {
                    try {
                        maps[rs.getInt("map_id")] = StoredImageMap(
                            owner = UUID.fromString(rs.getString("owner")),
                            file = rs.getString("file"),
                            deleted = rs.getInt("deleted") != 0,
                        )
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load image map ${rs.getInt("map_id")}: ${e.message}")
                    }
                }
            }
        }
        plugin.logger.info("Loaded ${maps.size} image map(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (mapId in dirty.drain()) {
            val ref = maps[mapId]
            if (ref == null) {
                ops += SqlOp("DELETE FROM image_maps WHERE map_id = ?", listOf(mapId))
            } else {
                ops += SqlOp(
                    "INSERT INTO image_maps (map_id, owner, file, deleted) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT(map_id) DO UPDATE SET owner = excluded.owner, file = excluded.file, " +
                        "deleted = excluded.deleted",
                    listOf(mapId, ref.owner, ref.file, ref.deleted),
                )
            }
        }
    }

    /** Parses the pre-SQLite `image_maps.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        for (key in yaml.getKeys(false)) {
            try {
                val mapId = key.toIntOrNull()
                if (mapId == null) {
                    plugin.logger.warning("Ignoring invalid map id in the legacy image map file: $key")
                    continue
                }
                val owner = UUID.fromString(yaml.getString("$key.owner") ?: continue)
                val file = yaml.getString("$key.file") ?: continue
                ops += SqlOp(
                    "INSERT INTO image_maps (map_id, owner, file, deleted) VALUES (?, ?, ?, ?)",
                    listOf(mapId, owner, file, yaml.getBoolean("$key.deleted", false)),
                )
            } catch (e: Exception) {
                plugin.logger.warning("Could not import image map $key: ${e.message}")
            }
        }
        return ops
    }
}
