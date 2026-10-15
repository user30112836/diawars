package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.listeners.ImageListener
import de.davidsw.diawars.util.MiniMessageHelper.escape
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapView
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO

class ImageManager(private val plugin: Diawars) {

    companion object {
        const val MAP_SIZE = 128

        /** Max decoded image bytes per upload (protects against OOM). */
        const val MAX_IMAGE_BYTES = 2 * 1024 * 1024

        /** Max chunk payloads per image (total part count sanity bound). */
        const val MAX_PARTS = 256

        /** Max base64 chars per chunk (keeps each payload well below packet limits). */
        const val MAX_CHUNK_CHARS = 20_000

        /** Upload session timeout in millis. */
        const val SESSION_TIMEOUT_MILLIS = 120_000L

        private val FILE_NAME_REGEX = Regex("^[A-Za-z0-9._-]{1,32}$")
    }

    sealed interface Result {
        data class Success(val message: String) : Result
        data class Error(val message: String) : Result
    }

    private data class UploadSession(
        val file: String,
        val total: Int,
        val parts: MutableMap<Int, String> = mutableMapOf(),
        var lastUpdate: Long = System.currentTimeMillis(),
    )

    private val sessions = mutableMapOf<UUID, UploadSession>()
    private val sessionLock = Any()

    // Cached per-player file lists: menus re-render every 40 ticks, so the
    // hot paths (menu, main-menu count, tab completion) must not hit the disk.
    private val imageListCache = mutableMapOf<UUID, MutableList<String>>()
    private val imageListLock = Any()

    // ------------------------------------------------------------------
    // storage
    // ------------------------------------------------------------------

    fun imagesDir(): File = File(plugin.dataFolder, "images")

    fun playerDir(owner: UUID): File = File(imagesDir(), owner.toString())

    fun imageFile(owner: UUID, file: String): File = File(playerDir(owner), "$file.png")

    fun isValidFileName(file: String): Boolean {
        if (file.equals("all", ignoreCase = true)) return false
        if (file == "." || file == "..") return false
        if (file.contains("/") || file.contains("\\")) return false
        return FILE_NAME_REGEX.matches(file)
    }

    fun listImages(owner: UUID): List<String> {
        synchronized(imageListLock) {
            imageListCache[owner]?.let { return it.toList() }
            val scanned = scanDisk(owner)
            imageListCache[owner] = scanned.toMutableList()
            return scanned
        }
    }

    private fun scanDisk(owner: UUID): List<String> {
        val dir = playerDir(owner)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension.equals("png", ignoreCase = true) }
            .orEmpty()
            .map { it.nameWithoutExtension }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun getImageCount(owner: UUID): Int = listImages(owner).size

    fun hasImage(owner: UUID, file: String): Boolean = imageFile(owner, file).isFile

