package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.listeners.ImageListener
import de.davidsw.diawars.stores.ReportEntry
import de.davidsw.diawars.stores.ReportTargetType
import de.davidsw.diawars.util.BedrockUtil
import de.davidsw.diawars.util.MiniMessageHelper.escape
import de.davidsw.diawars.util.MiniMessageHelper.mm
import com.google.gson.JsonObject
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Player reports (images and players), with optional screenshot attachment.
 *
 * <p>Flow: the player starts a report (command or menu) for a target — an
 * image map in the main hand or another online player. A pending target is
 * remembered server-side; the reason comes through the client-mod text input
 * dialog ([TextInputManager.Prompt.REPORT_REASON], the mod runs
 * {@code /report reason <text>}). The reason completes the report and opens a
 * short window in which the reporter can append a screenshot of what they
 * currently see (<code>/report shot</code>): the server asks the client mod to
 * capture the screen, which then uploads the PNG through the regular chunked
 * image upload channels tagged with a {@code report} field, and the upload is
 * stored under <code>reports/&lt;id&gt;.png</code> instead of the gallery.</p>
 *
 * <p>Bedrock players can report (chat-typed reason via the TextInputManager
 * fallback) but never attach screenshots (no client mod); documented parity
 * gap like the rest of the image feature.</p>
 *
 * <p>Admins review with {@code /admin report list|view|shot|resolve}; the
 * screenshot is handed out as a map rendered by
 * [ReportShotRenderer].</p>
 */
class ReportManager(private val plugin: Diawars) {

    companion object {
        /** Server -> client signal asking the mod to capture a screenshot. */
        const val SHOT_CHANNEL = "diawars:screenshot"

        /** Upload-JSON field marking offer/chunks as a report screenshot. */
        const val REPORT_FIELD = "report"

        /** How long the player has to answer the reason dialog. */
        private const val PENDING_TIMEOUT_MILLIS = 2 * 60_000L

        /** How long after the reason a screenshot may still be attached. */
        const val SHOT_WINDOW_MILLIS = 10 * 60_000L

        /** Upload session timeout in millis (mirrors ImageManager). */
        private const val SESSION_TIMEOUT_MILLIS = 120_000L

        private val DATE_FORMAT = SimpleDateFormat("dd.MM.yyyy HH:mm")
    }

    sealed interface Result {
        data class Success(val message: String) : Result
        data class Error(val message: String) : Result
    }

    sealed interface Target {
        data class Image(val owner: UUID, val file: String) : Target
        data class Player(val playerId: UUID, val name: String) : Target
    }

    private class Pending(val target: Target) {
        val createdAt = System.currentTimeMillis()
    }

    private val pending = mutableMapOf<UUID, Pending>()

    private class ShotSession(
        val reportId: String,
        val file: String,
        val total: Int,
        /** Announced byte count. */
        val bytesExpected: Long,
        /** Announced SHA-256 hex. */
        val hashExpected: String?,
    ) {
        val received = mutableSetOf<Int>()
        var lastUpdate = System.currentTimeMillis()
    }

    private val sessions = mutableMapOf<UUID, ShotSession>()
    private val sessionLock = Any()

    init {
        // Orphaned part files from a crash never belong to a session again.
        try {
            tmpRoot().deleteRecursively()
        } catch (_: Exception) {
        }
    }

    private val store get() = plugin.store.reportStore

    // ------------------------------------------------------------------
    // files
    // ------------------------------------------------------------------

    private fun tmpRoot(): File = File(reportsDir(), ".tmp")

    private fun tmpDir(playerId: UUID, file: String): File = File(File(tmpRoot(), playerId.toString()), file)

    private fun deleteSessionFiles(playerId: UUID, session: ShotSession) {
        try {
            tmpDir(playerId, session.file).deleteRecursively()
        } catch (_: Exception) {
        }
    }

    fun reportsDir(): File = File(plugin.dataFolder, "reports")

    private fun shotFile(id: String): File = File(reportsDir(), "$id.png")

