package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.StoreFiles
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

data class BugReport(
    val id: String,
    val reporter: UUID,
    val description: String,
    val reportedAt: Long,
    var resolved: Boolean = false,
)

class BugStore(private val plugin: Diawars) {
    private val storeFile = StoreFiles.resolve(plugin, "bugs.yml")
    private val cache = mutableMapOf<String, BugReport>()
    private val adminReads = mutableMapOf<UUID, Long>()
    private var nextId = 1

    init {
        load()
    }

    fun getById(id: String): BugReport? = cache[id]

    fun getUnresolved(): List<BugReport> = cache.values.filter { !it.resolved }.sortedBy { it.reportedAt }

    fun addBug(reporter: UUID, description: String): BugReport {
        val id = (nextId++).toString()
        val bug = BugReport(id, reporter, description, System.currentTimeMillis() / 1000)
        cache[id] = bug
        save()
        return bug
    }

    fun resolve(id: String): Boolean {
        val bug = cache[id] ?: return false
        if (bug.resolved) return false
        bug.resolved = true
        save()
        return true
    }

    fun hasUnread(adminId: UUID): Boolean {
        val lastRead = adminReads[adminId] ?: 0
        return getUnresolved().any { it.reportedAt > lastRead }
    }

    fun markRead(adminId: UUID) {
        adminReads[adminId] = System.currentTimeMillis() / 1000
        save()
    }

    private fun save() {
        val config = YamlConfiguration()
        for ((id, bug) in cache) {
            config.set("bugs.$id.reporter", bug.reporter.toString())
            config.set("bugs.$id.description", bug.description)
            config.set("bugs.$id.reported-at", bug.reportedAt)
            config.set("bugs.$id.resolved", bug.resolved)
        }
        for ((admin, timestamp) in adminReads) {
            config.set("admin-reads.$admin", timestamp)
        }
        config.set("next-id", nextId)
        try {
            config.save(storeFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save bug reports to $storeFile: ${e.message}")
        }
    }

    private fun load() {
        if (!storeFile.exists()) {
            storeFile.parentFile.mkdirs()
            storeFile.createNewFile()
        }

        val yaml = YamlConfiguration.loadConfiguration(storeFile)

        yaml.getConfigurationSection("bugs")?.let { bugsSection ->
            for (id in bugsSection.getKeys(false)) {
                try {
                    val section = bugsSection.getConfigurationSection(id) ?: continue
                    val reporterString = section.getString("reporter") ?: continue
                    cache[id] = BugReport(
                        id = id,
                        reporter = UUID.fromString(reporterString),
                        description = section.getString("description") ?: "",
                        reportedAt = section.getLong("reported-at", 0L),
                        resolved = section.getBoolean("resolved", false),
                    )
                } catch (e: Exception) {
                    plugin.logger.warning("Could not load bug report $id: ${e.message}")
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

        plugin.logger.info("Loaded ${cache.size} bug report(s).")
    }
}