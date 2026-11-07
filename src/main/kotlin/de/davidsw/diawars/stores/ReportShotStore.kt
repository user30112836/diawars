package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.SqlOp
import java.io.File
import java.sql.Connection

/**
 * Screenshot attachments of reports, stored as PNG BLOBs (they used to be
 * `reports/<id>.png` files).
 *
 * Shots are written once and never modified, so there is no cache: [load]
 * reads on demand (map handout, admin-mod viewer) and [save] commits inline —
 * a finished upload is a custody handoff, a crash right after it must not lose
 * the screenshot the player just sent.
 */
class ReportShotStore(plugin: Diawars) : SqlStore(plugin, "report_shots") {
    private companion object {
        /** Report ids come from the admin channel, so validate before touching SQL. */
        val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    }

    init {
        migrateLegacyFiles()
        load()
    }

    fun save(reportId: String, png: ByteArray) {
        if (!ID_PATTERN.matches(reportId)) return
        db.execute(
            listOf(
                SqlOp(
                    "INSERT INTO report_shots (report, data, saved_at) VALUES (?, ?, ?) " +
                        "ON CONFLICT(report) DO UPDATE SET data = excluded.data, saved_at = excluded.saved_at",
                    listOf(reportId, png, System.currentTimeMillis() / 1000),
                ),
            ),
        )
    }

    fun load(reportId: String): ByteArray? {
        if (!ID_PATTERN.matches(reportId)) return null
        return try {
            db.read { connection ->
                connection.prepareStatement("SELECT data FROM report_shots WHERE report = ?").use { statement ->
                    statement.setString(1, reportId)
                    statement.executeQuery().use { rs -> if (rs.next()) rs.getBytes(1) else null }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Could not read the screenshot of report $reportId: ${e.message}")
            null
        }
    }

    /** True when a screenshot is attached (used by `/admin report shot`). */
    fun contains(reportId: String): Boolean {
        if (!ID_PATTERN.matches(reportId)) return false
        return try {
            db.read { connection ->
                connection.prepareStatement("SELECT 1 FROM report_shots WHERE report = ?").use { statement ->
                    statement.setString(1, reportId)
                    statement.executeQuery().use { rs -> rs.next() }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Could not check the screenshot of report $reportId: ${e.message}")
            false
        }
    }

    fun delete(reportId: String) {
        if (!ID_PATTERN.matches(reportId)) return
        db.execute(listOf(SqlOp("DELETE FROM report_shots WHERE report = ?", listOf(reportId))))
    }

    /** Nothing to load: the blobs are only read on demand. */
    override fun loadFrom(connection: Connection) = Unit

    /** Shots are written through [save] (inline custody write), not the flush task. */
    override fun writeTo(ops: MutableList<SqlOp>) = Unit

    /**
     * Imports the legacy `reports/<id>.png` files once and renames each to
     * `<name>.migrated`. Runs before the first read, so an interrupted import
     * (crash) simply continues on the next start.
     */
    private fun migrateLegacyFiles() {
        val dir = File(plugin.dataFolder, "reports")
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".png") } ?: return
        if (files.isEmpty()) return

        var imported = 0
        for (file in files.sortedBy { it.name }) {
            val id = file.nameWithoutExtension
            if (!ID_PATTERN.matches(id)) {
                plugin.logger.warning("Ignoring legacy screenshot with an invalid report id: ${file.name}")
                continue
            }
            try {
                if (!contains(id)) {
                    save(id, file.readBytes())
                    imported++
                }
                if (file.renameTo(File(file.parentFile, "${file.name}.migrated"))) {
                    continue
                }
                plugin.logger.warning("Could not rename the imported screenshot ${file.name}.")
            } catch (e: Exception) {
                plugin.logger.warning("Could not import the screenshot ${file.name}: ${e.message}")
            }
        }
        plugin.logger.info("Imported $imported report screenshot(s) into the database.")
    }
}
