package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.listeners.ImageListener
import de.davidsw.diawars.util.BedrockUtil
import de.davidsw.diawars.util.MiniMessageHelper.escape
import com.google.gson.JsonObject
import de.davidsw.diawars.util.MiniMessageHelper.mm
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
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
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO

class ImageManager(private val plugin: Diawars) {

    companion object {
        const val MAP_SIZE = 128

        /** Max decoded image bytes per upload (protects against OOM). */
        const val MAX_IMAGE_BYTES = 2 * 1024 * 1024

        /** Max image width/height in pixels (bounds RAM for pathological PNGs). */
        const val MAX_IMAGE_DIMENSION = 2048

        /** Max chunk payloads per image (total part count sanity bound). */
        const val MAX_PARTS = 256

        /** Max base64 chars per chunk (keeps each payload well below packet limits). */
        const val MAX_CHUNK_CHARS = 20_000

        /** Download chunks per player per tick (mirrors the client upload pacing). */
        const val DOWNLOAD_CHUNKS_PER_TICK = 8

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

    /** 128x128 map thumbnail derived from the full image (what maps render). */
    fun thumbFile(owner: UUID, file: String): File = File(playerDir(owner), "thumbs/$file.png")

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

    /**
     * 128x128 thumbnail for map rendering. Generated from the full image on
     * first use, which also migrates images stored before thumbnails existed.
     */
    fun loadImage(owner: UUID, file: String): BufferedImage? {
        val thumb = thumbFile(owner, file)
        if (thumb.isFile) {
            try {
                thumb.inputStream().use { ImageIO.read(it) }?.let { return ensure128(it) }
            } catch (_: Exception) {
                // fall through and regenerate below
            }
        }
        val full = loadFullImage(owner, file) ?: return null
        val resized = ensure128(full)
        try {
            thumb.parentFile.mkdirs()
            ImageIO.write(resized, "png", thumb)
        } catch (e: Exception) {
            plugin.logger.warning("Could not write thumbnail for $file of $owner: ${e.message}")
        }
        return resized
    }

    /** Full-resolution image as stored (no resize). Null when missing/unreadable. */
    fun loadFullImage(owner: UUID, file: String): BufferedImage? {
        val f = imageFile(owner, file)
        if (!f.isFile) return null
        return try {
            f.inputStream().use { ImageIO.read(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** Raw full-resolution PNG bytes. Null when missing/unreadable. */
    fun fullImageBytes(owner: UUID, file: String): ByteArray? {
        val f = imageFile(owner, file)
        if (!f.isFile) return null
        return try {
            f.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    private val hashCache = mutableMapOf<String, String>()
    private val hashCacheLock = Any()

    /** SHA-256 hex of the stored full-resolution PNG (for client cache checks). */
    fun fullImageHash(owner: UUID, file: String): String? {
        val key = "$owner/$file"
        synchronized(hashCacheLock) { hashCache[key]?.let { return it } }
        val bytes = fullImageBytes(owner, file) ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = digest.joinToString("") { "%02x".format(it) }
        synchronized(hashCacheLock) {
            if (hashCache.size > 1024) hashCache.clear()
            hashCache[key] = hex
        }
        return hex
    }

    private fun dropHash(owner: UUID, file: String) {
        synchronized(hashCacheLock) { hashCache.remove("$owner/$file") }
    }

    fun deleteImage(owner: UUID, file: String): Boolean {
        val f = imageFile(owner, file)
        if (!f.isFile) return false
        val deleted = f.delete()
        try {
            thumbFile(owner, file).delete()
        } catch (_: Exception) {
        }
        dropHash(owner, file)
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
        try {
            File(dir, "thumbs").deleteRecursively()
        } catch (_: Exception) {
        }
        synchronized(hashCacheLock) {
            hashCache.keys.removeIf { it.startsWith("$owner/") }
        }
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

        if (decoded.width < 1 || decoded.height < 1 ||
            decoded.width > MAX_IMAGE_DIMENSION || decoded.height > MAX_IMAGE_DIMENSION
        ) {
            return Result.Error("<red>Das Bild hat eine ungültige Größe (max. $MAX_IMAGE_DIMENSION×$MAX_IMAGE_DIMENSION Pixel)!</red>")
        }

        return try {
            val dir = playerDir(player.uniqueId)
            dir.mkdirs()
            val target = imageFile(player.uniqueId, file)
            // Re-check after decode (TOCTOU-safe enough for this use case).
            if (target.exists()) {
                return Result.Error("<red>Du hast bereits ein Bild mit diesem Namen! Wähle einen anderen Dateinamen.</red>")
            }
            // Full resolution is stored as-is; the 128x128 map thumbnail is
            // derived so vanilla maps (and Bedrock via Geyser) keep working.
            // Modded Java clients fetch the full image on demand.
            ImageIO.write(decoded, "png", target)
            val thumbDir = File(dir, "thumbs")
            thumbDir.mkdirs()
            ImageIO.write(ensure128(decoded), "png", File(thumbDir, "$file.png"))
            dropHash(player.uniqueId, file)
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
        // File name goes into the lore, NOT the display name: item frames
        // render the display name above the map, lore stays hidden.
        meta.lore(listOf(mm("<white>${escape(file)}</white>")))
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
            // Warm the thumbnail on the main thread (also migrates images
            // stored before thumbnails existed) so renderers never write files.
            loadImage(ref.owner, ref.file)
            view.renderers.clear()
            view.addRenderer(ImageMapRenderer(plugin, ref.owner, ref.file))
            restored++
        }
        if (restored > 0 || dropped > 0) {
            plugin.logger.info("Restored $restored image map(s), dropped $dropped stale entr(ies).")
        }
    }

    /**
     * Adopts a previously untracked image map (issued before tracking
     * existed): our maps carry their file name either as a single white lore
     * line (current scheme) or as a white display name (legacy scheme, from
     * before the name moved into the lore for item frames), so a filled map
     * with such a marker can be matched back to its file. Only adopts when
     * unambiguous — the holder's own file wins, otherwise exactly one owner
     * may have the file. Anvil-renamed vanilla maps are gray and never match,
     * so real terrain maps are never touched. Adopted items are normalized to
     * the lore scheme (display name cleared). Idempotent.
     *
     * @return true when the map is now tracked (or already was)
     */
    fun tryAdoptMap(player: Player, stack: ItemStack): Boolean {
        if (stack.type != Material.FILLED_MAP) return false
        val meta = stack.itemMeta as? MapMeta ?: return false
        val view = meta.mapView ?: return false
        val tracked = plugin.store.imageMapStore.get(view.id) != null
        val fileName = extractOurFileName(meta) ?: return false

        val ownerId = if (hasImage(player.uniqueId, fileName)) {
            player.uniqueId
        } else {
            val owners = getAllKnownPlayers().filter { hasImage(it, fileName) }
            if (owners.size != 1) return false
            owners.first()
        }

        if (!tracked) {
            view.renderers.clear()
            view.addRenderer(ImageMapRenderer(plugin, ownerId, fileName))
            plugin.store.imageMapStore.putMap(view.id, ownerId, fileName)
        }
        normalizeMapMeta(stack, meta, fileName)
        return true
    }

    /**
     * Reads our file name marker off a map item: either the single white lore
     * line (current) or the white display name (legacy). Returns null for
     * anything else, including anvil-renamed (gray) vanilla maps.
     */
    private fun extractOurFileName(meta: MapMeta): String? {
        meta.lore()?.let { lore ->
            if (lore.size == 1) {
                val line = lore[0] as? TextComponent ?: return null
                if (line.color() == NamedTextColor.WHITE && line.children().isEmpty()) {
                    val fileName = line.content()
                    if (isValidFileName(fileName)) return fileName
                }
            }
            return null
        }
        val name = meta.displayName() as? TextComponent ?: return null
        if (name.color() != NamedTextColor.WHITE || name.children().isNotEmpty()) return null
        val fileName = name.content()
        if (!isValidFileName(fileName)) return null
        return fileName
    }

    /** Rewrites a map item to the lore scheme (display name cleared). */
    private fun normalizeMapMeta(stack: ItemStack, meta: MapMeta, fileName: String) {
        if (meta.displayName() == null) {
            val lore = meta.lore()
            if (lore != null && lore.size == 1) return
        }
        meta.displayName(null)
        meta.lore(listOf(mm("<white>${escape(fileName)}</white>")))
        stack.itemMeta = meta
    }

    /** Scans the player's belongings for image maps, adopts and normalizes them. */
    fun adoptPlayerMaps(player: Player): Int {        var adopted = 0
        val seen = mutableSetOf<Int>()
        val all = player.inventory.contents + player.enderChest.contents
        for (stack in all) {
            if (stack == null || stack.type != Material.FILLED_MAP) continue
            val meta = stack.itemMeta as? MapMeta ?: continue
            val id = meta.mapView?.id ?: continue
            if (!seen.add(id)) continue
            if (tryAdoptMap(player, stack)) adopted++
        }
        return adopted
    }

    // ------------------------------------------------------------------
    // full-resolution download (diawars:image_req -> diawars:image_data)
    // ------------------------------------------------------------------
    //
    // Modded Java clients pull the stored full-resolution PNG on demand and
    // render it instead of the 128x128 map texture. Vanilla and Bedrock
    // (Geyser) clients never request anything, so they keep seeing the maps.

    private data class Download(
        val mapId: Int?,
        val owner: UUID,
        val file: String,
        val hash: String,
        val chunks: List<String>,
        var next: Int = 0,
    )

    private val downloadQueues = mutableMapOf<UUID, ArrayDeque<Download>>()
    private var downloadPump: org.bukkit.scheduler.BukkitTask? = null
    private var downloadPumpRunning = false

    /**
     * Queues the full-resolution image for download. Resolves owner/file
     * either directly or via the map id ([de.davidsw.diawars.stores.ImageMapStore]).
     * A matching [knownHash] short-circuits the transfer ("unchanged").
     */
    fun requestFullImage(player: Player, mapId: Int?, owner: UUID?, file: String?, knownHash: String?) {
        val resolvedOwner: UUID
        val resolvedFile: String
        if (owner != null && file != null && isValidFileName(file)) {
            resolvedOwner = owner
            resolvedFile = file
        } else if (mapId != null) {
            val ref = plugin.store.imageMapStore.get(mapId)
            if (ref == null) {
                sendDownloadJson(player, errorJson(mapId, null, null, "unknown"))
                return
            }
            resolvedOwner = ref.owner
            resolvedFile = ref.file
        } else {
            return
        }

        val bytes = fullImageBytes(resolvedOwner, resolvedFile)
        if (bytes == null) {
            if (mapId != null) plugin.store.imageMapStore.removeMap(mapId)
            sendDownloadJson(player, errorJson(mapId, resolvedOwner, resolvedFile, "unknown"))
            return
        }
        val hash = fullImageHash(resolvedOwner, resolvedFile) ?: run {
            sendDownloadJson(player, errorJson(mapId, resolvedOwner, resolvedFile, "unknown"))
            return
        }
        if (knownHash != null && knownHash.equals(hash, ignoreCase = true)) {
            sendDownloadJson(player, unchangedJson(mapId, resolvedOwner, resolvedFile, hash))
            return
        }

        val base64 = Base64.getEncoder().encodeToString(bytes)
        val total = (base64.length + MAX_CHUNK_CHARS - 1) / MAX_CHUNK_CHARS
        if (total > MAX_PARTS) {
            plugin.logger.warning("Image $resolvedFile of $resolvedOwner exceeds download chunk budget ($total parts).")
            sendDownloadJson(player, errorJson(mapId, resolvedOwner, resolvedFile, "too-big"))
            return
        }
        val chunks = (0 until total).map { i ->
            base64.substring(i * MAX_CHUNK_CHARS, minOf(base64.length, (i + 1) * MAX_CHUNK_CHARS))
        }
        synchronized(downloadQueues) {
            downloadQueues.getOrPut(player.uniqueId) { ArrayDeque() }
                .add(Download(mapId, resolvedOwner, resolvedFile, hash, chunks))
        }
        startDownloadPump()
    }

    fun cancelDownloads(playerId: UUID) {
        synchronized(downloadQueues) { downloadQueues.remove(playerId) }
    }

    private fun startDownloadPump() {
        synchronized(downloadQueues) {
            if (downloadPump != null) return
            downloadPump = plugin.server.scheduler.runTaskTimer(plugin, Runnable { pumpDownloads() }, 1L, 1L)
        }
    }

    private fun pumpDownloads() {
        val done = mutableListOf<UUID>()
        synchronized(downloadQueues) {
            for ((playerId, queue) in downloadQueues) {
                val player = plugin.server.getPlayer(playerId)
                if (player == null || !player.isOnline) {
                    done.add(playerId)
                    continue
                }
                var sent = 0
                while (sent < DOWNLOAD_CHUNKS_PER_TICK) {
                    val head = queue.peekFirst() ?: break
                    if (head.next >= head.chunks.size) {
                        queue.removeFirst()
                        continue
                    }
                    val json = dataJson(head, head.next, head.chunks.size)
                    head.next++
                    sent++
                    sendDownloadJson(player, json)
                    if (head.next >= head.chunks.size) queue.removeFirst()
                }
                if (queue.isEmpty()) done.add(playerId)
            }
            done.forEach { downloadQueues.remove(it) }
            if (downloadQueues.isEmpty()) {
                downloadPump?.cancel()
                downloadPump = null
            }
        }
    }

    private fun dataJson(d: Download, part: Int, total: Int): String {
        val root = JsonObject()
        if (d.mapId != null) root.addProperty("mapId", d.mapId)
        root.addProperty("owner", d.owner.toString())
        root.addProperty("file", d.file)
        root.addProperty("hash", d.hash)
        root.addProperty("part", part)
        root.addProperty("total", total)
        root.addProperty("data", d.chunks[part])
        return root.toString()
    }

    private fun unchangedJson(mapId: Int?, owner: UUID, file: String, hash: String): String {
        val root = JsonObject()
        if (mapId != null) root.addProperty("mapId", mapId)
        root.addProperty("owner", owner.toString())
        root.addProperty("file", file)
        root.addProperty("hash", hash)
        root.addProperty("unchanged", true)
        return root.toString()
    }

    private fun errorJson(mapId: Int?, owner: UUID?, file: String?, reason: String): String {
        val root = JsonObject()
        if (mapId != null) root.addProperty("mapId", mapId)
        if (owner != null) root.addProperty("owner", owner.toString())
        if (file != null) root.addProperty("file", file)
        root.addProperty("error", reason)
        return root.toString()
    }

    private fun sendDownloadJson(player: Player, json: String) {
        try {
            player.sendPluginMessage(plugin, ImageListener.DATA_CHANNEL, ImageListener.writeVarIntPrefixedUtf8(json))
        } catch (e: Exception) {
            plugin.logger.warning("Could not send image data to ${player.name}: ${e.message}")
            cancelDownloads(player.uniqueId)
        }
    }

    /**
     * Asks the player's client mod to open the image upload UI (via the
     * [ImageListener.OPEN_CHANNEL] signal). Every player needs the mod anyway,
     * so clients without it simply ignore the packet.
     */
    fun requestUploadUi(player: Player) {
        if (BedrockUtil.isBedrock(player)) {
            player.sendMessage(mm("<red>Die Bilder-Funktion ist auf Bedrock nicht verfügbar.</red>"))
            return
        }
        player.closeInventory()
        try {
            player.sendPluginMessage(plugin, ImageListener.OPEN_CHANNEL, ByteArray(0))
        } catch (e: Exception) {
            plugin.logger.warning("Could not request image upload UI for ${player.name}: ${e.message}")
            player.sendMessage(mm("<red>Der Upload konnte nicht geöffnet werden! Stelle sicher, dass du die Diawars-Client-Mod installiert hast.</red>"))
        }
    }
}
