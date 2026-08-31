package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.DiamondCounter
import de.davidsw.diawars.managers.Team
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.collections.iterator

class PlayerDiamondStore(plugin: Diawars) : YamlStore(plugin, "diamond_scores.yml") {
    private val cache = mutableMapOf<UUID, Int>()
    private val names = mutableMapOf<UUID, String>()

    init {
        load()
    }

    fun getStoredCount(playerId: UUID): Int = cache.getOrDefault(playerId, 0)

    fun snapshot(player: Player) {
        val count = DiamondCounter.countForPlayer(player)
        updateCache(player.uniqueId, player.name, count)
        saveImmediately()
    }

    fun snapshotIfChanged(player: Player) {
        val count = DiamondCounter.countForPlayer(player)
        if (cache[player.uniqueId] == count) return
        updateCache(player.uniqueId, player.name, count)
        markDirty()
    }

    fun saveCount(player: Player, count: Int) {
        updateCache(player.uniqueId, player.name, count)
        markDirty()
    }

    fun getOfflineTeamCount(team: Team): Int {
        val onlineIds = plugin.server.onlinePlayers.map { it.uniqueId }
        return plugin.teamManager.getTeamMembers(team)
            .filter { it !in onlineIds }
            .sumOf { getStoredCount(it) }
    }

    fun getOnlineTeamCount(team: Team): Int {
        val onlineIds = plugin.server.onlinePlayers.map { it.uniqueId }
        return plugin.teamManager.getTeamMembers(team)
            .filter { it in onlineIds }
            .sumOf { getStoredCount(it) }
    }

    fun getTotalTeamCount(team: Team): Int {
        return plugin.teamManager.getTeamMembers(team).sumOf { getStoredCount(it) }
    }

    override fun readFrom(yaml: YamlConfiguration) {
        for (key in yaml.getKeys(false)) {
            try {
                val uuid = UUID.fromString(key)
                val section = yaml.getConfigurationSection(key) ?: continue
                cache[uuid] = section.getInt("diamonds", 0)
                names[uuid] = section.getString("name") ?: "unknown"
            } catch (e: Exception) {
                plugin.logger.warning("Could not load diamond score for $key: ${e.message}")
            }
        }

        plugin.logger.info("Loaded diamond scores for ${cache.size} player(s).")
    }

    private fun updateCache(playerId: UUID, playerName: String, count: Int) {
        cache[playerId] = count
        names[playerId] = playerName
    }

    override fun writeTo(yaml: YamlConfiguration) {
        for ((uuid, count) in cache) {
            val key = uuid.toString()
            yaml.set("$key.diamonds", count)
            yaml.set("$key.name", names[uuid] ?: "unknown")
        }
    }
}
