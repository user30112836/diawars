package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.pmm
import org.bukkit.Bukkit.getCurrentTick
import org.bukkit.Bukkit.getPlayer
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AfkManager(private val plugin: Diawars) {
    private val lastActivity = ConcurrentHashMap<UUID, Int>()
    private val afkPlayers = mutableSetOf<UUID>()
    private var taskId = -1

    private val afkThresholdTicks get() = plugin.config.getInt("afk.threshold-seconds", 300) * 20

    fun recordActivity(playerId: UUID) {
        lastActivity[playerId] = getCurrentTick()
        if (afkPlayers.remove(playerId)) {
            getPlayer(playerId)?.let { updateListName(it, false) }
            val player = getPlayer(playerId) ?: return
            plugin.rewardManager.startPlaytimeReward(player)
        }
    }

    fun isAfk(playerId: UUID): Boolean {
        return afkPlayers.contains(playerId)
    }

    fun cleanupPlayer(playerId: UUID) {
        lastActivity.remove(playerId)
        afkPlayers.remove(playerId)
    }

    fun start() {
        stop()
        taskId = plugin.server.scheduler.runTaskTimer(plugin, Runnable { checkAfk() }, 20L, 20L).taskId
    }

    fun stop() {
        if (taskId != -1) {
            plugin.server.scheduler.cancelTask(taskId)
            taskId = -1
        }
    }

    private fun checkAfk() {
        val now = getCurrentTick()
        for (player in plugin.server.onlinePlayers) {
            val last = lastActivity[player.uniqueId] ?: now
            val isAfkNow = now - last >= afkThresholdTicks
            val wasAfk = afkPlayers.contains(player.uniqueId)

            if (isAfkNow && !wasAfk) {
                afkPlayers.add(player.uniqueId)
                updateListName(player, true)
                plugin.rewardManager.stopPlaytimeReward(player)
            }
        }
    }

    private fun updateListName(player: Player, isAfk: Boolean) {
        plugin.diamondScoreboardManager.update()
        if (isAfk) {
            player.sendMessage(pmm("<dark_gray>Du bist jetzt als AFK markiert</dark_gray>"))
        }
    }
}