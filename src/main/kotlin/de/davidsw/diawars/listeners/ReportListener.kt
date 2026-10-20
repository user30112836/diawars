package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Join/quit hooks for the report system: admins get a chat notice about open
 * reports (mirrors BugListener), quitting players drop their pending report
 * and screenshot upload session.
 */
class ReportListener(private val plugin: Diawars) : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.reportManager.handleAdminJoin(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        plugin.reportManager.clearPlayer(event.player.uniqueId)
    }
}
