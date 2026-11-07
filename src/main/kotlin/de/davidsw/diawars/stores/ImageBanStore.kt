package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.SqlOp
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID

/**
 * Players banned from the image feature: they cannot upload, cannot open the
 * image menu and cannot use /image. Their existing images stay on disk and
 * their maps show the placeholder while the ban lasts (see
 * [de.davidsw.diawars.managers.ImageManager.banPlayer]).
 */
class ImageBanStore(plugin: Diawars) : SqlStore(plugin, "image_bans") {
    private val banned = mutableSetOf<UUID>()
    private val dirty = DirtyKeys<UUID>()

    init {
        migrateLegacyYaml("image_bans.yml", "image_bans") { yaml -> legacyRows(yaml) }
        load()
    }

    fun isBanned(playerId: UUID): Boolean = banned.contains(playerId)

    /** Bans the player. Returns false when already banned. */
    fun ban(playerId: UUID): Boolean {
        if (!banned.add(playerId)) return false
        dirty.touch(playerId)
        markDirty()
        return true
    }

    /** Unbans the player. Returns false when not banned. */
    fun unban(playerId: UUID): Boolean {
        if (!banned.remove(playerId)) return false
        dirty.touch(playerId)
        markDirty()
        return true
    }

    fun getAll(): Set<UUID> = banned.toSet()

    override fun loadFrom(connection: Connection) {
        banned.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT player FROM image_bans").use { rs ->
                while (rs.next()) {
                    try {
                        banned += UUID.fromString(rs.getString("player"))
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load image ban from ${rs.getString("player")}: ${e.message}")
                    }
                }
            }
        }
        plugin.logger.info("Loaded ${banned.size} image ban(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (playerId in dirty.drain()) {
            if (playerId in banned) {
                ops += SqlOp("INSERT INTO image_bans (player) VALUES (?) ON CONFLICT(player) DO NOTHING", listOf(playerId))
            } else {
                ops += SqlOp("DELETE FROM image_bans WHERE player = ?", listOf(playerId))
            }
        }
    }

    /** Parses the pre-SQLite `image_bans.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        for (key in yaml.getKeys(false)) {
            try {
                ops += SqlOp("INSERT INTO image_bans (player) VALUES (?)", listOf(UUID.fromString(key)))
            } catch (e: Exception) {
                plugin.logger.warning("Could not import image ban from $key: ${e.message}")
            }
        }
        return ops
    }
}
