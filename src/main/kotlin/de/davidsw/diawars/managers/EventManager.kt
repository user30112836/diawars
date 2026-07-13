package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.stores.EventState
import de.davidsw.diawars.stores.GameEvent
import de.davidsw.diawars.util.DateTimeParser
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit.broadcast
import org.bukkit.Bukkit.getOfflinePlayer
import org.bukkit.Bukkit.getPlayer
import org.bukkit.Bukkit.getWorld
import org.bukkit.Bukkit.getWorldContainer
import org.bukkit.Bukkit.unloadWorld
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
    fun isEventWorld(worldName: String): Boolean = store.getByWorld(worldName) != null

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
        player.enderChest.clear()

        return Result.Success("<green>Event <gold>$name</gold> wurde erstellt! Du wurdest in die Event-Welt teleportiert.</green>")
    }

    fun resumeBuilding(player: Player): Result {
        if (sessions.containsKey(player.uniqueId)) {
            return Result.Error("<red>Du befindest dich bereits in einem Event!</red>")
        }
        val event = store.getByCreator(player.uniqueId).firstOrNull { it.state == EventState.BUILDING }
            ?: return Result.Error("<red>Du hast kein Event in Bearbeitung!</red>")

        val world = getWorld(event.worldName)
            ?: WorldCreator(event.worldName).createWorld()
            ?: return Result.Error("<red>Die Event-Welt konnte nicht geladen werden!</red>")

        states.saveState(player)
        sessions[player.uniqueId] = Session(event.id, SessionMode.BUILD)

        player.teleport(world.spawnLocation)
        player.gameMode = GameMode.CREATIVE
        player.inventory.clear()
        player.enderChest.clear()

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

        val world = getWorld(event.worldName)
            ?: WorldCreator(event.worldName).createWorld()
            ?: return Result.Error("<red>Die Event-Welt konnte nicht geladen werden!</red>")

        states.saveState(admin)
        sessions[admin.uniqueId] = Session(event.id, SessionMode.REVIEW)

        admin.teleport(world.spawnLocation)
        admin.gameMode = GameMode.SPECTATOR

        return Result.Success("<green>Du prüfst nun das Event <gold>${event.name}</gold>. Mit <yellow>/event leave</yellow> beendest du die Prüfung.</green>")
    }

    fun acceptEvent(id: String, startEpoch: Long, endEpoch: Long): Result {
        val event = store.getEvent(id) ?: return Result.Error("<red>Unbekanntes Event!</red>")
        if (event.state != EventState.SUBMITTED) {
            return Result.Error("<red>Dieses Event wartet nicht auf eine Prüfung!</red>")
        }
        val now = System.currentTimeMillis() / 1000
        if (endEpoch <=startEpoch) return Result.Error("<red>Das Ende muss nach dem Start liegen!</red>")
        if (endEpoch <= now) return Result.Error("<red>Das Ende darf nicht in der Vergangenheit liegen!</red>")

        event.startTime = startEpoch
        event.endTime = endEpoch
        event.state = EventState.ACCEPTED
        store.save()

        val creator = getOfflinePlayer(event.creator)
        if (creator !is Player) return Result.Error("<red>Der Ersteller ist unbekannt!</red>")

        val rewardAmount = plugin.config.getInt("event.accept-reward-diamonds", 10)
        if (rewardAmount > 0) {
            plugin.rewardManager.grantDiamondReward(creator, rewardAmount)
        }

        val startText = DateTimeParser.parseToString(startEpoch)
        val endText = DateTimeParser.parseToString(endEpoch)

        plugin.messageManager.sendOrQueue(
            event.creator,
            "<green>Dein Event <gold>${event.name}</gold> wurde angenommen! Start: <yellow>$startText</yellow>, Ende: <yellow>$endText</yellow></green>"
        )

        return Result.Success("<green>Event <gold>${event.name}</gold> wurde angenommen. Start: $startText, Ende: $endText. Belohnung: ${rewardAmount} Diamant(en).</green>")
    }

    fun rejectEvent(id: String): Result {
        val event = store.getEvent(id) ?: return Result.Error("<red>Unbekanntes Event!</red>")
        if (event.state != EventState.SUBMITTED) {
            return Result.Error("<red>Dieses Event wartet nicht auf eine Prüfung!</red>")
        }

        event.state = EventState.REJECTED
        store.save()

        plugin.messageManager.sendOrQueue(
            event.creator,
            "<red>Dein Event <gold>${event.name}</gold> wurde abgelehnt. Mit <yellow>/event resume</yellow> kannst du weiterbauen und es erneut einreichen.</red>"
        )

        return Result.Success("<yellow>Event <gold>${event.name}</gold> wurde abgelehnt.</yellow>")
    }

    // ------------------------------------------------------------------
    // Scheduling (start/end of the timeframe)
    // ------------------------------------------------------------------

    private var checkerTaskId: Int = -1

    fun startEventChecker() {
        stopEventChecker()
        checkerTaskId = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            checkEventTransitions()
        }, 20L, 20L).taskId // check once per real second
    }

    fun stopEventChecker() {
        if (checkerTaskId != -1) {
            plugin.server.scheduler.cancelTask(checkerTaskId)
            checkerTaskId = -1
        }
    }

    private fun checkEventTransitions() {
        val now = System.currentTimeMillis() / 1000
        for (event in store.getAll().toList()) {
            when (event.state) {
                EventState.ACCEPTED -> {
                    if (event.endTime <= now) {
                        endEvent(event.id)
                    } else if (event.startTime <= now) {
                        activateEvent(event.id)
                    }
                }
                EventState.ACTIVE -> {
                    if (event.endTime <= now) {
                        endEvent(event.id)
                    }
                }
                else -> {}
            }
        }
    }

    fun activateEvent(id: String) {
        val event = store.getEvent(id) ?: return
        if (event.state != EventState.ACCEPTED) return

        event.state = EventState.ACTIVE
        store.save()

        broadcast(mm("<gold><bold>Das Event <yellow>${event.name}</yellow> ist jetzt live!</bold></gold> <gray>Beitreten mit</gray> <yellow>/event join ${event.id}</yellow>"))
    }

    fun endEvent(id: String) {
        val event = store.getEvent(id) ?: return
        if (event.state == EventState.ENDED) return

        event.state = EventState.ENDED
        store.save()

        sessions.filter { it.value.eventId == id }.keys.toList().forEach { uuid ->
            val player = getPlayer(uuid)
            if (player != null) {
                leaveWorld(player)
            } else {
                sessions.remove(uuid)
            }
            plugin.messageManager.sendOrQueue(uuid, "<gray>Das Event <gold>${event.name}</gold> ist zu Ende.</gray>")
        }

        getWorld(event.worldName)?.let { unloadWorld(it, true) }
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

        val world = getWorld(event.worldName)
            ?: WorldCreator(event.worldName).createWorld()
            ?: return Result.Error("<red>Die Event-Welt konnte nicht geladen werden!</red>")

        states.saveState(player)
        sessions[player.uniqueId] = Session(event.id, SessionMode.PLAY)

        player.teleport(world.spawnLocation)
        player.gameMode = GameMode.SURVIVAL
        player.inventory.clear()
        player.enderChest.clear()

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
        getWorld(event.worldName)?.let { unloadWorld(it, false) }
        val folder = File(getWorldContainer(), event.worldName)
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
        startEventChecker()
        checkEventTransitions()
    }
}