    fun loadImage(owner: UUID, file: String): BufferedImage? {
        val f = imageFile(owner, file)
        if (!f.isFile) return null
        return try {
            f.inputStream().use { ImageIO.read(it) }?.let { ensure128(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun deleteImage(owner: UUID, file: String): Boolean {
        val f = imageFile(owner, file)
        if (!f.isFile) return false
        val deleted = f.delete()
        if (deleted) {
            synchronized(imageListLock) { imageListCache[owner]?.remove(file) }
        }
        return deleted
    }

    fun deleteAllImages(owner: UUID): Int {
        val dir = playerDir(owner)
        if (!dir.isDirectory) {
            synchronized(imageListLock) { imageListCache.remove(owner) }
            return 0
        }
        val files = dir.listFiles { f -> f.isFile && f.extension.equals("png", ignoreCase = true) }.orEmpty()
        var count = 0
        files.forEach { if (it.delete()) count++ }
        synchronized(imageListLock) { imageListCache.remove(owner) }
        return count
    }

    /** All player UUIDs known to the image system (have files or a custom limit). */
    fun getAllKnownPlayers(): Set<UUID> {
        val result = mutableSetOf<UUID>()
        imagesDir().listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                try {
                    result += UUID.fromString(dir.name)
                } catch (_: IllegalArgumentException) {
                    // ignore non-uuid folders
                }
            }
        }
        result += plugin.store.imageLimitStore.getAllLimits().keys
        return result
    }

    // ------------------------------------------------------------------
    // chunked upload (diawars:image payload)
    // ------------------------------------------------------------------

    /**
     * Handles one upload chunk. Expected JSON fields (parsed by the listener):
     * file, part (0-based), total, data (base64 chunk).
     * Returns a [Result] only when the upload completes or fails fatally;
     * null means "chunk accepted, waiting for more".
     */
    fun handleChunk(player: Player, file: String, part: Int, total: Int, data: String): Result? {
        if (!isValidFileName(file)) {
            clearSession(player.uniqueId)
            return Result.Error("<red>Ungültiger Dateiname! Erlaubt sind Buchstaben, Zahlen sowie . _ - (max. 32 Zeichen). \"all\" ist reserviert.</red>")
        }
        if (total < 1 || total > MAX_PARTS) {
            clearSession(player.uniqueId)
            return Result.Error("<red>Ungültige Bilddaten (Anzahl der Teile)!</red>")
        }
        if (part < 0 || part >= total) {
            clearSession(player.uniqueId)
            return Result.Error("<red>Ungültige Bilddaten (Teilnummer)!</red>")
        }
        if (data.length > MAX_CHUNK_CHARS) {
            clearSession(player.uniqueId)
            return Result.Error("<red>Bildteil ist zu groß!</red>")
        }

        val playerId = player.uniqueId
        synchronized(sessionLock) {
            purgeExpiredLocked()
            val existing = sessions[playerId]
            if (existing != null && (existing.file != file || existing.total != total)) {
                // New upload started while another was in progress: replace it.
                sessions[playerId] = UploadSession(file, total)
            }
            val session = sessions.getOrPut(playerId) { UploadSession(file, total) }
            session.lastUpdate = System.currentTimeMillis()
            session.parts[part] = data

            if (session.parts.size < total) return null
            sessions.remove(playerId)
            return finishUpload(player, file, session)
        }
    }

    private fun finishUpload(player: Player, file: String, session: UploadSession): Result {
        // Limit check at completion time (images may have been added meanwhile).
        val count = getImageCount(player.uniqueId)
        val limit = plugin.store.imageLimitStore.getLimit(player.uniqueId)
        if (count >= limit) {
            return Result.Error("<red>Dein Bildlimit ist erreicht ($count/$limit)! Bitte einen Admin um eine Erhöhung.</red>")
        }
        if (hasImage(player.uniqueId, file)) {
            return Result.Error("<red>Du hast bereits ein Bild mit diesem Namen! Wähle einen anderen Dateinamen.</red>")
        }

        val combined = (0 until session.total).joinToString("") { session.parts[it] ?: "" }
        val bytes = try {
            Base64.getDecoder().decode(combined)
        } catch (_: IllegalArgumentException) {
            return Result.Error("<red>Ungültige Bilddaten (Base64)!</red>")
        }
        if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) {
            return Result.Error("<red>Das Bild ist zu groß oder leer (max. 2 MB)!</red>")
        }

        val decoded = try {
            ByteArrayInputStream(bytes).use { ImageIO.read(it) }
        } catch (_: Exception) {
            null
        } ?: return Result.Error("<red>Die Datei ist kein gültiges Bild (PNG/JPG erwartet)!</red>")

        val resized = ensure128(decoded)

        return try {
            val dir = playerDir(player.uniqueId)
            dir.mkdirs()
            val target = imageFile(player.uniqueId, file)
            // Re-check after decode (TOCTOU-safe enough for this use case).
            if (target.exists()) {
                return Result.Error("<red>Du hast bereits ein Bild mit diesem Namen! Wähle einen anderen Dateinamen.</red>")
            }
            ImageIO.write(resized, "png", target)
            synchronized(imageListLock) {
                val cached = imageListCache.getOrPut(player.uniqueId) { mutableListOf() }
                if (!cached.any { it.equals(file, ignoreCase = true) }) {
                    cached.add(file)
                    cached.sortWith(String.CASE_INSENSITIVE_ORDER)
                }
            }
            val newCount = getImageCount(player.uniqueId)
            val newLimit = plugin.store.imageLimitStore.getLimit(player.uniqueId)
            val safeFile = escape(file)
            Result.Success("<green>✓ Bild <gold>$safeFile</gold> hochgeladen! ($newCount/$newLimit) Nutze <yellow>/image get $safeFile</yellow> oder das Bilder-Menü.</green>")
        } catch (e: Exception) {
            plugin.logger.warning("Could not save image $file for ${player.name}: ${e.message}")
            Result.Error("<red>Das Bild konnte nicht gespeichert werden!</red>")
        }
    }

    private fun purgeExpiredLocked() {
        val now = System.currentTimeMillis()
        val it = sessions.entries.iterator()
        while (it.hasNext()) {
            if (now - it.next().value.lastUpdate > SESSION_TIMEOUT_MILLIS) it.remove()
        }
    }

    fun clearSession(playerId: UUID) {
        synchronized(sessionLock) { sessions.remove(playerId) }
    }

    // ------------------------------------------------------------------
    // maps
    // ------------------------------------------------------------------

    fun ensure128(source: BufferedImage): BufferedImage {
        if (source.width == MAP_SIZE && source.height == MAP_SIZE) return source
        val out = BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_RGB)
        val g = out.graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(source, 0, 0, MAP_SIZE, MAP_SIZE, null)
        g.dispose()
        return out
    }

