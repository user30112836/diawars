package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.SqlOp
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID

/**
 * One player report with any combination of aspects (submitted via the
 * client-mod dialog = [ReportSource.MENU], or via the command line =
 * [ReportSource.COMMAND] where the command path auto-appends screenshot and
 * hand image):
 * - [targetPlayer] / [targetName]: reported online player (name snapshot)
 * - [targetOwner] / [targetFile]: reported image (may coexist with a player)
 * - [reason]: free text, may be empty
 * - [shot]: a screenshot was attached via the client mod
 */
data class ReportEntry(
    val id: String,
    val reporter: UUID,
    val reportedAt: Long,
    val source: ReportSource,
    val targetPlayer: UUID?,
    val targetName: String?,
    val targetOwner: UUID?,
    val targetFile: String?,
    val reason: String?,
    /** True once a screenshot was attached via the client mod. */
    var shot: Boolean = false,
    var resolved: Boolean = false,
)

/** How the report was submitted: mod dialog ("Menü") or command line ("Befehl"). */
enum class ReportSource {
    MENU,
    COMMAND;

    companion object {
        fun from(raw: String): ReportSource? = entries.firstOrNull { it.name == raw }
    }
}

class ReportStore(plugin: Diawars) : SqlStore(plugin, "reports") {
    companion object {
        /** Cap for per-admin read marks so departed admins don't grow the table forever. */
        const val MAX_ADMIN_READS = 100
    }

    private val cache = mutableMapOf<String, ReportEntry>()
    private val adminReads = mutableMapOf<UUID, Long>()
    private val dirtyIds = mutableSetOf<String>()
    private var adminReadsDirty = false
    private var nextId = 1
    private var legacyNextId = 1

    init {
        migrateLegacyYaml("reports.yml", "reports") { yaml -> legacyRows(yaml) }
        load()
    }

    fun get(id: String): ReportEntry? = cache[id]

    fun getAll(): Collection<ReportEntry> = cache.values

    fun getOpen(): List<ReportEntry> = cache.values.filter { !it.resolved }.sortedBy { it.reportedAt }

    fun addReport(
        reporter: UUID,
        source: ReportSource,
        targetPlayer: UUID?,
        targetName: String?,
        targetOwner: UUID?,
        targetFile: String?,
        reason: String?,
    ): ReportEntry {
        val entry = ReportEntry(
            id = (nextId++).toString(),
            reporter = reporter,
            reportedAt = System.currentTimeMillis() / 1000,
            source = source,
            targetPlayer = targetPlayer,
            targetName = targetName,
            targetOwner = targetOwner,
            targetFile = targetFile,
            reason = reason?.takeIf { it.isNotBlank() },
        )
        cache[entry.id] = entry
        // Infrequent player-facing writes: persist synchronously so reports
        // survive a crash (same custody reasoning as BugStore#addBug).
        touch(entry.id)
        flushNow()
        return entry
    }

    fun resolve(id: String): Boolean {
        val report = cache[id] ?: return false
        if (report.resolved) return false
        report.resolved = true
        touch(id)
        flushNow()
        return true
    }

    fun setShot(id: String): Boolean {
        val report = cache[id] ?: return false
        if (report.shot) return false
        report.shot = true
        touch(id)
        flushNow()
        return true
    }

    fun hasUnread(adminId: UUID): Boolean {
        val lastRead = adminReads[adminId] ?: 0
        return getOpen().any { it.reportedAt > lastRead }
    }

    fun markRead(adminId: UUID) {
        adminReads[adminId] = System.currentTimeMillis() / 1000
        // Evict stalest read marks beyond the cap (admin action rate: negligible cost).
        while (adminReads.size > MAX_ADMIN_READS) {
            adminReads.minByOrNull { it.value }?.key?.let { adminReads.remove(it) } ?: break
        }
        adminReadsDirty = true
        markDirty()
        flushNow()
    }

    private fun touch(id: String) {
        dirtyIds.add(id)
        markDirty()
    }