    /** Decoded 128x128 preview of the attached screenshot for map rendering. */
    fun loadShotImage(id: String): java.awt.image.BufferedImage? {
        val f = shotFile(id)
        if (!f.isFile) return null
        val decoded = try {
            f.inputStream().use { ImageIO.read(it) }
        } catch (_: Exception) {
            null
        } ?: return null
        return plugin.imageManager.ensure128(decoded)
    }

    // ------------------------------------------------------------------
    // starting reports (commands + menus)
    // ------------------------------------------------------------------

    /** Reports the image of the map the player holds in their main hand. */
    fun startImageReport(player: Player): Result {
        val stack: ItemStack = player.inventory.itemInMainHand
        val ref = plugin.imageManager.findImageRef(stack)
            ?: return Result.Error("<red>Halte die Bild-Karte, die du melden willst, in der Haupthand!</red>")
        setPending(player.uniqueId, Target.Image(ref.first, ref.second))
        plugin.textInputManager.openPrompt(player, TextInputManager.Prompt.REPORT_REASON)
        return Result.Success("<gray>Bitte gib den Grund für deinen Report an.</gray>")
    }

    /** Reports an online player by name. */
    fun startPlayerReport(player: Player, targetName: String): Result {
        val target = Bukkit.getPlayerExact(targetName)
            ?: return Result.Error("<red>Kein Spieler mit dem Namen <yellow>${escape(targetName)}</yellow> ist online!</red>")
        if (target.uniqueId == player.uniqueId) {
            return Result.Error("<red>Du kannst dich nicht selbst melden!</red>")
        }
        setPending(player.uniqueId, Target.Player(target.uniqueId, target.name))
        plugin.textInputManager.openPrompt(player, TextInputManager.Prompt.REPORT_REASON)
        return Result.Success("<gray>Bitte gib den Grund für deinen Report an.</gray>")
    }

    private fun setPending(playerId: UUID, target: Target) {
        synchronized(pending) { pending[playerId] = Pending(target) }
    }

    /** Drops the pending target and any shot session (player quit / cleanup). */
    fun clearPlayer(playerId: UUID) {
        synchronized(pending) { pending.remove(playerId) }
        synchronized(sessionLock) {
            sessions.remove(playerId)?.let { deleteSessionFiles(playerId, it) }
        }
    }

    // ------------------------------------------------------------------
    // reason dialog (runs /report reason <text>)
    // ------------------------------------------------------------------

    fun submitReason(player: Player, reason: String): Result {
        val body = reason.trim()
        if (body.isBlank()) {
            return Result.Error("<red>Bitte gib einen Grund für den Report an!</red>")
        }
        val pendingReport = synchronized(pending) { pending[player.uniqueId] }
            ?: return Result.Error(
                "<red>Du hast gerade keinen laufenden Report! Starte einen über das Menü, " +
                        "<yellow>/report image</yellow> oder <yellow>/report player &lt;name&gt;</yellow>.</red>",
            )
        if (System.currentTimeMillis() - pendingReport.createdAt > PENDING_TIMEOUT_MILLIS) {
            synchronized(pending) { pending.remove(player.uniqueId) }
            return Result.Error("<red>Die Eingabe hat zu lange gedauert — bitte starte den Report erneut.</red>")
        }
        synchronized(pending) { pending.remove(player.uniqueId) }

        val entry = when (val target = pendingReport.target) {
            is Target.Image -> store.addReport(
                player.uniqueId, ReportTargetType.IMAGE, null, null, target.owner, target.file, body,
            )

            is Target.Player -> store.addReport(
                player.uniqueId, ReportTargetType.PLAYER, target.playerId, target.name, null, null, body,
            )
        }

        notifyAdmins(entry, player.name)

        val hint = if (BedrockUtil.isBedrock(player)) {
            "<gray>Ein Screenshot-Anhang ist auf Bedrock nicht möglich.</gray>"
        } else {
            "<gray>Du kannst binnen 10 Minuten einen Screenshot deines aktuellen Bildschirms anhängen: " +
                    "<yellow><click:suggest_command:'/report shot'>/report shot</click></yellow></gray>"
        }
        return Result.Success("<green>✓ Report <gold>#${entry.id}</gold> gemeldet!</green> $hint")
    }

