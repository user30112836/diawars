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
    companion object {
        // A book page shows ~14 lines; generated mod pages stay below that so
        // nothing is cut off in-game. One mod per line, ~19 chars per line.
        private const val MAX_MOD_PAGE_LINES = 13
        private const val CHARS_PER_LINE = 19
    }

    private val manualFile = ConfigFiles.resolve(plugin, "manual.yml")

    private var overview = ManualSegment("overview", "Kurzübersicht", emptyList())
    private var rulesBase = ManualSegment("rules", "Serverregeln", emptyList())
    private var segments: List<ManualSegment> = emptyList()

    init {
        loadFromConfig()
    }

    fun loadFromConfig() {
        val config = YamlConfiguration.loadConfiguration(manualFile)

        overview = loadEntry(config, "overview", "overview", "Kurzübersicht")
        rulesBase = loadEntry(config, "rules", "rules", "Serverregeln")

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

    private fun textLines(text: String): Int = (text.length + CHARS_PER_LINE - 1) / CHARS_PER_LINE

    private fun modLines(mod: String): Int = textLines("• $mod")

    private fun chunkLines(chunk: List<String>): Int = chunk.sumOf { modLines(it) }

    private fun buildAllowedModsPages(): List<String> {
        val heading = "<aqua><bold>Erlaubte Mods</bold></aqua>"
        val mods = plugin.modWhitelistManager.getAllowedMods()
        if (mods.isEmpty()) {
            return listOf("$heading\n\n<dark_gray>Aktuell ist keine Mod freigegeben. Frage einen Admin.</dark_gray>")
        }
        val intro = "Folgende Mods darfst du benutzen. Alle anderen werden blockiert:"
        val outro = "Benötigte Bibliotheken sind automatisch erlaubt."
        val introLines = textLines(intro)
        val outroLines = textLines(outro)

        // Greedy chunking: one mod per line, fill each page with as many lines
        // as fit. The first page additionally carries the intro.
        val chunks = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        var currentLines = 0
        // Fixed lines before the mod lines: heading + blank + optional intro + blank.
        var headLines = 1 + 1 + introLines + 1
        for (mod in mods) {
            if (current.isNotEmpty() && headLines + currentLines + modLines(mod) > MAX_MOD_PAGE_LINES) {
                chunks += current
                current = mutableListOf()
                currentLines = 0
                headLines = 1 + 1
            }
            current += mod
            currentLines += modLines(mod)
        }
        if (current.isNotEmpty() || chunks.isEmpty()) chunks += current
        // Make room for the outro (blank + text) on the last page.
        while (chunks.last().size > 1) {
            val head = if (chunks.size == 1) 1 + 1 + introLines else 1 + 1
            if (head + chunkLines(chunks.last()) + 1 + outroLines <= MAX_MOD_PAGE_LINES) break
            chunks += mutableListOf(chunks.last().removeLast())
        }
        // Balance fullness across pages: move mods from fuller to emptier later
        // pages while both sides still fit. Keeps mod order and terminates:
        // every move strictly decreases the sum of squared page sizes.
        fun pageTotal(index: Int, chunk: List<String>): Int {
            var total = 1 + 1 + chunkLines(chunk)
            if (index == 0) total += introLines + 1
            if (index == chunks.lastIndex) total += 1 + outroLines
            return total
        }
        var balanced = false
        while (!balanced) {
            balanced = true
            for (i in chunks.size - 1 downTo 1) {
                val src = chunks[i - 1]
                val dst = chunks[i]
                val mod = src.lastOrNull() ?: continue
                if (src.size - 1 >= dst.size + 1 && pageTotal(i, dst + mod) <= MAX_MOD_PAGE_LINES) {
                    src.removeLast()
                    dst.add(0, mod)
                    balanced = false
                }
            }
        }

        return chunks.mapIndexed { index, chunk ->
            buildString {
                append(heading)
                if (index == 0) append("\n\n<dark_gray>").append(intro).append("</dark_gray>")
                append("\n\n<dark_gray>")
                append(chunk.joinToString("\n") { "• $it" })
                append("</dark_gray>")
                if (index == chunks.lastIndex) append("\n\n<dark_gray>").append(outro).append("</dark_gray>")
            }
        }
    }

    fun getOverview(): ManualSegment = overview

    /** Static rule pages from manual.yml plus the allowed-mods pages generated
     * from mod_whitelist.yml. Built on access so whitelist reloads apply
     * immediately, regardless of reload order. */
    fun getRules(): ManualSegment =
        ManualSegment(rulesBase.id, rulesBase.title, rulesBase.pages + buildAllowedModsPages())
    fun getSegments(): List<ManualSegment> = segments

    fun getFullManual(): ManualSegment {
        val pages = segments.flatMap { segment ->
            listOf("<gold><bold>${segment.title}</bold></gold>\n") + segment.pages
        }
        return ManualSegment("full", "Vollständiges Handbuch", pages)
    }
}