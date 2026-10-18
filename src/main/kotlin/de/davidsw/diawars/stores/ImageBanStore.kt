package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

/**
 * Players banned from the image feature: they cannot upload, cannot open the
 * image menu and cannot use /image. Their existing images stay on disk and
 * their maps show the placeholder while the ban lasts (see
 * [de.davidsw.diawars.managers.ImageManager.banPlayer]).
 */
class ImageBanStore(plugin: Diawars) : YamlStore(plugin, "image_bans.yml") {
    private val banned = mutableSetOf<UUID>()

    init {
        load()
    }

    fun isBanned(playerId: UUID): Boolean = banned.contains(playerId)

    /** Bans the player. Returns false when already banned. */
    fun ban(playerId: UUID): Boolean {
        if (!banned.add(playerId)) return false
        markDirty()
        return true
    }

    /** Unbans the player. Returns false when not banned. */
    fun unban(playerId: UUID): Boolean {
        if (!banned.remove(playerId)) return false
        markDirty()
        return true
    }

    fun getAll(): Set<UUID> = banned.toSet()

    override fun writeTo(yaml: YamlConfiguration) {
        banned.forEach { yaml.set(it.toString(), true) }
    }

    override fun readFrom(yaml: YamlConfiguration) {
        banned.clear()
        for (key in yaml.getKeys(false)) {
            try {
                banned += UUID.fromString(key)
            } catch (e: Exception) {
                plugin.logger.warning("Could not load image ban from $key: ${e.message}")
            }
        }
        plugin.logger.info("Loaded ${banned.size} image ban(s).")
    }
}