    private fun notifyAdmins(entry: ReportEntry, reporterName: String) {
        plugin.server.onlinePlayers
            .filter { it.hasPermission("diawars.admin") }
            .forEach {
                it.sendMessage(mm(
                    "<yellow>⚠ <gold>$reporterName</gold> hat ${targetLabel(entry)} gemeldet " +
                            "(ID: <white>${entry.id}</white>). Nutze <white>/admin report list</white> für Details.</yellow>",
                ))
            }
    }

    /** Short description of the reported target for admin messages. */
    private fun targetLabel(entry: ReportEntry): String = when (entry.type) {
        ReportTargetType.IMAGE -> "<red>Bild</red> <white>${escape(entry.targetFile ?: "?")}</white> " +
                "<gray>(von " + displayNameOf(entry.targetOwner) + ")</gray>"

        ReportTargetType.PLAYER -> "<red>Spieler</red> <white>${escape(entry.targetName ?: "?")}</white>"
    }

    private fun displayNameOf(id: UUID?): String =
        if (id == null) "?" else de.davidsw.diawars.util.PlayerNameCache.nameOf(id)

    // ------------------------------------------------------------------
    // screenshot attachment (diawars:screenshot -> offer/accept -> chunks)
    // ------------------------------------------------------------------

    /**
     * Marks that the reporter wants to attach a screenshot: validates the
     * open report window and the client payload channel, then asks the mod to
     * capture the screen and upload it (tagged with the report id).
     */
    fun requestShot(player: Player): Result {
        if (BedrockUtil.isBedrock(player)) {
            return Result.Error("<red>Screenshot-Anhänge sind auf Bedrock nicht möglich.</red>")
        }
        val now = System.currentTimeMillis()
        val entry = store.getAll()
            .filter { it.reporter == player.uniqueId && !it.resolved && !it.shot && now - it.reportedAt * 1000 <= SHOT_WINDOW_MILLIS }
            .maxByOrNull { it.reportedAt }
            ?: return Result.Error(
                "<red>Du hast keinen Report, an den noch ein Screenshot angehängt werden kann " +
                        "(Zeitfenster: ${SHOT_WINDOW_MILLIS / 60_000} Minuten ab Report).</red>",
            )
        if (SHOT_CHANNEL !in player.listeningPluginChannels) {
            return Result.Error("<red>Der Screenshot-Upload benötigt die aktuelle Diawars-Client-Mod!</red>")
        }
        synchronized(sessionLock) {
            purgeExpiredLocked()
            if (sessions[player.uniqueId] != null) {
                return Result.Error("<red>Bereits ein Screenshot-Upload aktiv, bitte warten!</red>")
            }
        }
        val doc = JsonObject().apply {
            addProperty("report", entry.id)
            addProperty("file", "report-${entry.id}")
        }
        try {
            player.sendPluginMessage(plugin, SHOT_CHANNEL, ImageListener.writeVarIntPrefixedUtf8(doc.toString()))
        } catch (e: Exception) {
            plugin.logger.warning("Could not request screenshot from ${player.name}: ${e.message}")
            return Result.Error("<red>Der Screenshot konnte nicht angefordert werden!</red>")
        }
        return Result.Success("<gray>📷 Screenshot wird aufgenommen und hochgeladen …</gray>")
    }

