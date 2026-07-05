package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.stores.EventState
import de.davidsw.diawars.stores.GameEvent
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID

class EventManager(private val plugin: Diawars) {
    enum class SessionMode { BUILD, REVIEW, PLAY }
    data class Session(val eventId: String, val mode: SessionMode)

    sealed class Result {
        data class Success(val message: String): Result()
        data class Error(val message: String): Result()
    }

    // player -> which event/world they are currently inside of
    private val sessions = mutableMapOf<UUID, Session>()

    // eventId -> scheduled bukkit task ids (activate / end)
    private val scheduledTasks = mutableMapOf<String, MutableList<Int>>()

    private val store get() = plugin.store.eventStore
    private val states get() = plugin.store.playerStateStore

    fun getSession(playerId: UUID): Session? = sessions[playerId]

    // ------------------------------------------------------------------
    // Creation / building
    // ------------------------------------------------------------------

    fun createEvent(player: Player, name: String): Result {
        if (sessions.containsKey(player.uniqueId)) {
            return Result.Error("<red>Du befindest dich bereits in einem Event!</red>")
        }
        val alreadyOpen = store.getByCreator(player.uniqueId).any {
            it.state == EventState.BUILDING || it.state == EventState.SUBMITTED
        }
        if (alreadyOpen) {
            return Result.Error("<red>Du hast bereits ein Event in Bearbeitung oder zur Prüfung eingereicht!</red>")
        }

        lateinit var id: String
        try {
            id = store.generateId(name)
        } catch (e: Error) {
            return Result.Error("<red>${e.message}</red>")
        }

        val worldName = "event_$id"
        val world = WorldCreator(worldName).generateStructures(false).createWorld()
            ?: return Result.Error("<red>Die Event-Welt konnte nicht erstellt werden!</red>")

        val event = GameEvent(
            id = id,
            name = name,
            creator = player.uniqueId,
            state = EventState.BUILDING,
            worldName = worldName,
        )
        store.addEvent(event)

        states.saveState(player)
        sessions[player.uniqueId] = Session(id, SessionMode.BUILD)

        player.teleport(world.spawnLocation)
        player.gameMode = GameMode.CREATIVE
        player.inventory.clear()

        return Result.Success("<green>Event <gold>$name</gold> wurde erstellt! Du wurdest in die Event-Welt teleportiert.</green>")
    }

    fun resumeBuilding(player: Player): Result {
        if (sessions.containsKey(player.uniqueId)) {
            return Result.Error("<red>Du befindest dich bereits in einem Event!</red>")
        }
        val event = store.getByCreator(player.uniqueId).firstOrNull { it.state == EventState.BUILDING }
            ?: return Result.Error("<red>Du hast kein Event in Bearbeitung!</red>")

        val world = Bukkit.getWorld(event.worldName)
            ?: WorldCreator(event.worldName).createWorld()
            ?: return Result.Error("<red>Die Event-Welt konnte nicht geladen werden!</red>")

        states.saveState(player)
        sessions[player.uniqueId] = Session(event.id, SessionMode.BUILD)

        player.teleport(world.spawnLocation)
        player.gameMode = GameMode.CREATIVE
        player.inventory.clear()

        return Result.Success("<green>Du bist zu deinem Event <gold>${event.name}</gold> zurückgekehrt.</green>")
    }

    fun submitEvent(player: Player): Result {
        val session = sessions[player.uniqueId]
            ?: return Result.Error("<red>Du befindest dich in keinem Event!</red>")
        if (session.mode != SessionMode.BUILD) {
            return Result.Error("<red>Du kannst nur ein Event einreichen, an dem du gerade baust!</red>")
        }
        val event = store.getEvent(session.eventId)
            ?: return Result.Error("<red>Dieses Event existiert nicht mehr!</red>")

        event.state = EventState.SUBMITTED
        store.save()

        leaveWorld(player)

        return Result.Success("<green>Dein Event <gold>${event.name}</gold> wurde zur Prüfung eingereicht!</green>")
    }

    fun cancelEvent(player: Player): Result {
        val session = sessions[player.uniqueId]
            ?: return Result.Error("<red>Du befindest dich in keinem Event!</red>")
        if (session.mode != SessionMode.BUILD) {
            return Result.Error("<red>Du kannst nur ein Event abbrechen, an dem du gerade baust!</red>")
        }
        val event = store.getEvent(session.eventId)

        leaveWorld(player)

        if (event != null) {
            deleteEventWorld(event)
            store.removeEvent(event.id)
        }

        return Result.Success("<yellow>Dein Event wurde abgebrochen und gelöscht.</yellow>")
    }

