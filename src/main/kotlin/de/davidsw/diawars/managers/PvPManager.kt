package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getCurrentTick
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.floor

class PvPManager(private val plugin: Diawars) {
    private val store = plugin.store.pvpStatusStore
    private val toggleDelaySeconds = 5 * 60L
    private val fightTime = 2 * 60 * 20
    private val actionbarTasks = ConcurrentHashMap<UUID, Int>()
    private val toggleTasks = ConcurrentHashMap<UUID, Int>()
    private val lastFight = ConcurrentHashMap<UUID, Int>()

    fun isInFight(playerId: UUID): Boolean {
        val lastFightTime = lastFight.getOrDefault(playerId, -fightTime)
        return lastFightTime - getCurrentTick() < -fightTime
    }

    fun fightTimeRemaining(playerId: UUID): Int {
        return fightTime - (lastFight.getOrDefault(playerId, -fightTime)) - getCurrentTick()
    }

    fun storeFight(player: Player) {
        lastFight[player.uniqueId] = getCurrentTick()
        if (store.hasPendingToggle(player.uniqueId)) {
            cancelToggle(player.uniqueId)
            val message = mm("""
                <red>Dein PvP Status wird nicht mehr geändert, da du dich in einem Kampf befindest!</red>
            """.trimIndent())
            player.sendMessage(message)
        }
    }

    fun togglePvP(playerId: UUID): ToggleResult {
        if (store.hasPendingToggle(playerId)) {
            return ToggleResult.ALREADY_PENDING
        }
        if (isInFight(playerId)) {
            return ToggleResult.IN_FIGHT
        }

        val currentStatus = store.isPvPEnabled(playerId)
        val targetStatus = !currentStatus

        val taskId = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            store.applyPvPStatus(playerId, targetStatus)
        }, toggleDelaySeconds * 20L).taskId
        toggleTasks[playerId] = taskId

        store.applyToggle(playerId, targetStatus)

        return if (targetStatus) {
            ToggleResult.ENABLING
        } else {
            ToggleResult.DISABLING
        }
    }

    fun continueToggle(playerId: UUID, targetStatus: Boolean, delay: Int) {
        val taskId = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            store.applyPvPStatus(playerId, targetStatus)
        }, delay * 20L).taskId
        toggleTasks[playerId] = taskId
    }

    fun cancelToggle(playerId: UUID): Boolean {
        store.removeToggle(playerId)
        val taskId = toggleTasks.remove(playerId) ?: return false
        Bukkit.getScheduler().cancelTask(taskId)
        return true
    }

    fun cleanupPlayer(playerId: UUID) {
        cancelToggle(playerId)
        stopActionbar(Bukkit.getPlayer(playerId) ?: return)
    }

    fun startActionbar(player: Player) {
        val actionBarTaskId = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (store.hasPendingToggle(player.uniqueId)) {
                val minutes = floor(store.getRemainingTime(player.uniqueId).toDouble() / 60).toInt()
                val seconds = (store.getRemainingTime(player.uniqueId).toDouble() % 60).toInt()
                val timeText = if (minutes != 0) "${minutes}:${if (seconds < 10) "0${seconds}" else seconds.toString()}" else seconds.toString()
                if (store.getToggleDestination(player.uniqueId) ?: false) {
                    player.sendActionBar(mm("<yellow>PvP-Aktivierung in ${timeText}</yellow>"))
                } else {
                    player.sendActionBar(mm("<yellow>PvP-Deaktivierung in ${timeText}</yellow>"))
                }
            } else {
                if (store.isPvPEnabled(player.uniqueId)) {
                    if (isInFight(player.uniqueId)) {
                        val timeLeft = fightTimeRemaining(player.uniqueId)
                        val minutes = floor(timeLeft.toDouble() / 60).toInt()
                        val seconds = (timeLeft % 60)
                        val timeText = if (minutes != 0) "${minutes}:${if (seconds < 10) "0${seconds}" else seconds.toString()}" else seconds.toString()
                        player.sendActionBar(mm("""
                            <yellow>Du bist in einem Kampf für ${timeText}</yellow>
                            <red><bold>Nicht ausloggen!</bold></red>
                        """.trimMargin()))
                    } else {
                        player.sendActionBar(mm("<green>PvP aktiviert</green>"))
                    }
                } else {
                    player.sendActionBar(mm("<red>PvP deaktiviert</red>"))
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

    fun reactivateTasks() {
        for ((playerId, status) in store.getAll().entries) {
            if (status.toggleActive) {
                val timeLeft = status.oldTimeRemaining
                if (timeLeft <= 0) {
                    store.applyPvPStatus(playerId, status.toggleDestination)
                } else {
                    store.setRemainingTimeTicks(playerId, timeLeft)
                    continueToggle(playerId, status.toggleDestination, timeLeft)
                }
            }
        }
    }

    enum class ToggleResult {
        ALREADY_PENDING, ENABLING, DISABLING, IN_FIGHT
    }
}