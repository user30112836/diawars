package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.ConfigFiles
import org.bukkit.configuration.file.YamlConfiguration

class ModWhitelistManager(private val plugin: Diawars) {
    private val whitelistFile = ConfigFiles.resolve(plugin, "mod_whitelist.yml")
    private var whitelist: Set<String> = emptySet()

    private val DEFAULT_MODS = listOf("diawars-client", "java", "mixinextras")

    init {
        loadFromConfig()
    }

    fun loadFromConfig() {
        val config = YamlConfiguration.loadConfiguration(whitelistFile)
        whitelist = config.getStringList("mods").map { it.lowercase() }.toSet()
        plugin.logger.info("Loaded ${whitelist.size} whitelisted mod(s).")
    }

    fun isAllowed(modId: String): Boolean = modId.lowercase() in whitelist || modId.lowercase() in DEFAULT_MODS

    fun findDisallowedMods(modIds: Collection<String>): Collection<String> = modIds.filter { !isAllowed(it) }
}