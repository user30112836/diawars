package de.davidsw.diawars.stores

import com.google.gson.Gson
import com.google.gson.JsonParseException
import de.davidsw.diawars.Diawars
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

data class ModEntry(
    var id: String = "",
    var version: String = "",
)

data class OldModEntry(
    val loader: String,
    val id: String,
)

data class Loader(
    val type: String,
    val version: String,
)

data class ClientInfo(
    val minecraftVersion: String,
    val activeLoader: Loader,
    val previousLoaders: List<Loader> = emptyList(),

    val activeMods: List<ModEntry>,
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
) : YamlStore(plugin, "client_info.yml") {

    private val cache = mutableMapOf<UUID, ClientInfo>()
    private val gson = Gson()
    private val reportedPlayers = mutableSetOf<UUID>()

    init {
        load()
    }

    fun hasReported(playerId: UUID): Boolean {
        return playerId in reportedPlayers
    }

    fun addReported(playerId: UUID) {
        reportedPlayers.add(playerId)
    }

    fun removeReported(playerId: UUID) {
        reportedPlayers.remove(playerId)
    }

    fun getInfo(playerId: UUID): ClientInfo? {
        return cache[playerId]
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

            saveImmediately()

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

        return (oldInfo.previousLoaders + oldInfo.activeLoader).distinct()
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

        return (oldInfo.previousMods + oldEntries).distinct()
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
                ).distinct()
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
                ).distinct()
    }

    override fun writeTo(yaml: YamlConfiguration) {
        for ((uuid, info) in cache) {
            saveClientInfo(yaml, uuid, info)
        }
    }

    private fun saveClientInfo(
        config: YamlConfiguration,
        uuid: UUID,
        info: ClientInfo,
    ) {
        val path = uuid.toString()

        config.set("$path.minecraft-version", info.minecraftVersion)
        config.set("$path.reported-at", info.reportedAt)

        config.set(
            "$path.active-loader.type",
            info.activeLoader.type,
        )
        config.set(
            "$path.active-loader.version",
            info.activeLoader.version,
        )

        config.set(
            "$path.previous-loaders",
            info.previousLoaders.map { loader ->
                mapOf(
                    "type" to loader.type,
                    "version" to loader.version,
                )
            },
        )

        config.set(
            "$path.active-mods",
            info.activeMods.map { mod ->
                mapOf(
                    "id" to mod.id,
                    "version" to mod.version,
                )
            },
        )

        config.set(
            "$path.previous-mods",
            info.previousMods.map { mod ->
                mapOf(
                    "loader" to mod.loader,
                    "id" to mod.id,
                )
            },
        )

        config.set(
            "$path.active-resource-packs",
            info.activeResourcePacks,
        )

        config.set(
            "$path.previous-resource-packs",
            info.previousResourcePacks,
        )

        config.set(
            "$path.shader-mod-installed",
            info.shaderModInstalled,
        )

        config.set(
            "$path.ever-shader-mod-installed",
            info.everShaderModInstalled,
        )

        config.set(
            "$path.shaders-enabled",
            info.shadersEnabled,
        )

        config.set(
            "$path.ever-shaders-enabled",
            info.everShadersEnabled,
        )

        config.set(
            "$path.active-shader-pack",
            info.activeShaderPack,
        )

        config.set(
            "$path.previous-shader-packs",
            info.previousShaderPacks,
        )
    }

    override fun readFrom(yaml: YamlConfiguration) {
        cache.clear()

        for (key in yaml.getKeys(false)) {
            val uuid = try {
                UUID.fromString(key)
            } catch (_: IllegalArgumentException) {
                plugin.logger.warning(
                    "Ignoring invalid UUID in ${storeFile.name}: $key"
                )
                continue
            }

            val section = yaml.getConfigurationSection(key)
                ?: continue

            try {
                cache[uuid] = loadClientInfo(section)
            } catch (e: Exception) {
                plugin.logger.warning(
                    "Could not load client info for $key: ${e.message}"
                )
            }
        }

        plugin.logger.info(
            "Loaded client info for ${cache.size} player(s)."
        )
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

            previousLoaders = loadPreviousLoaders(section),

            activeMods = loadModEntries(section),

            previousMods = loadPreviousMods(section),

            activeResourcePacks = section
                .getStringList("active-resource-packs")
                .ifEmpty {
                    section.getStringList("resource-packs")
                },

            previousResourcePacks =
                section.getStringList("previous-resource-packs"),

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
                section.getStringList("previous-shader-packs"),

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
