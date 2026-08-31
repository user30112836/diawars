package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getCurrentTick
import org.bukkit.configuration.file.YamlConfiguration
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

private const val TICKS_PER_SECOND = 20

class PvPStatusStore(plugin: Diawars) : YamlStore(plugin, "pvp_status.yml") {
    private val cache = mutableMapOf<UUID, PvPStatus>()
    private val toggleDelaySeconds get() = plugin.config.getInt("pvp-toggle.delay-seconds", 300)

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
        val elapsed = (getCurrentTick() - startTime) / TICKS_PER_SECOND
        return (toggleDelaySeconds.toLong() - elapsed).coerceAtLeast(0)
    }

    fun setRemainingTimeSeconds(playerId: UUID, seconds: Int) {
        val elapsedTicks = (toggleDelaySeconds - seconds.coerceIn(0, toggleDelaySeconds)) * TICKS_PER_SECOND
        cache[playerId] = PvPStatus(
            pvpEnabled = cache[playerId]?.pvpEnabled ?: true,
            toggleActive = true,
            toggleStartTime = getCurrentTick() - elapsedTicks,
            toggleDestination = cache[playerId]?.toggleDestination ?: true,
            oldTimeRemaining = 0,
        )
        markDirty()
    }

    fun applyPvPStatus(playerId: UUID, status: Boolean) {
        cache[playerId] = PvPStatus(
            pvpEnabled = status,
            toggleActive = false,
            toggleStartTime = cache[playerId]?.toggleStartTime ?: getCurrentTick(),
            toggleDestination = status,
            oldTimeRemaining = 0,
        )
        markDirty()
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
        markDirty()
    }

    fun removeToggle(playerId: UUID) {
        cache[playerId] = PvPStatus(
            pvpEnabled = cache[playerId]?.pvpEnabled ?: true,
            toggleActive = false,
            toggleStartTime = cache[playerId]?.toggleStartTime ?: getCurrentTick(),
            toggleDestination = cache[playerId]?.toggleDestination ?: true,
            oldTimeRemaining = 0,
        )
        markDirty()
    }

    override fun readFrom(yaml: YamlConfiguration) {
        for (key in yaml.getKeys(false)) {
            try {
                val uuid = UUID.fromString(key)
                val section = yaml.getConfigurationSection(key) ?: continue
                cache[uuid] = PvPStatus(
                    section.getBoolean("enabled", plugin.config.getBoolean("pvp-toggle.default-enabled", true)),
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

    override fun writeTo(yaml: YamlConfiguration) {
        for ((uuid, status) in cache) {
            val key = uuid.toString()
            yaml.set("$key.enabled", status.pvpEnabled)
            yaml.set("$key.toggle", status.toggleActive)
            yaml.set("$key.startTime", status.toggleStartTime)
            yaml.set("$key.destination", status.toggleDestination)

            if (finalFlush && status.toggleActive) {
                yaml.set("$key.old-time", getRemainingTime(uuid).toInt())
            }
        }
    }
}
