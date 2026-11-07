package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.SqlOp
import java.io.File
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The diamond log (every pickup/drop/craft/… line shown by `/log`), stored in
 * the `diamond_log` table — it used to be one `logs/YYYY-MM-DD.txt` file per
 * day.
 *
 * Only the rendered line is kept (plus its day and timestamp), exactly like
 * the text file did: nothing reads structured fields back. Lines are queued in
 * memory and appended by the regular store flush; [recent] merges the queue
 * with the newest rows so an admin sees fresh entries before the flush ran.
 * Nothing is pruned, matching the old files that were never deleted either.
 */
class DiamondLogStore(plugin: Diawars) : SqlStore(plugin, "diamond_log") {
    private class Entry(val loggedAt: Long, val day: String, val line: String)

    private companion object {
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val DAY_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")

        /** Leading `[HH:mm:ss]` of every rendered line. */
        val LINE_TIME = Regex("^\\[(\\d{2}):(\\d{2}):(\\d{2})]")
    }

    /** Lines not yet written to the database, oldest first. */
    private val pending = ArrayDeque<Entry>()

    init {
        importLegacyFiles()
    }

    /** Queues one rendered log line; the next flush inserts it. */
    fun append(loggedAt: Long, day: String, line: String) {
        synchronized(pending) { pending.addLast(Entry(loggedAt, day, line)) }
        markDirty()
    }

    /** Newest [limit] entries, oldest first (same shape the text file had). */
    fun recent(limit: Int = 15): List<String> {
        val cap = limit.coerceAtLeast(1)
        val rows = try {
            db.read { connection ->
                connection.prepareStatement("SELECT line FROM diamond_log ORDER BY id DESC LIMIT ?").use { statement ->
                    statement.setInt(1, cap)
                    statement.executeQuery().use { rs ->
                        val out = ArrayList<String>(cap)
                        while (rs.next()) out += rs.getString(1)
                        out
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Could not read the diamond log: ${e.message}")
            emptyList()
        }
        val combined = ArrayList<String>(cap)
        synchronized(pending) { pending.forEach { combined += it.line } }
        combined += rows.asReversed()
        return combined.takeLast(cap)
    }

    /** Nothing to cache: [recent] reads the rows on demand. */
    override fun loadFrom(connection: Connection) = Unit

    override fun writeTo(ops: MutableList<SqlOp>) {
        val drained = synchronized(pending) {
            pending.toList().also { pending.clear() }
        }
        for (entry in drained) {
            ops += SqlOp(
                "INSERT INTO diamond_log (logged_at, day, line) VALUES (?, ?, ?)",
                entry.loggedAt, entry.day, entry.line,
            )
        }
    }

    /**
     * Imports the legacy `logs/YYYY-MM-DD.txt` files once and renames each to
     * `<name>.migrated`. A day that already has rows in the database is left
     * alone (the import then only renames), so a crash mid-import simply
     * continues on the next start without duplicating lines.
     */
    private fun importLegacyFiles() {
        val dir = File(plugin.dataFolder, "logs")
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".txt") } ?: return
        if (files.isEmpty()) return

        var imported = 0
        for (file in files.sortedBy { it.name }) {
            val day = file.nameWithoutExtension
            if (!DAY_PATTERN.matches(day)) {
                plugin.logger.warning("Ignoring legacy log file with an unexpected name: ${file.name}")
                continue
            }
            try {
                val populated = db.read { connection ->
                    connection.prepareStatement("SELECT COUNT(*) FROM diamond_log WHERE day = ?").use { statement ->
                        statement.setString(1, day)
                        statement.executeQuery().use { rs -> rs.next() && rs.getInt(1) > 0 }
                    }
                }
                if (!populated) {
                    val date = LocalDate.parse(day, DAY_FORMAT)
                    val ops = mutableListOf<SqlOp>()
                    for (line in file.readLines()) {
                        if (line.isBlank()) continue
                        ops += SqlOp(
                            "INSERT INTO diamond_log (logged_at, day, line) VALUES (?, ?, ?)",
                            epochOf(date, line), day, line,
                        )
                    }
                    db.execute(ops)
                    imported += ops.size
                }
                val target = File(file.parentFile, "${file.name}.migrated")
                if (!file.renameTo(target)) {
                    plugin.logger.warning("Could not rename the imported log file ${file.name}.")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Could not import the log file ${file.name}: ${e.message}")
            }
        }
        plugin.logger.info("Imported $imported diamond log line(s) into the database.")
    }

    /** Timestamp of a legacy line from its `[HH:mm:ss]` prefix (day start if missing). */
    private fun epochOf(date: LocalDate, line: String): Long {
        val match = LINE_TIME.find(line)
        val time = match?.let {
            try {
                LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
            } catch (_: Exception) {
                LocalTime.MIDNIGHT
            }
        } ?: LocalTime.MIDNIGHT
        return date.atTime(time).atZone(ZoneId.systemDefault()).toEpochSecond()
    }
}
