package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

/**
 * One player report (image or player). Exactly one of the target fields is
 * set, depending on [ReportTargetType]:
 * - IMAGE: [targetOwner] + [targetFile] (the reported image)
 * - PLAYER: [targetPlayer] + [targetName] (the reported player at report time)
 */
data class ReportEntry(
    val id: String,
    val reporter: UUID,
    val reportedAt: Long,
    val type: ReportTargetType,
    val targetPlayer: UUID?,
    val targetName: String?,
    val targetOwner: UUID?,
    val targetFile: String?,
    val reason: String,
    /** True once a screenshot was attached via the client mod. */
    var shot: Boolean = false,
    var resolved: Boolean = false,
)

enum class ReportTargetType {
    IMAGE,
    PLAYER;

    companion object {
        fun from(raw: String): ReportTargetType? = entries.firstOrNull { it.name == raw }
    }
}

class ReportStore(plugin: Diawars) : YamlStore(plugin, "reports.yml") {
    companion object {
        /** Cap for per-admin read marks so departed admins don't grow the file forever. */
        const val MAX_ADMIN_READS = 100
    }

    private val cache = mutableMapOf<String, ReportEntry>()
    private val adminReads = mutableMapOf<UUID, Long>()
    private var nextId = 1

    init {
        load()
    }

    fun get(id: String): ReportEntry? = cache[id]

    fun getAll(): Collection<ReportEntry> = cache.values

    fun getOpen(): List<ReportEntry> = cache.values.filter { !it.resolved }.sortedBy { it.reportedAt }

    fun addReport(
        reporter: UUID,
        type: ReportTargetType,
        targetPlayer: UUID?,
        targetName: String?,
        targetOwner: UUID?,
        targetFile: String?,
        reason: String,
    ): ReportEntry {
        val entry = ReportEntry(
            id = (nextId++).toString(),
            reporter = reporter,
            reportedAt = System.currentTimeMillis() / 1000,
            type = type,
            targetPlayer = targetPlayer,
            targetName = targetName,
            targetOwner = targetOwner,
            targetFile = targetFile,
            reason = reason,
        )
        cache[entry.id] = entry
        // Infrequent player-facing writes: persist synchronously so reports
        // survive a crash (same custody reasoning as BugStore#addBug).
        flushNow()
        return entry
    }

    fun resolve(id: String): Boolean {
        val report = cache[id] ?: return false
        if (report.resolved) return false
        report.resolved = true
        flushNow()
        return true
    }

    fun setShot(id: String): Boolean {
        val report = cache[id] ?: return false
        if (report.shot) return false
        report.shot = true
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
        flushNow()
    }

    override fun writeTo(yaml: YamlConfiguration) {
        for ((id, report) in cache) {
            yaml.set("reports.$id.reporter", report.reporter.toString())
            yaml.set("reports.$id.reported-at", report.reportedAt)
            yaml.set("reports.$id.type", report.type.name)
            yaml.set("reports.$id.target-player", report.targetPlayer?.toString())
            yaml.set("reports.$id.target-name", report.targetName)
            yaml.set("reports.$id.target-owner", report.targetOwner?.toString())
            yaml.set("reports.$id.target-file", report.targetFile)
            yaml.set("reports.$id.reason", report.reason)
            yaml.set("reports.$id.shot", report.shot)
            yaml.set("reports.$id.resolved", report.resolved)
        }
        for ((admin, timestamp) in adminReads) {
            yaml.set("admin-reads.$admin", timestamp)
        }
        yaml.set("next-id", nextId)
    }

    override fun readFrom(yaml: YamlConfiguration) {
        yaml.getConfigurationSection("reports")?.let { reportsSection ->
            for (id in reportsSection.getKeys(false)) {
                try {
                    val section = reportsSection.getConfigurationSection(id) ?: continue
                    val type = ReportTargetType.from(section.getString("type") ?: "PLAYER") ?: continue
                    val targetPlayer = section.getString("target-player")?.let {
                        try {
                            UUID.fromString(it)
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    }
                    val targetOwner = section.getString("target-owner")?.let {
                        try {
                            UUID.fromString(it)
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    }
                    cache[id] = ReportEntry(
                        id = id,
                        reporter = UUID.fromString(section.getString("reporter") ?: continue),
                        reportedAt = section.getLong("reported-at", 0L),
                        type = type,
                        targetPlayer = targetPlayer,
                        targetName = section.getString("target-name"),
                        targetOwner = targetOwner,
                        targetFile = section.getString("target-file"),
                        reason = section.getString("reason") ?: "",
                        shot = section.getBoolean("shot", false),
                        resolved = section.getBoolean("resolved", false),
                    )
                } catch (e: Exception) {
                    plugin.logger.warning("Could not load report $id: ${e.message}")
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

        plugin.logger.info("Loaded ${cache.size} report(s).")
    }
}
