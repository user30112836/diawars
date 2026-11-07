package de.davidsw.diawars.stores

import com.google.gson.Gson
import com.google.gson.JsonParseException
import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.JsonColumn
import de.davidsw.diawars.util.SqlOp
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID

data class ModEntry(
    var id: String = "",
    var version: String = "",
)

data class OldModEntry(
    val loader: String = "unknown",
    val id: String = "",
)

data class Loader(
    val type: String = "unknown",
    val version: String = "unknown",
)

data class ClientInfo(
    val minecraftVersion: String = "unknown",
    val activeLoader: Loader = Loader(),
    val previousLoaders: List<Loader> = emptyList(),

    val activeMods: List<ModEntry> = emptyList(),
    val previousMods: List<OldModEntry> = emptyList(),

    val activeResourcePacks: List<String> = emptyList(),
    val previousResourcePacks: List<String> = emptyList(),

    val shaderModInstalled: Boolean = false,
    val everShaderModInstalled: Boolean = false,

    val shadersEnabled: Boolean = false,
    val everShadersEnabled: Boolean = false,

    val activeShaderPack: String? = null,
    val previousShaderPacks: List<String> = emptyList(),

    val reportedAt: Long = System.currentTimeMillis() / 1000,
)

private data class ClientInfoDto(
    var minecraftVersion: String = "",
    var loaderType: String = "",
    var loaderVersion: String = "",
    var mods: List<ModEntry> = emptyList(),
    var resourcePacks: List<String>? = null,
    var shaderModInstalled: Boolean? = null,
    var shadersEnabled: Boolean? = null,
    var activeShaderPack: String? = null,
)

