package de.davidsw.diawars.listeners

import com.google.gson.Gson
import com.google.gson.JsonObject
import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.ImageManager
import de.davidsw.diawars.managers.ImageMapRenderer
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.MapInitializeEvent
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.plugin.messaging.PluginMessageListener

class ImageListener(
    private val plugin: Diawars,
) : Listener, PluginMessageListener {

    companion object {
        const val CHANNEL = "diawars:image"
        const val OPEN_CHANNEL = "diawars:image_open"

        private val GSON = Gson()

        fun readVarIntPrefixedUtf8(bytes: ByteArray): String {
            var index = 0
            var length = 0
            var shift = 0

            while (true) {
                if (index >= bytes.size) throw IllegalArgumentException("Missing VarInt")
                val byte = bytes[index++].toInt()
                length = length or ((byte and 0x7F) shl shift)
                if (byte and 0x80 == 0) break
                shift += 7
                if (shift >= 35) throw IllegalArgumentException("VarInt too big")
            }

            if (length < 0 || index + length > bytes.size) throw IllegalArgumentException("Invalid message length")
            return String(bytes, index, length, Charsets.UTF_8)
        }
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != CHANNEL) return

        val json = try {
            readVarIntPrefixedUtf8(message)
        } catch (e: Exception) {
            plugin.logger.warning("Invalid image message from ${player.name}: ${e.message}")
            return
        }

        val (file, part, total, data) = try {
            parseChunk(json)
        } catch (e: Exception) {
            plugin.logger.warning("Invalid image chunk from ${player.name}: ${e.message}")
            player.sendMessage(mm("<red>Ungültige Bilddaten vom Client!</red>"))
            return
        }

        val result = try {
            plugin.imageManager.handleChunk(player, file, part, total, data)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to handle image chunk from ${player.name}: ${e.message}")
            plugin.imageManager.clearSession(player.uniqueId)
            player.sendMessage(mm("<red>Fehler beim Verarbeiten des Bildes!</red>"))
            return
        }

        when (result) {
            is ImageManager.Result.Success -> player.sendMessage(mm(result.message))
            is ImageManager.Result.Error -> player.sendMessage(mm(result.message))
            null -> { /* waiting for more parts */ }
        }
    }

    private data class Chunk(val file: String, val part: Int, val total: Int, val data: String)

    private fun parseChunk(json: String): Chunk {
        val root = GSON.fromJson(json, JsonObject::class.java)
            ?: throw IllegalArgumentException("JSON is null")
        val file = root.get("file")?.takeIf { it.isJsonPrimitive }?.asString
            ?: throw IllegalArgumentException("Missing file")
        val part = root.get("part")?.takeIf { it.isJsonPrimitive }?.asInt
            ?: throw IllegalArgumentException("Missing part")
        val total = root.get("total")?.takeIf { it.isJsonPrimitive }?.asInt
            ?: throw IllegalArgumentException("Missing total")
        val data = root.get("data")?.takeIf { it.isJsonPrimitive }?.asString
            ?: throw IllegalArgumentException("Missing data")
        return Chunk(file, part, total, data)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        plugin.imageManager.clearSession(event.player.uniqueId)
    }

    /**
     * Re-attaches our renderer whenever one of our maps initializes (freshly
     * created or loaded from disk). Loaded views only carry the vanilla
     * terrain renderer, which would otherwise repaint them as normal maps.
     */
    @EventHandler
    fun onMapInitialize(event: MapInitializeEvent) {
        val map = event.map
        if (map.renderers.any { it is ImageMapRenderer }) return
        val ref = plugin.store.imageMapStore.get(map.id) ?: return
        if (!plugin.imageManager.hasImage(ref.owner, ref.file)) {
            plugin.store.imageMapStore.removeMap(map.id)
            return
        }
        map.renderers.clear()
        map.addRenderer(ImageMapRenderer(plugin, ref.owner, ref.file))
    }

    /** Late-loading worlds (e.g. event worlds): retry the startup restore. */
    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        plugin.imageManager.restoreMapRenderers()
    }
}