    /**
     * Handles a report-screenshot upload offer (routed here by
     * [ImageListener] when the offer carries the {@code report} field).
     * Always answers on {@code diawars:image_accept}.
     */
    fun handleShotOffer(player: Player, reportId: String, file: String, bytes: Long, total: Int, hash: String?) {
        val entry = store.get(reportId)
        val reject: String? = when {
            entry == null || entry.reporter != player.uniqueId || entry.resolved ->
                "Dieser Report ist unbekannt, abgeschlossen oder gehört dir nicht."

            entry.shot -> "An diesem Report hängt bereits ein Screenshot."

            System.currentTimeMillis() - entry.reportedAt * 1000 > SHOT_WINDOW_MILLIS ->
                "Zeitfenster überschritten (max. ${SHOT_WINDOW_MILLIS / 60_000} Minuten ab Report)."

            bytes < 1 || bytes > ImageManager.MAX_UPLOAD_BYTES || total < 1 ||
                    total > ImageManager.MAX_NEGOTIATED_PARTS || total.toLong() != ImageManager.expectedParts(bytes) ->
                "Ungültige Bildgröße (max. ${ImageManager.MAX_UPLOAD_BYTES / 1024 / 1024} MB, Teile passen nicht zur Größe)!"

            hash != null && !hash.matches(Regex("[0-9a-fA-F]{64}")) -> "Ungültige Bilddaten (Hash)!"

            synchronized(sessionLock) { sessions[player.uniqueId] != null } -> "Bereits ein Upload aktiv, bitte warten."

            else -> null
        }
        if (reject != null) {
            sendAccept(player, file, false, null, reject)
            return
        }
        synchronized(sessionLock) {
            purgeExpiredLocked()
            sessions[player.uniqueId]?.let { deleteSessionFiles(player.uniqueId, it) }
            sessions[player.uniqueId] = ShotSession(reportId, file, total, bytes, hash?.lowercase())
        }
        sendAccept(player, file, true, total, null)
    }

    private fun sendAccept(player: Player, file: String, accepted: Boolean, total: Int?, error: String?) {
        val root = JsonObject()
        root.addProperty("file", file)
        root.addProperty("accepted", accepted)
        if (total != null) root.addProperty("total", total)
        if (error != null) root.addProperty("error", error)
        try {
            player.sendPluginMessage(plugin, ImageListener.ACCEPT_CHANNEL, ImageListener.writeVarIntPrefixedUtf8(root.toString()))
        } catch (e: Exception) {
            plugin.logger.warning("Could not send image accept to ${player.name}: ${e.message}")
            if (!accepted) synchronized(sessionLock) { sessions.remove(player.uniqueId)?.let { deleteSessionFiles(player.uniqueId, it) } }
        }
    }

    /**
     * Handles one report-screenshot chunk (routed by [ImageListener] when the
     * chunk carries the {@code report} field). Returns a [Result] only on
     * fatal errors and completion; null keeps waiting for more parts.
     */
    fun handleShotChunk(player: Player, reportId: String, file: String, part: Int, total: Int, data: String): Result? {
        if (data.length > ImageManager.MAX_CHUNK_CHARS) {
            failSession(player.uniqueId)
            return Result.Error("<red>Ungültige Screenshot-Daten (Teil zu groß)!</red>")
        }
        val playerId = player.uniqueId
        synchronized(sessionLock) {
            purgeExpiredLocked()
            val session = sessions[playerId]
            if (session == null || session.reportId != reportId || session.file != file || session.total != total) {
                // No matching session: leftover from a failed offer or stale
                // upload — drop it silently so retrying can start fresh.
                return null
            }
            if (part < 0 || part >= session.total) {
                failSession(playerId)
                return Result.Error("<red>Ungültige Screenshot-Daten (Teilnummer)!</red>")
            }
            val decoded = try {
                Base64.getDecoder().decode(data)
            } catch (_: IllegalArgumentException) {
                failSession(playerId)
                return Result.Error("<red>Ungültige Screenshot-Daten (Base64)!</red>")
            }
            try {
                val dir = tmpDir(playerId, session.file)
                dir.mkdirs()
                File(dir, "p$part.bin").writeBytes(decoded)
            } catch (e: Exception) {
                plugin.logger.warning("Could not stage screenshot part for ${player.name}: ${e.message}")
                failSession(playerId)
                return Result.Error("<red>Der Screenshot konnte nicht zwischengespeichert werden!</red>")
            }
            session.lastUpdate = System.currentTimeMillis()
            session.received.add(part)

            if (session.received.size < session.total) return null
            sessions.remove(playerId)
            return finishShot(player, session)
        }
    }