class ClientInfoStore(
    plugin: Diawars,
) : SqlStore(plugin, "client_info") {

    companion object {
        /** Cap for every previous-* history list so client_info.yml cannot grow forever. */
        const val MAX_HISTORY = 50
    }

    private val cache = mutableMapOf<UUID, ClientInfo>()
    private val gson = Gson()
    private val reportedPlayers = mutableSetOf<UUID>()
    private val dirty = DirtyKeys<UUID>()

    init {
        migrateLegacyYaml("client_info.yml", "client_info") { yaml -> legacyRows(yaml) }
        load()
    }

    fun hasReported(playerId: UUID): Boolean {
        return playerId in reportedPlayers
    }

    /** Last reported client info for [playerId], or null if it never reported. */
    fun get(playerId: UUID): ClientInfo? {
        return cache[playerId]
    }

    fun addReported(playerId: UUID) {
        reportedPlayers.add(playerId)
    }

    fun removeReported(playerId: UUID) {
        reportedPlayers.remove(playerId)
    }

    fun saveFromJson(playerId: UUID, json: String): Boolean {
        return try {
            val dto = gson.fromJson(json, ClientInfoDto::class.java)
                ?: throw JsonParseException("JSON is null")

            require(dto.minecraftVersion.isNotBlank()) {
                "Missing Minecraft version"
            }

            require(dto.loaderType.isNotBlank()) {
                "Missing loader type"
            }

            require(dto.loaderVersion.isNotBlank()) {
                "Missing loader version"
            }

            val oldInfo = cache[playerId]

            val newLoader = Loader(
                type = dto.loaderType,
                version = dto.loaderVersion,
            )

            val newMods = dto.mods
            val newResourcePacks = dto.resourcePacks.orEmpty()

            val newShaderModInstalled = dto.shaderModInstalled == true
            val newShadersEnabled = dto.shadersEnabled == true
            val newShaderPack = dto.activeShaderPack

            val updatedInfo = ClientInfo(
                minecraftVersion = dto.minecraftVersion,
                activeLoader = newLoader,
                previousLoaders = buildPreviousLoaders(
                    oldInfo = oldInfo,
                    newLoader = newLoader,
                ),

                activeMods = newMods,
                previousMods = buildPreviousMods(
                    oldInfo = oldInfo,
                    newLoader = newLoader,
                    newMods = newMods,
                ),

                activeResourcePacks = newResourcePacks,
                previousResourcePacks = buildPreviousResourcePacks(
                    oldInfo = oldInfo,
                    newResourcePacks = newResourcePacks,
                ),

                shaderModInstalled = newShaderModInstalled,
                everShaderModInstalled =
                    oldInfo?.everShaderModInstalled == true ||
                            newShaderModInstalled,

                shadersEnabled = newShadersEnabled,
                everShadersEnabled =
                    oldInfo?.everShadersEnabled == true ||
                            newShadersEnabled,

                activeShaderPack = newShaderPack,
                previousShaderPacks = buildPreviousShaderPacks(
                    oldInfo = oldInfo,
                    newShaderPack = newShaderPack,
                ),

                reportedAt = System.currentTimeMillis() / 1000,
            )

            cache[playerId] = updatedInfo
            dirty.touch(playerId)

            // Packet-frequency path: defer persistence to the periodic/shutdown
            // flush instead of serializing the whole file per client packet.
            // Lost entries are re-reported on next join anyway.
            markDirty()

            true
        } catch (e: Exception) {
            plugin.logger.warning(
                "Could not parse client info for $playerId: ${e.message}"
            )
            false
        }
    }

    private fun buildPreviousLoaders(
        oldInfo: ClientInfo?,
        newLoader: Loader,
    ): List<Loader> {
        if (oldInfo == null || oldInfo.activeLoader == newLoader) {
            return oldInfo?.previousLoaders.orEmpty()
        }

        return (oldInfo.previousLoaders + oldInfo.activeLoader).distinct().takeLast(MAX_HISTORY)
    }

    private fun buildPreviousMods(
        oldInfo: ClientInfo?,
        newLoader: Loader,
        newMods: List<ModEntry>,
    ): List<OldModEntry> {
        if (oldInfo == null) {
            return emptyList()
        }

        if (
            oldInfo.activeMods == newMods &&
            oldInfo.activeLoader == newLoader
        ) {
            return oldInfo.previousMods
        }

        val oldEntries = oldInfo.activeMods.map {
            OldModEntry(
                loader = oldInfo.activeLoader.type,
                id = it.id,
            )
        }

        return (oldInfo.previousMods + oldEntries).distinct().takeLast(MAX_HISTORY)
    }

    private fun buildPreviousResourcePacks(
        oldInfo: ClientInfo?,
        newResourcePacks: List<String>,
    ): List<String> {
        if (
            oldInfo == null ||
            oldInfo.activeResourcePacks == newResourcePacks
        ) {
            return oldInfo?.previousResourcePacks.orEmpty()
        }

        return (
                oldInfo.previousResourcePacks +
                        oldInfo.activeResourcePacks
                ).distinct().takeLast(MAX_HISTORY)
    }

    private fun buildPreviousShaderPacks(
        oldInfo: ClientInfo?,
        newShaderPack: String?,
    ): List<String> {
        if (oldInfo == null) {
            return emptyList()
        }

        if (oldInfo.activeShaderPack == newShaderPack) {
            return oldInfo.previousShaderPacks
        }

        val oldShaderPack = oldInfo.activeShaderPack
            ?: return oldInfo.previousShaderPacks

        return (
                oldInfo.previousShaderPacks +
                        oldShaderPack
                ).distinct().takeLast(MAX_HISTORY)
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (playerId in dirty.drain()) {
            val info = cache[playerId]
            if (info == null) {
                ops += SqlOp("DELETE FROM client_info WHERE player = ?", listOf(playerId))
            } else {
                ops += SqlOp(
                    "INSERT INTO client_info (player, data, reported_at) VALUES (?, ?, ?) " +
                        "ON CONFLICT(player) DO UPDATE SET data = excluded.data, reported_at = excluded.reported_at",
                    listOf(playerId, JsonColumn.encode(info), info.reportedAt),
                )
            }
        }
    }

    override fun loadFrom(connection: Connection) {
        cache.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT player, data FROM client_info").use { rs ->
                while (rs.next()) {
                    val rawPlayer = rs.getString("player")
                    try {
                        val data = rs.getString("data")
                        if (data.isNullOrBlank()) continue
                        cache[UUID.fromString(rawPlayer)] = gson.fromJson(data, ClientInfo::class.java)
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load client info for $rawPlayer: ${e.message}")
                    }
                }
            }
        }
        plugin.logger.info("Loaded client info for ${cache.size} player(s).")
    }

    /** Parses the pre-SQLite `client_info.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        for (key in yaml.getKeys(false)) {
            val playerId = try {
                UUID.fromString(key)
            } catch (_: IllegalArgumentException) {
                plugin.logger.warning("Ignoring invalid UUID in the legacy client info file: $key")
                continue
            }
            val section = yaml.getConfigurationSection(key) ?: continue
            try {
                val info = loadClientInfo(section)
                ops += SqlOp(
                    "INSERT INTO client_info (player, data, reported_at) VALUES (?, ?, ?)",
                    listOf(playerId, JsonColumn.encode(info), info.reportedAt),
                )
            } catch (e: Exception) {
                plugin.logger.warning("Could not import client info for $key: ${e.message}")
            }
        }
        return ops
    }

    private fun loadClientInfo(
        section: ConfigurationSection,
    ): ClientInfo {
        val activeLoaderSection =
            section.getConfigurationSection("active-loader")

        val activeLoader = if (activeLoaderSection != null) {
            Loader(
                type = activeLoaderSection.getString("type") ?: "unknown",
                version = activeLoaderSection.getString("version") ?: "unknown",
            )
        } else {
            parseLoader(section.getString("active-loader"))
        }

        return ClientInfo(
            minecraftVersion =
                section.getString("minecraft-version") ?: "unknown",

            activeLoader = activeLoader,

            previousLoaders = loadPreviousLoaders(section).takeLast(MAX_HISTORY),

            activeMods = loadModEntries(section),

            previousMods = loadPreviousMods(section).takeLast(MAX_HISTORY),

            activeResourcePacks = section
                .getStringList("active-resource-packs")
                .ifEmpty {
                    section.getStringList("resource-packs")
                },

            previousResourcePacks =
                section.getStringList("previous-resource-packs").takeLast(MAX_HISTORY),

            shaderModInstalled =
                section.getBoolean("shader-mod-installed", false),

            everShaderModInstalled =
                section.getBoolean(
                    "ever-shader-mod-installed",
                    section.getBoolean("shader-mod-installed", false),
                ),

            shadersEnabled =
                section.getBoolean("shaders-enabled", false),

            everShadersEnabled =
                section.getBoolean(
                    "ever-shaders-enabled",
                    section.getBoolean("shaders-enabled", false),
                ),

            activeShaderPack =
                section.getString("active-shader-pack"),

            previousShaderPacks =
                section.getStringList("previous-shader-packs").takeLast(MAX_HISTORY),

            reportedAt =
                section.getLong("reported-at", 0L),
        )
    }

    private fun loadPreviousLoaders(
        section: ConfigurationSection,
    ): List<Loader> {
        val raw = section.getList("previous-loaders")
            ?: return emptyList()

        return raw.mapNotNull { entry ->
            when (entry) {
                is Map<*, *> -> {
                    val type = entry["type"]?.toString()
                    val version = entry["version"]?.toString()

                    if (type != null && version != null) {
                        Loader(type, version)
                    } else {
                        null
                    }
                }

                is String -> parseLoader(entry)

                else -> null
            }
        }.distinct()
    }

    private fun loadPreviousMods(
        section: ConfigurationSection,
    ): List<OldModEntry> {
        val raw = section.getList("previous-mods")
            ?: return emptyList()

        return raw.mapNotNull { entry ->
            when (entry) {
                is Map<*, *> -> {
                    val loader = entry["loader"]?.toString()
                    val id = entry["id"]?.toString()

                    if (loader != null && id != null) {
                        OldModEntry(loader, id)
                    } else {
                        null
                    }
                }

                else -> null
            }
        }.distinct()
    }

    private fun loadModEntries(
        section: ConfigurationSection,
    ): List<ModEntry> {
        val raw = section.getList("active-mods")
            ?: section.getList("mods")
            ?: return emptyList()

        return raw.mapNotNull { entry ->
            when (entry) {
                is Map<*, *> -> {
                    val id = entry["id"]?.toString()
                    val version = entry["version"]?.toString()

                    if (id != null && version != null) {
                        ModEntry(id, version)
                    } else {
                        null
                    }
                }

                is String -> parseLegacyModEntry(entry)

                else -> null
            }
        }
    }

    private fun parseLegacyModEntry(
        value: String,
    ): ModEntry? {
        val separator = value.lastIndexOf(':')

        if (separator <= 0 || separator == value.lastIndex) {
            return null
        }

        return ModEntry(
            id = value.substring(0, separator),
            version = value.substring(separator + 1),
        )
    }

    private fun parseLoader(
        value: String?,
    ): Loader {
        if (value == null) {
            return Loader("unknown", "unknown")
        }

        val separator = value.lastIndexOf(':')

        if (separator <= 0 || separator == value.lastIndex) {
            return Loader(value, "unknown")
        }

        return Loader(
            type = value.substring(0, separator),
            version = value.substring(separator + 1),
        )
    }
}