    // ------------------------------------------------------------------
    // Review / admin
    // ------------------------------------------------------------------

    fun listByState(state: EventState): List<GameEvent> = store.getByState(state)

    fun reviewEvent(admin: Player, id: String): Result {
        if (sessions.containsKey(admin.uniqueId)) {
            return Result.Error("<red>Du befindest dich bereits in einem Event!</red>")
        }
        val event = store.getEvent(id) ?: return Result.Error("<red>Unbekanntes Event!</red>")
        if (event.state != EventState.SUBMITTED) {
            return Result.Error("<red>Dieses Event wartet nicht auf eine Prüfung!</red>")
        }

        val world = Bukkit.getWorld(event.worldName)
            ?: WorldCreator(event.worldName).createWorld()
            ?: return Result.Error("<red>Die Event-Welt konnte nicht geladen werden!</red>")

        states.saveState(admin)
        sessions[admin.uniqueId] = Session(event.id, SessionMode.REVIEW)

        admin.teleport(world.spawnLocation)
        admin.gameMode = GameMode.SPECTATOR

        return Result.Success("<green>Du prüfst nun das Event <gold>${event.name}</gold>. Mit <yellow>/event leave</yellow> beendest du die Prüfung.</green>")
    }

    fun acceptEvent(admin: Player, id: String, startDelayMinutes: Long, durationMinutes: Long): Result {
        val event = store.getEvent(id) ?: return Result.Error("<red>Unbekanntes Event!</red>")
        if (event.state != EventState.SUBMITTED) {
            return Result.Error("<red>Dieses Event wartet nicht auf eine Prüfung!</red>")
        }
        if (startDelayMinutes < 0 || durationMinutes <= 0) {
            return Result.Error("<red>Ungültiger Zeitraum!</red>")
        }

        val now = System.currentTimeMillis() / 1000
        event.startTime = now + startDelayMinutes * 60
        event.endTime = event.startTime + durationMinutes * 60
        event.state = EventState.ACCEPTED
        store.save()

        scheduleTransitions(event)

        Bukkit.getPlayer(event.creator)?.sendMessage(
            mm("<green>Dein Event <gold>${event.name}</gold> wurde angenommen und startet in <yellow>$startDelayMinutes Minute(n)</yellow>!</green>")
        )

        return Result.Success("<green>Event <gold>${event.name}</gold> wurde angenommen. Start in $startDelayMinutes Minute(n), Dauer $durationMinutes Minute(n).</green>")
    }

    fun rejectEvent(admin: Player, id: String): Result {
        val event = store.getEvent(id) ?: return Result.Error("<red>Unbekanntes Event!</red>")
        if (event.state != EventState.SUBMITTED) {
            return Result.Error("<red>Dieses Event wartet nicht auf eine Prüfung!</red>")
        }

        event.state = EventState.REJECTED
        store.save()

        Bukkit.getPlayer(event.creator)?.sendMessage(
            mm("<red>Dein Event <gold>${event.name}</gold> wurde abgelehnt. Mit <yellow>/event resume</yellow> kannst du weiterbauen und es erneut einreichen.</red>")
        )

        return Result.Success("<yellow>Event <gold>${event.name}</gold> wurde abgelehnt.</yellow>")
    }

    // ------------------------------------------------------------------
    // Scheduling (start/end of the timeframe)
    // ------------------------------------------------------------------

    fun scheduleTransitions(event: GameEvent) {
        cancelScheduledTasks(event.id)
        val now = System.currentTimeMillis() / 1000
        val tasks = mutableListOf<Int>()

        if (event.state == EventState.ACCEPTED) {
            val ticksUntilStart = (event.startTime - now).coerceAtLeast(0) * 20L
            tasks += plugin.server.scheduler.runTaskLater(plugin, Runnable {
                activateEvent(event.id)
            }, ticksUntilStart).taskId
        }

        val ticksUntilEnd = (event.endTime - now).coerceAtLeast(0) * 20L
        tasks += plugin.server.scheduler.runTaskLater(plugin, Runnable {
            endEvent(event.id)
        }, ticksUntilEnd).taskId

        scheduledTasks[event.id] = tasks
    }

    private fun cancelScheduledTasks(id: String) {
        scheduledTasks.remove(id)?.forEach { plugin.server.scheduler.cancelTask(it) }
    }

