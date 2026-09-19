package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.ConfigFiles
import org.bukkit.configuration.file.YamlConfiguration

class ModWhitelistManager(private val plugin: Diawars) {
    // Fabric mod id uses a hyphen ("diawars-client"), Forge forbids hyphens so the
    // Forge port uses an underscore ("diawars_client"). Both must always be allowed.
    private val defaultMods = listOf("diawars-client", "diawars_client", "java", "mixinextras")
    private val whitelistFile = ConfigFiles.resolve(plugin, "mod_whitelist.yml")
    private var whitelist: Set<String> = emptySet()
    private var allowedMods: List<String> = emptyList()


    init {
        loadFromConfig()
    }

    fun loadFromConfig() {
        val config = YamlConfiguration.loadConfiguration(whitelistFile)
        allowedMods = config.getStringList("allowed_mods")
        // "mods" is the legacy single-list format, still read for backwards
        // compatibility with existing server configs.
        whitelist = (allowedMods + config.getStringList("libraries") +
            config.getStringList("mods")).map { it.lowercase() }.toSet()
        plugin.logger.info("Loaded ${whitelist.size} whitelisted mod(s).")
    }

    /** Mods players install themselves, alphabetically. Shown in the Serverregeln. */
    fun getAllowedMods(): List<String> = allowedMods.sorted()

    fun isAllowed(modId: String): Boolean = modId.lowercase() in whitelist || modId.lowercase() in defaultMods

    fun findDisallowedMods(modIds: Collection<String>): Collection<String> = modIds.filter { !isAllowed(it) }
}