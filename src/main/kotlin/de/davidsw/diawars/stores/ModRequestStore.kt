package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
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
 * Persistence for mod whitelist requests ({@code mod_requests.yml}), shaped
 * like [BugStore]: sequential ids, open-vs-resolved state, per-admin read
 * marks so the join notice knows when something new arrived.
 */
class ModRequestStore(plugin: Diawars) : YamlStore(plugin, "mod_requests.yml") {
    companion object {
        /** Refresh cap for per-admin read marks (mirrors BugStore). */
        const val MAX_ADMIN_READS = 100
    }

    private val cache = mutableMapOf<String, ModRequest>()
    private val adminReads = mutableMapOf<UUID, Long>()
    private var nextId = 1

    init {
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
        flushNow()
        return request
    }

    fun resolve(id: String): Boolean {
        val request = cache[id] ?: return false
        if (request.resolved) return false
        request.resolved = true
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
        flushNow()
    }

    override fun writeTo(yaml: YamlConfiguration) {
        for ((id, request) in cache) {
            yaml.set("requests.$id.requester", request.requester.toString())
            yaml.set("requests.$id.requester-name", request.requesterName)
            yaml.set("requests.$id.display-name", request.displayName)
            yaml.set("requests.$id.requested-at", request.requestedAt)
            yaml.set("requests.$id.resolved", request.resolved)
        }
        for ((admin, timestamp) in adminReads) {
            yaml.set("admin-reads.$admin", timestamp)
        }
        yaml.set("next-id", nextId)
    }

    override fun readFrom(yaml: YamlConfiguration) {
        yaml.getConfigurationSection("requests")?.let { requestsSection ->
            for (id in requestsSection.getKeys(false)) {
                try {
                    val section = requestsSection.getConfigurationSection(id) ?: continue
                    val requesterString = section.getString("requester") ?: continue
                    cache[id] = ModRequest(
                        id = id,
                        requester = UUID.fromString(requesterString),
                        requesterName = section.getString("requester-name") ?: "?",
                        displayName = section.getString("display-name") ?: "",
                        requestedAt = section.getLong("requested-at", 0L),
                        resolved = section.getBoolean("resolved", false),
                    )
                } catch (e: Exception) {
                    plugin.logger.warning("Could not load mod request $id: ${e.message}")
                }
            }
        }

        yaml.getConfigurationSection("admin-reads")?.let { readsSection ->
            for (key in readsSection.getKeys(false)) {
                try {
                    adminReads[UUID.fromString(key)] = readsSection.getLong(key)
                } catch (e: Exception) {
                    plugin.logger.warning("Could not load admin read state for $key: ${e.message}")
                }
            }
        }

        val highestExisting = cache.keys.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0
        nextId = maxOf(yaml.getInt("next-id", 1), highestExisting + 1)

        plugin.logger.info("Loaded ${cache.size} mod request(s).")
    }
}
