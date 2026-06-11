package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getCurrentTick
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

data class PvPStatus(
    val pvpEnabled: Boolean,
    val toggleActive: Boolean,
    val toggleStartTime: Int,
    val toggleDestination: Boolean,
    val oldTimeRemaining: Int,
)

class PvPStatusStore(private val plugin: Diawars) {
    private val storeFile = File(plugin.dataFolder, "pvp_status.yml")
    private val cache = mutableMapOf<UUID, PvPStatus>()
    private val toggleDelaySeconds = 300L

    init {
        load()
    }

    fun getAll(): MutableMap<UUID, PvPStatus> {
        return cache
    }

    fun isPvPEnabled(playerId: UUID): Boolean {
        return cache[playerId]?.pvpEnabled ?: true
    }

    fun hasPendingToggle(playerId: UUID): Boolean {
        return cache[playerId]?.toggleActive ?: false
    }

    fun getToggleDestination(playerId: UUID): Boolean? {
        return cache[playerId]?.toggleDestination
    }

    fun getRemainingTime(playerId: UUID): Long {
        val startTime = cache[playerId]?.toggleStartTime ?: return 0
        val elapsed = (getCurrentTick() - startTime) / 20
        return (toggleDelaySeconds - elapsed).coerceAtLeast(0)
    }

    fun setRemainingTimeTicks(playerId: UUID, ticks: Int) {
        cache[playerId] = PvPStatus(
            pvpEnabled = cache[playerId]?.pvpEnabled ?: true,
            toggleActive = true,
            toggleStartTime = (getCurrentTick() - (toggleDelaySeconds - ticks)).toInt(),
            toggleDestination = cache[playerId]?.toggleDestination ?: true,
            oldTimeRemaining = 0,
        )
        save()
    }

    fun applyPvPStatus(playerId: UUID, status: Boolean) {
        cache[playerId] = PvPStatus(
            pvpEnabled = status,
            toggleActive = false,
            toggleStartTime = cache[playerId]?.toggleStartTime ?: getCurrentTick(),
            toggleDestination = status,
            oldTimeRemaining = 0,
        )
        save()
        val statusText = if (status) "aktiviert" else "deaktiviert"
        Bukkit.getPlayer(playerId)?.sendMessage(mm("<green>Dein PvP-Status wurde <yellow>$statusText</yellow>!</green>"))
    }

    fun applyToggle(playerId: UUID, destination: Boolean) {
        cache[playerId] = PvPStatus(
            pvpEnabled = cache[playerId]?.pvpEnabled ?: !destination,
            toggleActive = true,
            toggleStartTime = getCurrentTick(),
            toggleDestination = destination,
            oldTimeRemaining = 0,
        )
        save()
    }

    fun removeToggle(playerId: UUID) {
        cache[playerId] = PvPStatus(
            pvpEnabled = cache[playerId]?.pvpEnabled ?: true,
            toggleActive = false,
            toggleStartTime = cache[playerId]?.toggleStartTime ?: getCurrentTick(),
            toggleDestination = cache[playerId]?.toggleDestination ?: true,
            oldTimeRemaining = 0,
        )
        save()
    }

    private fun load() {
        if (!storeFile.exists()) {
            storeFile.parentFile.mkdirs()
            storeFile.createNewFile()
        }

        val yaml = YamlConfiguration.loadConfiguration(storeFile)

        for (key in yaml.getKeys(false)) {
            try {
                val uuid = UUID.fromString(key)
                val section = yaml.getConfigurationSection(key) ?: continue
                cache[uuid] = PvPStatus(
                    section.getBoolean("enabled", true),
                    section.getBoolean("toggle", false),
                    section.getInt("startTime", 0),
                    section.getBoolean("destination", true),
                    section.getInt("old-time", 0),
                )
            } catch (e: Exception) {
                plugin.logger.warning("Could not load pvp status for $key: ${e.message}")
            }
        }

        plugin.logger.info("Loaded pvp status for ${cache.size} player(s).")
    }

    private fun save(shutdown: Boolean = false) {
        val config = YamlConfiguration()

        for ((uuid, status) in cache) {
            val key = uuid.toString()
            config.set("$key.enabled", status.pvpEnabled)
            config.set("$key.toggle", status.toggleActive)
            config.set("$key.startTime", status.toggleStartTime)
            config.set("$key.destination", status.toggleDestination)
            if (shutdown) {
                config.set("$key.old-time", (toggleDelaySeconds - (getCurrentTick() - status.toggleStartTime)).toInt())
            }
        }

        try {
            config.save(storeFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save pvp status to $storeFile: ${e.message}")
        }
    }

    fun stop() {
        save(true)
    }
}