    override fun loadFrom(connection: Connection) {
        cache.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT id, reporter, reported_at, source, target_player, target_name, " +
                    "target_owner, target_file, reason, shot, resolved FROM reports",
            ).use { rs ->
                while (rs.next()) {
                    try {
                        cache[rs.getString("id")] = ReportEntry(
                            id = rs.getString("id"),
                            reporter = UUID.fromString(rs.getString("reporter")),
                            reportedAt = rs.getLong("reported_at"),
                            source = ReportSource.from(rs.getString("source") ?: "") ?: ReportSource.COMMAND,
                            targetPlayer = rs.getString("target_player")?.let { parseUuid(it) },
                            targetName = rs.getString("target_name"),
                            targetOwner = rs.getString("target_owner")?.let { parseUuid(it) },
                            targetFile = rs.getString("target_file"),
                            reason = rs.getString("reason")?.takeIf { it.isNotBlank() },
                            shot = rs.getInt("shot") != 0,
                            resolved = rs.getInt("resolved") != 0,
                        )
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load report ${rs.getString("id")}: ${e.message}")
                    }
                }
            }
        }
        adminReads.clear()
        loadAdminReads(connection, adminReads)
        nextId = maxOf(legacyNextId, highestId(connection, "reports") + 1)
        plugin.logger.info("Loaded ${cache.size} report(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        if (dirtyIds.isNotEmpty()) {
            for (id in dirtyIds) {
                val report = cache[id]
                if (report == null) {
                    ops += SqlOp("DELETE FROM reports WHERE id = ?", listOf(id))
                } else {
                    ops += SqlOp(
                        "INSERT INTO reports (id, reporter, reported_at, source, target_player, target_name, " +
                            "target_owner, target_file, reason, shot, resolved) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT(id) DO UPDATE SET reporter = excluded.reporter, " +
                            "reported_at = excluded.reported_at, source = excluded.source, " +
                            "target_player = excluded.target_player, target_name = excluded.target_name, " +
                            "target_owner = excluded.target_owner, target_file = excluded.target_file, " +
                            "reason = excluded.reason, shot = excluded.shot, resolved = excluded.resolved",
                        listOf(
                            id, report.reporter, report.reportedAt, report.source,
                            report.targetPlayer, report.targetName, report.targetOwner,
                            report.targetFile, report.reason, report.shot, report.resolved,
                        ),
                    )
                }
            }
            dirtyIds.clear()
        }
        if (adminReadsDirty) {
            writeAdminReads(ops, adminReads)
            adminReadsDirty = false
        }
    }

    private fun parseUuid(raw: String): UUID? = try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Parses the pre-SQLite `reports.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        yaml.getConfigurationSection("reports")?.let { section ->
            for (id in section.getKeys(false)) {
                try {
                    val entry = section.getConfigurationSection(id) ?: continue
                    val reporter = UUID.fromString(entry.getString("reporter") ?: continue)
                    ops += SqlOp(
                        "INSERT INTO reports (id, reporter, reported_at, source, target_player, target_name, " +
                            "target_owner, target_file, reason, shot, resolved) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        listOf(
                            id,
                            reporter,
                            entry.getLong("reported-at", 0L),
                            // Legacy entries predate the source marker (command only).
                            ReportSource.from(entry.getString("source") ?: "") ?: ReportSource.COMMAND,
                            entry.getString("target-player")?.let { parseUuid(it) },
                            entry.getString("target-name"),
                            entry.getString("target-owner")?.let { parseUuid(it) },
                            entry.getString("target-file"),
                            entry.getString("reason")?.takeIf { it.isNotBlank() },
                            entry.getBoolean("shot", false),
                            entry.getBoolean("resolved", false),
                        ),
                    )
                } catch (e: Exception) {
                    plugin.logger.warning("Could not import report $id: ${e.message}")
                }
            }
        }
        yaml.getConfigurationSection("admin-reads")?.let { section ->
            for (key in section.getKeys(false)) {
                try {
                    ops += SqlOp(
                        "INSERT INTO admin_reads (store, admin, last_read) VALUES (?, ?, ?)",
                        listOf(storeName, UUID.fromString(key), section.getLong(key)),
                    )
                } catch (e: Exception) {
                    plugin.logger.warning("Could not import admin read state for $key: ${e.message}")
                }
            }
        }
        legacyNextId = yaml.getInt("next-id", 1)
        return ops
    }
}