    fun activateEvent(id: String) {
        val event = store.getEvent(id) ?: return
        if (event.state != EventState.ACCEPTED) return

        event.state = EventState.ACTIVE
        store.save()

        Bukkit.broadcast(mm("<gold><bold>Das Event <yellow>${event.name}</yellow> ist jetzt live!</bold></gold> <gray>Beitreten mit</gray> <yellow>/event join ${event.id}</yellow>"))
    }

    fun endEvent(id: String) {
        val event = store.getEvent(id) ?: return
        if (event.state == EventState.ENDED) return

        cancelScheduledTasks(id)
        event.state = EventState.ENDED
        store.save()

        sessions.filter { it.value.eventId == id }.keys.toList().forEach { uuid ->
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            leaveWorld(player)
            player.sendMessage(mm("<gray>Das Event <gold>${event.name}</gold> ist zu Ende gegangen.</gray>"))
        }

        Bukkit.getWorld(event.worldName)?.let { Bukkit.unloadWorld(it, true) }
    }

    // ------------------------------------------------------------------
    // Joining / leaving an active event
    // ------------------------------------------------------------------

    fun joinEvent(player: Player, id: String): Result {
        if (sessions.containsKey(player.uniqueId)) {
            return Result.Error("<red>Du befindest dich bereits in einem Event!</red>")
        }
        val event = store.getEvent(id) ?: return Result.Error("<red>Unbekanntes Event!</red>")
        if (event.state != EventState.ACTIVE) {
            return Result.Error("<red>Dieses Event ist derzeit nicht aktiv!</red>")
        }

        val world = Bukkit.getWorld(event.worldName)
            ?: WorldCreator(event.worldName).createWorld()
            ?: return Result.Error("<red>Die Event-Welt konnte nicht geladen werden!</red>")

        states.saveState(player)
        sessions[player.uniqueId] = Session(event.id, SessionMode.PLAY)

        player.teleport(world.spawnLocation)
        player.gameMode = GameMode.SURVIVAL
        player.inventory.clear()

        return Result.Success("<green>Du bist dem Event <gold>${event.name}</gold> beigetreten!</green>")
    }

    fun leaveEvent(player: Player): Result {
        if (!sessions.containsKey(player.uniqueId)) {
            return Result.Error("<red>Du befindest dich in keinem Event!</red>")
        }
        leaveWorld(player)
        return Result.Success("<green>Du hast das Event verlassen.</green>")
    }

    private fun leaveWorld(player: Player) {
        sessions.remove(player.uniqueId)
        if (!states.restoreState(player)) {
            player.gameMode = GameMode.SURVIVAL
            player.teleport(plugin.server.worlds.first().spawnLocation)
        }
    }

    // ------------------------------------------------------------------
    // Cleanup / safety
    // ------------------------------------------------------------------

    private fun deleteEventWorld(event: GameEvent) {
        Bukkit.getWorld(event.worldName)?.let { Bukkit.unloadWorld(it, false) }
        val folder = File(Bukkit.getWorldContainer(), event.worldName)
        if (folder.exists()) {
            folder.deleteRecursively()
        }
    }

    /**
     * Called on player join. If the plugin/server restarted while a player was
     * still inside an event world, their in-memory session is gone but their
     * saved state on disk isn't - restore them safely instead of leaving them
     * stuck with an event inventory in a possibly-unloaded world.
     */
    fun handlePlayerJoin(player: Player) {
        if (!sessions.containsKey(player.uniqueId) && states.hasSavedState(player.uniqueId)) {
            states.restoreState(player)
            player.sendMessage(mm("<yellow>Deine Event-Sitzung wurde durch einen Serverneustart unterbrochen. Du wurdest zurückgesetzt.</yellow>"))
        }
    }

    /** Re-schedules pending start/end transitions after a plugin/server restart. */
    fun reactivateSchedules() {
        for (event in store.getAll().toList()) {
            when (event.state) {
                EventState.ACCEPTED, EventState.ACTIVE -> {
                    val now = System.currentTimeMillis() / 1000
                    if (event.endTime in 1..now) {
                        endEvent(event.id)
                    } else {
                        if (event.state == EventState.ACCEPTED && event.startTime in 1..now) {
                            event.state = EventState.ACTIVE
                        }
                        scheduleTransitions(event)
                    }
                }
                else -> {}
            }
        }
        store.save()
    }
}