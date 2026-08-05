package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.ConfigFiles
import org.bukkit.configuration.file.YamlConfiguration

data class ManualSegment(
    val id: String,
    val title: String,
    val pages: List<String>,
)

class ManualManager(private val plugin: Diawars) {
    private val manualFile = ConfigFiles.resolve(plugin, "manual.yml")

    private var overview = ManualSegment("overview", "Kurzübersicht", emptyList())
    private var rules = ManualSegment("rules", "Serverregeln", emptyList())
    private var segments: List<ManualSegment> = emptyList()

    init {
        loadFromConfig()
    }

    fun loadFromConfig() {
        val config = YamlConfiguration.loadConfiguration(manualFile)

        overview = loadEntry(config, "overview", "overview", "Kurzübersicht")
        rules = loadEntry(config, "rules", "rules", "Serverregeln")

        val list = mutableListOf<ManualSegment>()
        config.getConfigurationSection("segments")?.let { section ->
            for (id in section.getKeys(false)) {
                list += loadEntry(config, "segments.$id", id, id)
            }
        }
        segments = list

        plugin.logger.info("Loaded manual with ${segments.size} segment(s).")
    }

    private fun loadEntry(config: YamlConfiguration, path: String, id: String, fallbackTitle: String): ManualSegment {
        val section = config.getConfigurationSection(path)
        val title = section?.getString("title") ?: fallbackTitle
        val pages = section?.getStringList("pages") ?: emptyList()
        return ManualSegment(id, title, pages)
    }

    fun getOverview(): ManualSegment = overview
    fun getRules(): ManualSegment = rules
    fun getSegments(): List<ManualSegment> = segments
    fun getSegment(id: String): ManualSegment? = segments.firstOrNull { it.id == id }

    fun getFullManual(): ManualSegment {
        val pages = segments.flatMap { segment ->
            listOf("<gold><bold>${segment.title}</bold></gold>\n") + segment.pages
        }
        return ManualSegment("full", "Vollständiges Handbuch", pages)
    }
}