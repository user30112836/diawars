package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.listeners.ImageListener
import de.davidsw.diawars.stores.ReportEntry
import de.davidsw.diawars.stores.ReportSource
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
 * Player reports for images, players, screenshots and free-text reasons —
 * any combination, at least one aspect.
 *
 * <p>Two submission paths:</p>
 * <ul>
 *   <li><b>Menu + client mod</b> ([ReportSource.MENU]): the "Melden" main-menu
 *   button asks the mod to open the report dialog
 *   ([DIALOG_CHANNEL], JSON: hand-image availability + reason cap). The mod
 *   shows editable fields for player name and reason plus checkboxes for the
 *   screenshot (pre-checked) and the map in the main hand (pre-checked when
 *   the server saw one). On submit the dialog sends everything at once to
 *   [SUBMIT_CHANNEL]; the server re-validates (online player, hand image,
 *   non-empty report) and requests the screenshot immediately when chosen.</li>
 *   <li><b>Command line</b> ([ReportSource.COMMAND], also for Bedrock and
 *   players with an outdated mod): {@code /report <player>|- [grund]}. The
 *   screenshot is always taken when the mod can do it and the hand image is
 *   appended if possible.</li>
 * </ul>
 *
 * <p>Screenshot upload reuses the regular chunked image pipeline tagged with
 * the {@code report} field (see [ImageListener]); the attachment is stored
 * under {@code reports/<id>.png} instead of the gallery. Admins review with
 * {@code /admin report list|view|shot|resolve}, where every report is marked
 * with its submission path; the screenshot is handed out as a map rendered by
 * [ReportShotRenderer].</p>
 */
class ReportManager(private val plugin: Diawars) {

    companion object {
        /** Server -> client signal asking the mod to capture a screenshot. */
        const val SHOT_CHANNEL = "diawars:screenshot"

        /** Server -> client signal to open the report dialog in the mod. */
        const val DIALOG_CHANNEL = "diawars:report_dialog"

        /** Client -> server submit of a completed report dialog. */
        const val SUBMIT_CHANNEL = "diawars:report_submit"

        /** Upload-JSON field marking offer/chunks as a report screenshot. */
        const val REPORT_FIELD = "report"

        /** Upper bound for the free-text reason (dialog field + store). */
        const val MAX_REASON_LENGTH = 240

        /** Upload session timeout in millis (mirrors ImageManager). */
        private const val SESSION_TIMEOUT_MILLIS = 120_000L

        private val DATE_FORMAT = SimpleDateFormat("dd.MM.yyyy HH:mm")
    }

    sealed interface Result {
        data class Success(val message: String) : Result
        data class Error(val message: String) : Result
    }

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

