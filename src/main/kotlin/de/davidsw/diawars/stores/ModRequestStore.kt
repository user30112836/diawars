package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.SqlOp
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID

/**
 * One "please add this mod to the whitelist" request from the client-mod
 * whitelist dialog. Only the displayed mod name is required; anything else
 * (requester, time) is resolved server-side.
 */
data class ModRequest(
    val id: String,
    val requester: UUID,
    /** Reporter name snapshot, shown in admin lists even after renames. */
    val requesterName: String,
    /** Player-entered display name of the requested mod. */
    val displayName: String,
    val requestedAt: Long,
    var resolved: Boolean = false,
)

/**
 * Persistence for mod whitelist requests, shaped like [BugStore]: sequential
 * ids, open-vs-resolved state, per-admin read marks so the join notice knows
 * when something new arrived.
 */
class ModRequestStore(plugin: Diawars) : SqlStore(plugin, "mod_requests") {
    companion object {
        /** Refresh cap for per-admin read marks (mirrors BugStore). */
        const val MAX_ADMIN_READS = 100
    }

    private val cache = mutableMapOf<String, ModRequest>()
    private val adminReads = mutableMapOf<UUID, Long>()
    private val dirtyIds = mutableSetOf<String>()
    private var adminReadsDirty = false
    private var nextId = 1
    private var legacyNextId = 1

    init {
        migrateLegacyYaml("mod_requests.yml", "mod_requests") { yaml -> legacyRows(yaml) }
        load()
    }

    fun getOpen(): List<ModRequest> = cache.values.filter { !it.resolved }.sortedBy { it.requestedAt }

    fun hasOpenRequest(modName: String): Boolean =
        getOpen().any { it.displayName.equals(modName.trim(), ignoreCase = true) }

    fun addRequest(requester: UUID, requesterName: String, displayName: String): ModRequest {
        val id = (nextId++).toString()
        val request = ModRequest(id, requester, requesterName, displayName, System.currentTimeMillis() / 1000)
        cache[id] = request
        // Infrequent player-facing writes: persist synchronously so requests
        // survive a crash instead of resurrecting as open.
        touch(id)
        flushNow()
        return request
    }

    fun resolve(id: String): Boolean {
        val request = cache[id] ?: return false
        if (request.resolved) return false
        request.resolved = true
        touch(id)
        flushNow()
        return true
    }

    fun hasUnread(adminId: UUID): Boolean {
        val lastRead = adminReads[adminId] ?: 0
        return getOpen().any { it.requestedAt > lastRead }
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
                "SELECT id, requester, requester_name, display_name, requested_at, resolved FROM mod_requests",
            ).use { rs ->
                while (rs.next()) {
                    try {
                        cache[rs.getString("id")] = ModRequest(
                            id = rs.getString("id"),
                            requester = UUID.fromString(rs.getString("requester")),
                            requesterName = rs.getString("requester_name") ?: "?",
                            displayName = rs.getString("display_name") ?: "",
                            requestedAt = rs.getLong("requested_at"),
                            resolved = rs.getInt("resolved") != 0,
                        )
                    } catch (e: Exception) {
                        plugin.logger.warning(
                            "Could not load mod request ${rs.getString("id")}: ${e.message}",
                        )
                    }
                }
            }
        }
        adminReads.clear()
        loadAdminReads(connection, adminReads)
        nextId = maxOf(legacyNextId, highestId(connection, "mod_requests") + 1)
        plugin.logger.info("Loaded ${cache.size} mod request(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        if (dirtyIds.isNotEmpty()) {
            for (id in dirtyIds) {
                val request = cache[id]
                if (request == null) {
                    ops += SqlOp("DELETE FROM mod_requests WHERE id = ?", listOf(id))
                } else {
                    ops += SqlOp(
                        "INSERT INTO mod_requests (id, requester, requester_name, display_name, requested_at, resolved) " +
                            "VALUES (?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT(id) DO UPDATE SET requester = excluded.requester, " +
                            "requester_name = excluded.requester_name, display_name = excluded.display_name, " +
                            "requested_at = excluded.requested_at, resolved = excluded.resolved",
                        listOf(
                            id, request.requester, request.requesterName,
                            request.displayName, request.requestedAt, request.resolved,
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

    /** Parses the pre-SQLite `mod_requests.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        yaml.getConfigurationSection("requests")?.let { section ->
            for (id in section.getKeys(false)) {
                try {
                    val entry = section.getConfigurationSection(id) ?: continue
                    val requester = UUID.fromString(entry.getString("requester") ?: continue)
                    ops += SqlOp(
                        "INSERT INTO mod_requests (id, requester, requester_name, display_name, requested_at, resolved) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                        listOf(
                            id,
                            requester,
                            entry.getString("requester-name") ?: "?",
                            entry.getString("display-name") ?: "",
                            entry.getLong("requested-at", 0L),
                            entry.getBoolean("resolved", false),
                        ),
                    )
                } catch (e: Exception) {
                    plugin.logger.warning("Could not import mod request $id: ${e.message}")
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
