package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getCurrentTick
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

class PvPManager(private val plugin: Diawars) {
    private val pvpStatus = ConcurrentHashMap<UUID, Boolean>()
    private val pendingToggles = ConcurrentHashMap<UUID, PendingToggle>()
    private val actionbarTasks = ConcurrentHashMap<UUID, Int>()
    private val toggleDelaySeconds = 300L

    data class PendingToggle(
        val targetStatus: Boolean,
        val startTime: Int,
        val taskId: Int,
    )

    fun isPvPEnabled(playerId: UUID): Boolean {
        return pvpStatus.getOrDefault(playerId, true)
    }

    fun hasPendingToggle(playerId: UUID): Boolean {
        return pendingToggles.containsKey(playerId)
    }

    fun getToggleDestination(playerId: UUID): Boolean? {
        val pending = pendingToggles[playerId] ?: return null
        return pending.targetStatus
    }

    fun getRemainingTime(playerId: UUID): Long {
        val pending = pendingToggles[playerId] ?: return 0
        val elapsed = (getCurrentTick() - pending.startTime) / 20
        return (toggleDelaySeconds - elapsed).coerceAtLeast(0)
    }

    fun togglePvP(playerId: UUID): ToggleResult {
        if (hasPendingToggle(playerId)) {
            return ToggleResult.ALREADY_PENDING
        }

        val currentStatus = isPvPEnabled(playerId)
        val targetStatus = !currentStatus

        val taskId = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            applyPvPStatus(playerId, targetStatus)
            pendingToggles.remove(playerId)
        }, toggleDelaySeconds * 20L).taskId

        pendingToggles[playerId] = PendingToggle(
            targetStatus = targetStatus,
            startTime = getCurrentTick(),
            taskId = taskId,
        )

        return if (targetStatus) {
            ToggleResult.ENABLING
        } else {
            ToggleResult.DISABLING
        }
    }

    fun cancelToggle(playerId: UUID): Boolean {
        val pending = pendingToggles.remove(playerId) ?: return false
        Bukkit.getScheduler().cancelTask(pending.taskId)
        return true
    }

    private fun applyPvPStatus(playerId: UUID, status: Boolean) {
        pvpStatus[playerId] = status
        val statusText = if (status) "aktiviert" else "deaktiviert"
        Bukkit.getPlayer(playerId)?.sendMessage("§aDein PvP-Status wurde §e$statusText§a!")
    }

    fun cancelAllPendingToggles() {
        pendingToggles.values.forEach { pending ->
            Bukkit.getScheduler().cancelTask(pending.taskId)
        }
        pendingToggles.clear()
    }

    fun cleanupPlayer(playerId: UUID) {
        cancelToggle(playerId)
        stopActionbar(Bukkit.getPlayer(playerId) ?: return)
    }

    fun startActionbar(player: Player) {
        val actionBarTaskId = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (hasPendingToggle(player.uniqueId)) {
                val minutes = floor(getRemainingTime(player.uniqueId).toDouble() / 60).toInt()
                val seconds = (getRemainingTime(player.uniqueId).toDouble() % 60).toInt()
                val timeText = if (minutes != 0) "${minutes}:${if (seconds < 10) "0${seconds}" else seconds.toString()}" else seconds.toString()
                if (pendingToggles[player.uniqueId]?.targetStatus ?: false) {
                    player.sendActionBar(MiniMessage.miniMessage().deserialize("<yellow>PvP-Aktivierung in ${timeText}</yellow>"))
                } else {
                    player.sendActionBar(MiniMessage.miniMessage().deserialize("<yellow>PvP-Deaktivierung in ${timeText}</yellow>"))
                }
            } else {
                if (isPvPEnabled(player.uniqueId)) {
                    player.sendActionBar(MiniMessage.miniMessage().deserialize("<green>PvP aktiviert</green>"))
                } else {
                    player.sendActionBar(MiniMessage.miniMessage().deserialize("<red>PvP deaktiviert</red>"))
                }
            }
        }, 0, 20).taskId
        actionbarTasks[player.uniqueId] = actionBarTaskId
    }

    fun stopActionbar(player: Player): Boolean {
        val task = actionbarTasks.remove(player.uniqueId) ?: return false
        Bukkit.getScheduler().cancelTask(task)
        return true
    }

    enum class ToggleResult {
        ALREADY_PENDING, ENABLING, DISABLING
    }
}