    /** Assembles the staged parts and stores the screenshot. Deletes staging files. */
    private fun finishShot(player: Player, session: ShotSession): Result {
        val entry = store.get(session.reportId)
        if (entry == null || entry.reporter != player.uniqueId || entry.resolved || entry.shot) {
            deleteSessionFiles(player.uniqueId, session)
            return Result.Error("<red>Der Report ist nicht mehr offen (Screenshot verworfen).</red>")
        }

        val bytes = try {
            assembleParts(player.uniqueId, session)
        } catch (e: Exception) {
            plugin.logger.warning("Could not assemble screenshot for ${player.name}: ${e.message}")
            deleteSessionFiles(player.uniqueId, session)
            return Result.Error("<red>Ungültige Screenshot-Daten (Teile unvollständig)!</red>")
        }
        deleteSessionFiles(player.uniqueId, session)
        if (bytes.isEmpty() || bytes.size > ImageManager.MAX_UPLOAD_BYTES) {
            return Result.Error("<red>Der Screenshot ist zu groß oder leer!</red>")
        }
        if (bytes.size.toLong() != session.bytesExpected) {
            return Result.Error("<red>Ungültige Screenshot-Daten (Größe weicht vom Angebot ab)!</red>")
        }
        if (session.hashExpected != null && !sha256Hex(bytes).equals(session.hashExpected, ignoreCase = true)) {
            return Result.Error("<red>Ungültige Screenshot-Daten (Prüfsumme stimmt nicht)!</red>")
        }

        val decoded = try {
            ByteArrayInputStream(bytes).use { ImageIO.read(it) }
        } catch (_: Exception) {
            null
        } ?: return Result.Error("<red>Der Screenshot ist kein gültiges Bild (PNG erwartet)!</red>")
        if (decoded.width < 1 || decoded.height < 1 ||
            decoded.width > ImageManager.MAX_IMAGE_DIMENSION || decoded.height > ImageManager.MAX_IMAGE_DIMENSION
        ) {
            return Result.Error("<red>Der Screenshot hat eine ungültige Größe!</red>")
        }

        return try {
            val target = shotFile(entry.id)
            target.parentFile?.mkdirs()
            ImageIO.write(decoded, "png", target)
            if (!store.setShot(entry.id)) {
                target.delete()
                return Result.Error("<red>An diesem Report hängt bereits ein Screenshot.</red>")
            }
            Result.Success("<green>✓ Screenshot an Report <gold>#${entry.id}</gold> angehängt!</green>")
        } catch (e: Exception) {
            plugin.logger.warning("Could not save screenshot for report ${entry.id}: ${e.message}")
            Result.Error("<red>Der Screenshot konnte nicht gespeichert werden!</red>")
        }
    }

    /** Concatenates staged part files 0..total-1 in order. */
    private fun assembleParts(playerId: UUID, session: ShotSession): ByteArray {
        val dir = tmpDir(playerId, session.file)
        val out = java.io.ByteArrayOutputStream()
        for (i in 0 until session.total) {
            val part = File(dir, "p$i.bin")
            if (!part.isFile) throw IllegalStateException("missing part $i")
            part.inputStream().use { it.copyTo(out) }
        }
        return out.toByteArray()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun purgeExpiredLocked() {
        val now = System.currentTimeMillis()
        val it = sessions.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (now - entry.value.lastUpdate > SESSION_TIMEOUT_MILLIS) {
                deleteSessionFiles(entry.key, entry.value)
                it.remove()
            }
        }
    }

    private fun failSession(playerId: UUID) {
        synchronized(sessionLock) {
            sessions.remove(playerId)?.let { deleteSessionFiles(playerId, it) }
        }
    }

    // ------------------------------------------------------------------
    // admin review
    // ------------------------------------------------------------------