    /**
     * Raw screenshot PNG for the admin mod viewer (see ImageManager
     * requestAdminShot). Ids come from the admin channel, so they are
     * strictly validated: report-id charset only and no path tricks.
     */
    fun shotBytes(id: String): ByteArray? {
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) return null
        return try {
            val f = shotFile(id)
            if (f.isFile) f.readBytes() else null
        } catch (_: Exception) {
            null
        }
    }

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
    // opening the dialog (main-menu button)
    // ------------------------------------------------------------------

    /**
     * Opens the client-mod report dialog. Players without the dialog channel
     * (Bedrock, outdated mod, mod-less admins) are redirected to the command
     * line instead.
     */
    fun openDialog(player: Player) {
        if (BedrockUtil.isBedrock(player) || DIALOG_CHANNEL !in player.listeningPluginChannels) {
            player.sendMessage(mm(commandRedirect(player)))
            return
        }
        val doc = JsonObject().apply {
            addProperty("handImage", plugin.imageManager.findImageRef(player.inventory.itemInMainHand) != null)
            addProperty("reasonMax", MAX_REASON_LENGTH)
        }
        try {
            player.sendPluginMessage(plugin, DIALOG_CHANNEL, ImageListener.writeVarIntPrefixedUtf8(doc.toString()))
        } catch (e: Exception) {
            plugin.logger.warning("Could not open report dialog for ${player.name}: ${e.message}")
            player.sendMessage(mm(commandRedirect(player)))
            return
        }
        // The dialog must show world + chat, not the menu inventory.
        player.closeInventory()
    }

    /** How players without the mod can still report (chat-typed command). */
    private fun commandRedirect(player: Player): String = if (BedrockUtil.isBedrock(player)) {
        "<red>Reports schreibst du im Chat:</red> <yellow>/report &lt;spieler|-&gt; [grund]</yellow> " +
                "<gray>— eine Bild-Karte in der Hand wird automatisch angehängt.</gray>"
    } else {
        "<red>Der Report-Dialog benötigt die aktuelle Diawars-Client-Mod!</red> " +
                "<gray>Du kannst auch im Chat melden: <yellow>/report &lt;spieler|-&gt; [grund]</yellow> " +
                "(Bild-Karte in der Hand wird automatisch angehängt).</gray>"
    }

    // ------------------------------------------------------------------
    // dialog submit (client -> server, runs separately from commands)
    // ------------------------------------------------------------------

    /**
     * Completes a report submitted through the client-mod dialog. Player
     * target and hand image resolve server-side (the mod only asks); the
     * screenshot is requested immediately when chosen. At least one aspect
     * must be present.
     */
    fun handleSubmit(player: Player, playerName: String?, reason: String?, shotRequested: Boolean, handImageRequested: Boolean): Result {
        val body = clampReason(reason)
        var targetPlayer: UUID? = null
        var targetSnapshot: String? = null
        if (!playerName.isNullOrBlank()) {
            val target = Bukkit.getPlayerExact(playerName.trim())
                ?: return Result.Error("<red>Kein Spieler mit dem Namen <yellow>${escape(playerName.trim())}</yellow> ist online!</red>")
            if (target.uniqueId == player.uniqueId) {
                return Result.Error("<red>Du kannst dich nicht selbst melden!</red>")
            }
            targetPlayer = target.uniqueId
            targetSnapshot = target.name
        }
        // Server-Authoritative: a "hand image" is only ever the map in the main hand.
        val handRef = if (handImageRequested) plugin.imageManager.findImageRef(player.inventory.itemInMainHand) else null
        if (targetPlayer == null && handRef == null && body == null && !shotRequested) {
            return Result.Error("<red>Der Report ist leer — fülle mindestens einen Punkt aus!</red>")
        }

        val entry = store.addReport(
            reporter = player.uniqueId,
            source = ReportSource.MENU,
            targetPlayer = targetPlayer,
            targetName = targetSnapshot,
            targetOwner = handRef?.first,
            targetFile = handRef?.second,
            reason = body,
        )
        notifyAdmins(entry, player.name)

        val withShot = requestShot(player, entry.id, shotRequested)
        var message = buildSummary(entry, withShot)
        if (shotRequested && !withShot) {
            message += " <red>(Screenshot konnte nicht angefragt werden — Upload läuft bereits.)</red>"
        }
        return Result.Success(message)
    }

    // ------------------------------------------------------------------
    // command line submit (/report <player>|- [grund])
    // ------------------------------------------------------------------

    /**
     * Completes a report typed as a command. The screenshot is always taken
     * when the mod can do it and the hand image is appended if possible.
     */
    fun submitCommandReport(player: Player, targetName: String?, reason: String?): Result {
        val body = clampReason(reason)
        var targetPlayer: UUID? = null
        var targetSnapshot: String? = null
        if (!targetName.isNullOrBlank()) {
            val target = Bukkit.getPlayerExact(targetName)
                ?: return Result.Error(
                    "<red>Kein Spieler mit dem Namen <yellow>${escape(targetName)}</yellow> ist online! " +
                            "Nutze <yellow>-</yellow> als Platzhalter, um ohne Spieler zu melden.</red>",
                )
            if (target.uniqueId == player.uniqueId) {
                return Result.Error("<red>Du kannst dich nicht selbst melden!</red>")
            }
            targetPlayer = target.uniqueId
            targetSnapshot = target.name
        }
        // Always appended if possible (server-side lookup, works for every client).
        val handRef = plugin.imageManager.findImageRef(player.inventory.itemInMainHand)
        if (targetPlayer == null && handRef == null && body == null) {
            return Result.Error(
                "<red>Der Report ist leer! Nutze <yellow>/report &lt;spieler|-&gt; [grund]</yellow> " +
                        "oder halte die Bild-Karte, die du melden willst, in der Hand.</red>",
            )
        }

        val entry = store.addReport(
            reporter = player.uniqueId,
            source = ReportSource.COMMAND,
            targetPlayer = targetPlayer,
            targetName = targetSnapshot,
            targetOwner = handRef?.first,
            targetFile = handRef?.second,
            reason = body,
        )
        notifyAdmins(entry, player.name)

        // The screenshot is always taken when the mod can do it.
        val withShot = requestShot(player, entry.id, shotRequested = true)
        return Result.Success(buildSummary(entry, withShot))
    }

    private fun clampReason(reason: String?): String? =
        reason?.trim()?.take(MAX_REASON_LENGTH)?.takeIf { it.isNotEmpty() }

    /** Aspects of the report for the reporter's confirmation message. */
    private fun buildSummary(entry: ReportEntry, withShot: Boolean): String {
        val parts = mutableListOf<String>()
        if (entry.targetPlayer != null) parts += "<white>${escape(entry.targetName ?: "?")}</white>"
        if (entry.targetOwner != null) parts += "<white>${escape(entry.targetFile ?: "?")}</white>"
        if (entry.reason != null) parts += "<white>Grund</white>"
        if (withShot) parts += "<white>Screenshot</white>"
        val listing = if (parts.isEmpty()) "" else " <gray>(</gray>${parts.joinToString("<gray>, </gray>")}<gray>)</gray>"
        return "<green>✓ Report <gold>#${entry.id}</gold> gemeldet!</green>$listing"
    }

    /**
     * Sends the screenshot capture request; returns whether one started
     * (needs the mod channel and no upload running).
     */
    private fun requestShot(player: Player, reportId: String, shotRequested: Boolean): Boolean {
        if (!shotRequested || SHOT_CHANNEL !in player.listeningPluginChannels) {
            return false
        }
        if (hasActiveSession(player.uniqueId)) {
            return false
        }
        val doc = JsonObject().apply {
            addProperty("report", reportId)
            addProperty("file", "report-$reportId")
        }
        try {
            player.sendPluginMessage(plugin, SHOT_CHANNEL, ImageListener.writeVarIntPrefixedUtf8(doc.toString()))
        } catch (e: Exception) {
            plugin.logger.warning("Could not request screenshot for report $reportId: ${e.message}")
            return false
        }
        return true
    }

    private fun hasActiveSession(playerId: UUID): Boolean =
        synchronized(sessionLock) {
            purgeExpiredLocked()
            sessions[playerId] != null
        }

    /** Drops any shot session (player quit / cleanup). */
    fun clearPlayer(playerId: UUID) {
        synchronized(sessionLock) {
            sessions.remove(playerId)?.let { deleteSessionFiles(playerId, it) }
        }
    }

    private fun notifyAdmins(entry: ReportEntry, reporterName: String) {
        plugin.server.onlinePlayers
            .filter { it.hasPermission("diawars.admin") }
            .forEach {
                it.sendMessage(mm(
                    "<yellow>⚠ <gold>$reporterName</gold> hat ${targetText(entry)} gemeldet " +
                            "(${sourceLabel(entry.source)}, ID: <white>${entry.id}</white>). " +
                            "Nutze <white>/admin report list</white> für Details.</yellow>",
                ))
            }
    }

    private fun sourceLabel(source: ReportSource): String = when (source) {
        ReportSource.MENU -> "<dark_aqua>[Menü]</dark_aqua>"
        ReportSource.COMMAND -> "<dark_aqua>[Befehl]</dark_aqua>"
    }

    /** Short description of the reported aspects for admin messages. */
    private fun targetText(entry: ReportEntry): String {
        val parts = mutableListOf<String>()
        if (entry.targetPlayer != null) parts += "<red>Spieler</red> <white>${escape(entry.targetName ?: "?")}</white>"
        if (entry.targetOwner != null) parts += "<red>Bild</red> <white>${escape(entry.targetFile ?: "?")}</white> " +
                "<gray>(von ${displayNameOf(entry.targetOwner)})</gray>"
        if (entry.reason != null) parts += "<red>Grund</red> <white>${escape(shortReason(entry.reason))}</white>"
        if (parts.isEmpty() && entry.shot) parts += "<red>Screenshot</red>"
        if (parts.isEmpty()) return "<gray>keine Angaben</gray>"
        return parts.joinToString("<gray> + </gray>")
    }

    private fun shortReason(text: String): String = if (text.length > 40) text.take(40) + "…" else text

    private fun displayNameOf(id: UUID?): String =
        if (id == null) "?" else de.davidsw.diawars.util.PlayerNameCache.nameOf(id)

    // ------------------------------------------------------------------
    // screenshot attachment (request -> offer/accept -> chunks)
    // ------------------------------------------------------------------

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
            val reporterName = displayNameOf(report.reporter)
            val shotMark = if (report.shot) " <dark_aqua>[Screenshot]</dark_aqua>" else ""
            lines += "<gray>- <yellow>#${report.id}</yellow> ${sourceLabel(report.source)} <white>$reporterName</white>: " +
                    "${targetText(report)}$shotMark</gray>"
        }
        return lines
    }

    fun formatView(id: String): Result {
        val report = store.get(id) ?: return Result.Error("<red>Dieser Report existiert nicht!</red>")
        val lines = mutableListOf(
            "<gold>=== Report #$id ===</gold>",
            "<gray>Von:</gray> <white>${displayNameOf(report.reporter)}</white> <gray>am ${DATE_FORMAT.format(Date(report.reportedAt * 1000))}</gray>",
            "<gray>Eingereicht über:</gray> ${sourceLabel(report.source)}",
            "<gray>Grund:</gray> " + (report.reason?.let { "<white>${escape(it)}</white>" } ?: "<dark_gray>—</dark_gray>"),
        )
        if (report.targetPlayer != null) {
            lines += "<gray>Spieler:</gray> <white>${escape(report.targetName ?: "?")}</white>"
        }
        if (report.targetOwner != null) {
            lines += "<gray>Bild:</gray> <white>${escape(report.targetFile ?: "?")}</white> <gray>(von ${displayNameOf(report.targetOwner)})</gray>"
        }
        lines += "<gray>Screenshot:</gray> " + if (report.shot) {
            "<green>angehängt</green> <gray>(</gray><yellow>/admin report shot $id</yellow><gray>)</gray>"
        } else {
            "<dark_gray>—</dark_gray>"
        }
        lines += "<gray>Status:</gray> " + (if (report.resolved) "<green>behandelt</green>" else "<yellow>offen</yellow>")
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