    /** Gives the player a map with the stored image, or an error message. */
    fun giveImage(player: Player, owner: UUID, file: String): Result {
        if (!isValidFileName(file)) {
            return Result.Error("<red>Ungültiger Dateiname!</red>")
        }
        loadImage(owner, file)
            ?: return Result.Error("<red>Dieses Bild existiert nicht!</red>")
        val mapView: MapView = Bukkit.createMap(player.world)
        mapView.renderers.clear()
        mapView.addRenderer(ImageMapRenderer(plugin, owner, file))
        plugin.store.imageMapStore.putMap(mapView.id, owner, file)
        val stack = ItemStack(Material.FILLED_MAP)
        val meta = stack.itemMeta as MapMeta
        meta.mapView = mapView
        meta.displayName(mm("<white>${escape(file)}</white>"))
        stack.itemMeta = meta
        val safeFile = escape(file)
        val leftover = player.inventory.addItem(stack)
        return if (leftover.isEmpty()) {
            Result.Success("<green>✓ Karte <gold>$safeFile</gold> erhalten!</green>")
        } else {
            player.world.dropItemNaturally(player.location, stack)
            Result.Success("<green>✓ Karte <gold>$safeFile</gold> erhalten! (Inventar war voll, Karte liegt am Boden.)</green>")
        }
    }

    /**
     * Re-attaches our renderer to every known image map whose view exists.
     * Runs at startup and whenever a world loads (late-loading worlds);
     * per-map [org.bukkit.event.server.MapInitializeEvent] covers the rest.
     * Drops mappings whose image file is gone. Idempotent.
     */
    fun restoreMapRenderers() {
        var restored = 0
        var dropped = 0
        for ((mapId, ref) in plugin.store.imageMapStore.getAll()) {
            val view = try {
                Bukkit.getMap(mapId)
            } catch (e: Exception) {
                plugin.logger.warning("Could not load map $mapId: ${e.message}")
                continue
            } ?: continue
            if (view.renderers.any { it is ImageMapRenderer }) continue
            if (!hasImage(ref.owner, ref.file)) {
                plugin.store.imageMapStore.removeMap(mapId)
                dropped++
                continue
            }
            view.renderers.clear()
            view.addRenderer(ImageMapRenderer(plugin, ref.owner, ref.file))
            restored++
        }
        if (restored > 0 || dropped > 0) {
            plugin.logger.info("Restored $restored image map(s), dropped $dropped stale entr(ies).")
        }
    }

    /**
     * Asks the player's client mod to open the image upload UI (via the
     * [ImageListener.OPEN_CHANNEL] signal). Every player needs the mod anyway,
     * so clients without it simply ignore the packet.
     */
    fun requestUploadUi(player: Player) {
        player.closeInventory()
        try {
            player.sendPluginMessage(plugin, ImageListener.OPEN_CHANNEL, ByteArray(0))
        } catch (e: Exception) {
            plugin.logger.warning("Could not request image upload UI for ${player.name}: ${e.message}")
            player.sendMessage(mm("<red>Der Upload konnte nicht geöffnet werden! Stelle sicher, dass du die Diawars-Client-Mod installiert hast.</red>"))
        }
    }
}