    fun formatList(): List<String> {
        val reports = store.getOpen()
        if (reports.isEmpty()) {
            return listOf("<gray>Es gibt derzeit keine offenen Reports.</gray>")
        }
        val lines = mutableListOf("<gold>=== Offene Reports (${reports.size}) ===</gold>")
        reports.forEach { report ->
            lines += "<gray>- <yellow>#${report.id}</yellow> <white>${displayNameOf(report.reporter)}</white>: " +
                    "${targetLabel(report)} ${if (report.shot) "<dark_aqua>[Screenshot]" else ""}</gray>"
        }
        return lines
    }

    fun formatView(id: String): Result {
        val report = store.get(id) ?: return Result.Error("<red>Dieser Report existiert nicht!</red>")
        val lines = mutableListOf(
            "<gold>=== Report #$id ===</gold>",
            "<gray>Von:</gray> <white>${displayNameOf(report.reporter)}</white> <gray>am ${DATE_FORMAT.format(Date(report.reportedAt * 1000))}</gray>",
            "<gray>Ziel:</gray> ${targetLabel(report)}",
            "<gray>Grund:</gray> <white>${escape(report.reason)}</white>",
            "<gray>Screenshot:</gray> " + if (report.shot) "<green>angehängt</green> <gray>(</gray><yellow>/admin report shot $id</yellow><gray>)</gray>" else "<red>nicht vorhanden</red>",
            "<gray>Status:</gray> " + (if (report.resolved) "<green>behandelt</green>" else "<yellow>offen</yellow>"),
        )
        return Result.Success(lines.joinToString("\n"))
    }

    fun resolve(id: String): Result {
        return if (store.resolve(id)) {
            Result.Success("<green>✓ Report <gold>#$id</gold> wurde als behandelt markiert.</green>")
        } else {
            Result.Error("<red>Dieser Report existiert nicht oder wurde bereits behandelt!</red>")
        }
    }

    /**
     * Hands the admin a map rendering the attached screenshot. Report shot
     * maps are review handouts: deliberately not tracked in ImageMapStore
     * (they must not appear in image browsing/adoption), the renderer paints
     * once and the pixels persist in the map data.
     */
    fun giveShotMap(admin: Player, id: String): Result {
        val report = store.get(id) ?: return Result.Error("<red>Dieser Report existiert nicht!</red>")
        if (!report.shot || !shotFile(id).isFile) {
            return Result.Error("<red>Dieser Report hat keinen Screenshot-Anhang!</red>")
        }
        val mapView = Bukkit.createMap(admin.world)
        mapView.renderers.clear()
        mapView.addRenderer(ReportShotRenderer(this, id))
        val stack = ItemStack(Material.FILLED_MAP)
        val meta = stack.itemMeta as MapMeta
        meta.mapView = mapView
        // Display name (not lore): a lore marker would look adoptable — see ImageManager.tryAdoptMap.
        meta.displayName(mm("<gold><bold>Report #$id</bold></gold> <dark_gray>Screenshot</dark_gray>"))
        stack.itemMeta = meta
        val leftover = admin.inventory.addItem(stack)
        if (leftover.isEmpty()) {
            return Result.Success("<green>✓ Screenshot-Karte für Report <gold>#$id</gold> erhalten!</green>")
        }
        admin.world.dropItemNaturally(admin.location, stack)
        return Result.Success("<green>✓ Screenshot-Karte erhalten! (Inventar war voll, Karte liegt am Boden.)</green>")
    }

    /** Join notice for admins with open reports (mirrors BugManager). */
    fun handleAdminJoin(admin: Player) {
        if (!admin.hasPermission("diawars.admin")) return
        if (store.hasUnread(admin.uniqueId)) {
            val count = store.getOpen().size
            admin.sendMessage(mm("<yellow>⚠ Es gibt <gold>$count</gold> offene(n) Report(s). Nutze <white>/admin report list</white> um sie anzusehen.</yellow>"))
        }
    }
}
