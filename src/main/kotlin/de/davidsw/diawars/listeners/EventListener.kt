package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerRecipeDiscoverEvent
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.UUID

class EventListener(private val plugin: Diawars): Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.eventManager.handlePlayerJoin(event.player)
    }

    // ------------------------------------------------------------------
    // Bug 2: no item smuggling across event-world borders.
    // The only legal exit is EventManager.leaveWorld (via /event leave,
    // submit/cancel, event end ...), which swaps the event inventory back to
    // the saved main-world state. Any other world change would carry the live
    // event inventory along, so it is cancelled here. Legal leaveWorld
    // teleports already removed the session, so they are unaffected.
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val from = event.from.world ?: return
        val to = event.to.world ?: return
        if (from.name == to.name) return
        if (isIllegalExit(event.player.uniqueId, from.name, to.name)) {
            event.isCancelled = true
            event.player.sendMessage(mm("<red>Du kannst die Event-Welt nur mit <yellow>/event leave</yellow> verlassen!</red>"))
            return
        }
        if (isIllegalEntry(event.player.uniqueId, to.name)) {
            event.isCancelled = true
            event.player.sendMessage(mm("<red>Du kannst Event-Welten nur über die Event-Befehle betreten!</red>"))
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerPortal(event: PlayerPortalEvent) {
        val from = event.from.world ?: return
        val toWorld = event.to.world ?: return
        if (from.name == toWorld.name) return
        if (isIllegalExit(event.player.uniqueId, from.name, toWorld.name)) {
            event.isCancelled = true
            event.player.sendMessage(mm("<red>Du kannst die Event-Welt nur mit <yellow>/event leave</yellow> verlassen!</red>"))
            return
        }
        if (isIllegalEntry(event.player.uniqueId, toWorld.name)) {
            event.isCancelled = true
            event.player.sendMessage(mm("<red>Du kannst Event-Welten nur über die Event-Befehle betreten!</red>"))
        }
    }

    /**
     * Safety net for teleports that bypass PlayerTeleportEvent (other plugins,
     * NMS): convert the illegal exit into a legal one instead of leaking the
     * event inventory into the main world. leaveWorld persists the carried
     * event items and restores the saved main-world state; the session is
     * removed first thing there, so the follow-up teleport cannot loop here.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        if (plugin.eventManager.getSession(player.uniqueId) == null) return
        val expected = plugin.eventManager.getSessionWorldName(player.uniqueId)
        // Still inside the correct event world: nothing to do.
        if (expected != null && player.world.name == expected) return
        plugin.eventManager.leaveWorld(player)
    }

    private fun isIllegalExit(playerId: UUID, fromWorld: String, toWorld: String): Boolean {
        if (plugin.eventManager.getSession(playerId) == null) return false
        if (!plugin.eventManager.isEventWorld(fromWorld)) return false
        return fromWorld != toWorld
    }

    private fun isIllegalEntry(playerId: UUID, toWorld: String): Boolean {
        if (plugin.eventManager.getSession(playerId) != null) return false
        return plugin.eventManager.isEventWorld(toWorld)
    }

    // ------------------------------------------------------------------
    // Bug 3: no advancement progression inside event worlds.
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onAdvancementDone(event: PlayerAdvancementDoneEvent) {
        if (!plugin.eventManager.isEventWorld(event.player.world.name)) return
        // PlayerAdvancementDoneEvent is only informative: revoke the just-awarded
        // criteria so the advancement (and its rewards/recipes) does not stick.
        // Deferred by one tick: revoking inside the event can race the grant.
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!event.player.isOnline) return@Runnable
            val current = event.player.getAdvancementProgress(event.advancement)
            current.awardedCriteria.toList().forEach { criteria ->
                try {
                    current.revokeCriteria(criteria)
                } catch (_: Exception) {
                    // Already revoked / race: ignore, advancement stays locked.
                }
            }
        })
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onRecipeDiscover(event: PlayerRecipeDiscoverEvent) {
        if (!plugin.eventManager.isEventWorld(event.player.world.name)) return
        event.isCancelled = true
    }
}
