package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.SqlOp
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID

data class BugReport(
    val id: String,
    val reporter: UUID,
    val description: String,
    val reportedAt: Long,
    var resolved: Boolean = false,
)

class BugStore(plugin: Diawars) : SqlStore(plugin, "bugs") {
    companion object {
        /** Cap for per-admin read marks so departed admins don't grow the table forever. */
        const val MAX_ADMIN_READS = 100
    }

    private val cache = mutableMapOf<String, BugReport>()
    private val adminReads = mutableMapOf<UUID, Long>()
    private val dirtyIds = mutableSetOf<String>()
    private var adminReadsDirty = false
    private var nextId = 1
    private var legacyNextId = 1

    init {
        migrateLegacyYaml("bugs.yml", "bugs") { yaml -> legacyRows(yaml) }
        load()
    }

    fun getUnresolved(): List<BugReport> = cache.values.filter { !it.resolved }.sortedBy { it.reportedAt }

    fun addBug(reporter: UUID, description: String): BugReport {
        val id = (nextId++).toString()
        val bug = BugReport(id, reporter, description, System.currentTimeMillis() / 1000)
        cache[id] = bug
        // Infrequent player-facing writes: persist synchronously so reports and
        // resolutions survive a crash instead of resurrecting as unresolved.
        touch(id)
        flushNow()
        return bug
    }

    fun resolve(id: String): Boolean {
        val bug = cache[id] ?: return false
        if (bug.resolved) return false
        bug.resolved = true
        touch(id)
        flushNow()
        return true
    }

    fun hasUnread(adminId: UUID): Boolean {
        val lastRead = adminReads[adminId] ?: 0
        return getUnresolved().any { it.reportedAt > lastRead }
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
                "SELECT id, reporter, description, reported_at, resolved FROM bugs",
            ).use { rs ->
                while (rs.next()) {
                    try {
                        val id = rs.getString("id")
                        cache[id] = BugReport(
                            id = id,
                            reporter = UUID.fromString(rs.getString("reporter")),
                            description = rs.getString("description") ?: "",
                            reportedAt = rs.getLong("reported_at"),
                            resolved = rs.getInt("resolved") != 0,
                        )
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load bug report ${rs.getString("id")}: ${e.message}")
                    }
                }
            }
        }
        adminReads.clear()
        loadAdminReads(connection, adminReads)
        nextId = maxOf(legacyNextId, highestId(connection, "bugs") + 1)
        plugin.logger.info("Loaded ${cache.size} bug report(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        if (dirtyIds.isNotEmpty()) {
            for (id in dirtyIds) {
                val bug = cache[id]
                if (bug == null) {
                    ops += SqlOp("DELETE FROM bugs WHERE id = ?", listOf(id))
                } else {
                    ops += SqlOp(
                        "INSERT INTO bugs (id, reporter, description, reported_at, resolved) " +
                            "VALUES (?, ?, ?, ?, ?) " +
                            "ON CONFLICT(id) DO UPDATE SET reporter = excluded.reporter, " +
                            "description = excluded.description, reported_at = excluded.reported_at, " +
                            "resolved = excluded.resolved",
                        listOf(id, bug.reporter, bug.description, bug.reportedAt, bug.resolved),
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

    /** Parses the pre-SQLite `bugs.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        yaml.getConfigurationSection("bugs")?.let { section ->
            for (id in section.getKeys(false)) {
                try {
                    val entry = section.getConfigurationSection(id) ?: continue
                    val reporter = entry.getString("reporter") ?: continue
                    ops += SqlOp(
                        "INSERT INTO bugs (id, reporter, description, reported_at, resolved) VALUES (?, ?, ?, ?, ?)",
                        listOf(
                            id,
                            UUID.fromString(reporter),
                            entry.getString("description") ?: "",
                            entry.getLong("reported-at", 0L),
                            entry.getBoolean("resolved", false),
                        ),
                    )
                } catch (e: Exception) {
                    plugin.logger.warning("Could not import bug report $id: ${e.message}")
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
