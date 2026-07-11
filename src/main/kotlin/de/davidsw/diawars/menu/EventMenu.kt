package de.davidsw.diawars.menu

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.EventManager
import de.davidsw.diawars.stores.EventState
import de.davidsw.diawars.stores.GameEvent
import de.davidsw.diawars.util.DateTimeParser
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit.getOfflinePlayer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import java.util.UUID

class EventMenu(private val plugin: Diawars) {
    companion object {
        private const val SLOT_CREATE = 37
        private const val SLOT_RESUME = 38
        private const val SLOT_SUBMIT = 39
        private const val SLOT_CANCEL = 40
        private const val SLOT_LEAVE = 41
        private const val SLOT_STATUS = 43

        private val ACTIVE_SLOTS = listOf(10, 11, 12, 13, 14, 15, 16)
        private val UPCOMING_SLOTS = listOf(19, 20, 21, 22, 23, 24, 25)
        private val PENDING_SLOTS = listOf(28, 29, 30, 31, 32, 33, 34)

        private val ALL_SLOTS = listOf(SLOT_CREATE, SLOT_RESUME, SLOT_SUBMIT, SLOT_CANCEL, SLOT_LEAVE, SLOT_STATUS) +
                ACTIVE_SLOTS + PENDING_SLOTS + UPCOMING_SLOTS
    }

    // slot -> event, kept per-player so clicks in the periodically-refreshed
    // inventory still map to the right GameEvent
    private val activeEventsCache = mutableMapOf<UUID, List<GameEvent>>()
    private val upcomingEventsCache = mutableMapOf<UUID, List<GameEvent>>()
    private val pendingEventsCache = mutableMapOf<UUID, List<GameEvent>>()

    fun populateEventMenu(inv: Inventory, player: Player) {
        ALL_SLOTS.forEach { inv.setItem(it, null) }

        val session = plugin.eventManager.getSession(player.uniqueId)
        val ownEvents = plugin.store.eventStore.getByCreator(player.uniqueId)
        val building = ownEvents.firstOrNull { it.state == EventState.BUILDING }
        val submitted = ownEvents.firstOrNull { it.state == EventState.SUBMITTED }
        val isAdmin = player.hasPermission("diawars.admin")

        populateActions(inv, session, building, submitted)
        populateStatus(inv, session, building, submitted)
        populateActiveEvents(inv, player)
        populateUpcomingEvents(inv, player)
        if (isAdmin) populatePendingEvents(inv, player)
    }

    private fun populateActions(
        inv: Inventory,
        session: EventManager.Session?,
        building: GameEvent?,
        submitted: GameEvent?,
    ) {
        val canCreate = session == null && building == null && submitted == null
        inv.setItem(
            SLOT_CREATE, actionItem(
                enabled = canCreate,
                material = Material.WRITABLE_BOOK,
                name = "<green><bold>Event erstellen</bold></green>",
                lore = if (canCreate) listOf(
                    mm("<gray>Erstellt ein neues Event</gray>"),
                    mm(""),
                    mm("<yellow>Klicken um den Befehl vorzuschlagen</yellow>"),
                ) else listOf(mm("<dark_gray>Du hast bereits ein Event in Bearbeitung oder in einem Event</dark_gray>")),
            )
        )

        val canResume = session == null && building != null
        inv.setItem(
            SLOT_RESUME, actionItem(
                enabled = canResume,
                material = Material.BOOK,
                name = "<yellow><bold>Weiterbauen</bold></yellow>",
                lore = if (canResume) listOf(
                    mm("<gray>Event: </gray><gold>${building.name}</gold>"),
                    mm(""),
                    mm("<yellow>Klicken zum Fortsetzen</yellow>"),
                ) else listOf(mm("<dark_gray>Du hast kein Event in Bearbeitung</dark_gray>")),
            )
        )

        val canSubmit = session != null && session.mode == EventManager.SessionMode.BUILD
        inv.setItem(
            SLOT_SUBMIT, actionItem(
                enabled = canSubmit,
                material = Material.PAPER,
                name = "<aqua><bold>Event einreichen</bold></aqua>",
                lore = if (canSubmit) listOf(
                    mm("<gray>Reicht dein Event zur Prüfung ein</gray>"),
                    mm(""),
                    mm("<yellow>Klicken zum Einreichen</yellow>"),
                ) else listOf(mm("<dark_gray>Du baust gerade an keinem Event</dark_gray>")),
            )
        )

        val canCancel = session != null && session.mode == EventManager.SessionMode.BUILD
        inv.setItem(
            SLOT_CANCEL, actionItem(
                enabled = canCancel,
                material = Material.BARRIER,
                name = "<red><bold>Event abbrechen</bold></red>",
                lore = if (canCancel) listOf(
                    mm("<gray>Bricht dein Event ab und löscht es</gray>"),
                    mm(""),
                    mm("<yellow>Klicken zum Abbrechen</yellow>"),
                ) else listOf(mm("<dark_gray>Du baust gerade an keinem Event</dark_gray>")),
            )
        )

        val canLeave = session != null
        inv.setItem(
            SLOT_LEAVE, actionItem(
                enabled = canLeave,
                material = Material.OAK_DOOR,
                name = "<gold><bold>Event verlassen</bold></gold>",
                lore = if (canLeave) listOf(
                    mm("<gray>Verlässt die aktuelle Event-Welt</gray>"),
                    mm(""),
                    mm("<yellow>Klicken zum Verlassen</yellow>"),
                ) else listOf(mm("<dark_gray>Du befindest dich in keinem Event</dark_gray>")),
            )
        )
    }

    private fun populateStatus(
        inv: Inventory,
        session: EventManager.Session?,
        building: GameEvent?,
        submitted: GameEvent?,
    ) {
        val lore = mutableListOf(
            mm("<gray>Sitzung: </gray><white>${sessionLabel(session)}</white>"),
        )
        if (building != null) lore += mm("<gray>In Bearbeitung: </gray><gold>${building.name}</gold>")
        if (submitted != null) lore += mm("<gray>Zur Prüfung eingereicht: </gray><gold>${submitted.name}</gold>")

        inv.setItem(
            SLOT_STATUS, item(
                material = Material.NETHER_STAR,
                name = mm("<light_purple><bold>Dein Status</bold></light_purple>"),
                lore = lore,
            )
        )
    }

    private fun populateActiveEvents(inv: Inventory, player: Player) {
        val active = plugin.eventManager.listByState(EventState.ACTIVE)
        activeEventsCache[player.uniqueId] = active

        if (active.isEmpty()) {
            inv.setItem(
                ACTIVE_SLOTS[0], item(
                    material = Material.GRAY_DYE,
                    name = mm("<gray>Keine aktiven Events</gray>"),
                )
            )
            return
        }

        val shown = active.take(if (active.size > ACTIVE_SLOTS.size) ACTIVE_SLOTS.size - 1 else ACTIVE_SLOTS.size)
        shown.forEachIndexed { index, event ->
            inv.setItem(
                ACTIVE_SLOTS[index], item(
                    material = Material.LIME_CONCRETE,
                    name = mm("<green><bold>${event.name}</bold></green>"),
                    lore = listOf(
                        mm("<gray>ID: </gray><white>${event.id}</white>"),
                        mm(""),
                        mm("<yellow>Klicken zum Beitreten</yellow>"),
                    ),
                )
            )
        }
        if (active.size > ACTIVE_SLOTS.size) {
            val rest = active.size - shown.size
            inv.setItem(
                ACTIVE_SLOTS.last(), item(
                    material = Material.PAPER,
                    name = mm("<gray>+$rest weitere Events</gray>"),
                    lore = listOf(mm("<dark_gray>Nutze /event list active für die volle Liste</dark_gray>")),
                )
            )
        }
    }

    private fun populateUpcomingEvents(inv: Inventory, player: Player) {
        val upcoming = plugin.eventManager.listByState(EventState.ACCEPTED)
            .sortedBy { it.startTime }
        upcomingEventsCache[player.uniqueId] = upcoming

        if (upcoming.isEmpty()) {
            inv.setItem(UPCOMING_SLOTS[0], item(
                material = Material.GRAY_DYE,
                name = mm("<gray>Keine bevorstehenden Events</gray>"),
            ))
            return
        }

        val shown = upcoming.take(if (upcoming.size > UPCOMING_SLOTS.size) UPCOMING_SLOTS.size - 1 else UPCOMING_SLOTS.size)
        shown.forEachIndexed { index, event ->
            val startText = DateTimeParser.parseToString(event.startTime) ?: "Unbekannt"
            val endText = DateTimeParser.parseToString(event.endTime) ?: "Unbekannt"
            inv.setItem(UPCOMING_SLOTS[index], item(
                material = Material.CLOCK,
                name = mm("<aqua><bold>${event.name}</bold></aqua>"),
                lore = listOf(
                    mm("<gray>ID: </gray><white>${event.id}</white>"),
                    mm("<gray>Start: </gray><yellow>$startText</yellow>"),
                    mm("<gray>Ende: </gray><yellow>$endText</yellow>"),
                    mm(""),
                    mm("<dark_gray>Noch nicht beitretbar</dark_gray>"),
                ),
            ))
        }
        if (upcoming.size > UPCOMING_SLOTS.size) {
            val rest = upcoming.size - shown.size
            inv.setItem(UPCOMING_SLOTS.last(), item(
                material = Material.PAPER,
                name = mm("<gray>+$rest weitere Events</gray>"),
                lore = listOf(mm("<dark_gray>Nutze /event list accepted für die volle Liste</dark_gray>")),
            ))
        }
    }

    private fun populatePendingEvents(inv: Inventory, player: Player) {
        val pending = plugin.eventManager.listByState(EventState.SUBMITTED)
        pendingEventsCache[player.uniqueId] = pending

        if (pending.isEmpty()) {
            inv.setItem(
                PENDING_SLOTS[0], item(
                    material = Material.GRAY_DYE,
                    name = mm("<gray>Keine Events zur Prüfung</gray>"),
                )
            )
            return
        }

        val shown = pending.take(if (pending.size > PENDING_SLOTS.size) PENDING_SLOTS.size - 1 else PENDING_SLOTS.size)
        shown.forEachIndexed { index, event ->
            val creatorName = getOfflinePlayer(event.creator).name ?: "Unbekannt"
            inv.setItem(
                PENDING_SLOTS[index], item(
                    material = Material.WRITTEN_BOOK,
                    name = mm("<yellow><bold>${event.name}</bold></yellow>"),
                    lore = listOf(
                        mm("<gray>ID: </gray><white>${event.id}</white>"),
                        mm("<gray>Ersteller: </gray><white>$creatorName</white>"),
                        mm(""),
                        mm("<yellow>Klicken zum Prüfen</yellow>"),
                        mm("<dark_gray>Annehmen/Ablehnen: /event accept|reject ${event.id}</dark_gray>"),
                    ),
                )
            )
        }
        if (pending.size > PENDING_SLOTS.size) {
            val rest = pending.size - shown.size
            inv.setItem(
                PENDING_SLOTS.last(), item(
                    material = Material.PAPER,
                    name = mm("<gray>+$rest weitere Events</gray>"),
                    lore = listOf(mm("<dark_gray>Nutze /event list pending für die volle Liste</dark_gray>")),
                )
            )
        }
    }

    fun handleEventClick(player: Player, slot: Int, inv: Inventory) {
        when (slot) {
            SLOT_CREATE -> {
                val ownEvents = plugin.store.eventStore.getByCreator(player.uniqueId)
                val blocked = plugin.eventManager.getSession(player.uniqueId) != null ||
                        ownEvents.any { it.state == EventState.BUILDING || it.state == EventState.SUBMITTED }
                if (blocked) return
                player.closeInventory()
                player.sendMessage(
                    mm(
                        "<yellow>Bitte gib den Namen deines Events ein:</yellow> " +
                                "<click:suggest_command:'/event create '><gold>[Event erstellen]</gold></click>"
                    )
                )
            }

            SLOT_RESUME -> {
                if (plugin.eventManager.getSession(player.uniqueId) != null) return
                player.closeInventory()
                respond(player, plugin.eventManager.resumeBuilding(player))
            }

            SLOT_SUBMIT -> {
                player.closeInventory()
                respond(player, plugin.eventManager.submitEvent(player))
            }

            SLOT_CANCEL -> {
                player.closeInventory()
                respond(player, plugin.eventManager.cancelEvent(player))
            }

            SLOT_LEAVE -> {
                player.closeInventory()
                respond(player, plugin.eventManager.leaveEvent(player))
            }

            in ACTIVE_SLOTS -> {
                val active = activeEventsCache[player.uniqueId] ?: return
                val index = ACTIVE_SLOTS.indexOf(slot)
                val event = active.getOrNull(index) ?: return
                player.closeInventory()
                respond(player, plugin.eventManager.joinEvent(player, event.id))
            }

            in UPCOMING_SLOTS -> {
                val upcoming = upcomingEventsCache[player.uniqueId] ?: return
                val index = UPCOMING_SLOTS.indexOf(slot)
                val event = upcoming.getOrNull(index) ?: return
                val startText = DateTimeParser.parseToString(event.startTime) ?: "Unbekannt"
                val endText = DateTimeParser.parseToString(event.endTime) ?: "Unbekannt"
                player.sendMessage(mm(
                    "<gold>${event.name}</gold> <gray>startet am</gray> <yellow>$startText</yellow> " +
                            "<gray>und endet am</gray> <yellow>$endText</yellow>"
                ))
            }

            in PENDING_SLOTS -> {
                if (!player.hasPermission("diawars.admin")) return
                val pending = pendingEventsCache[player.uniqueId] ?: return
                val index = PENDING_SLOTS.indexOf(slot)
                val event = pending.getOrNull(index) ?: return
                player.closeInventory()
                respond(player, plugin.eventManager.reviewEvent(player, event.id))
            }
        }
    }

    private fun actionItem(
        enabled: Boolean,
        material: Material,
        name: String,
        lore: List<net.kyori.adventure.text.Component>,
    ) = item(
        material = if (enabled) material else Material.GRAY_DYE,
        name = if (enabled) mm(name) else mm("<dark_gray><bold>Nicht verfügbar</bold></dark_gray>"),
        lore = lore,
        glow = false,
    )

    private fun sessionLabel(session: EventManager.Session?): String = when (session?.mode) {
        EventManager.SessionMode.BUILD -> "Du baust an einem Event"
        EventManager.SessionMode.REVIEW -> "Du prüfst ein Event"
        EventManager.SessionMode.PLAY -> "Du nimmst an einem Event teil"
        null -> "Kein aktives Event"
    }

    private fun respond(player: Player, result: EventManager.Result) {
        when (result) {
            is EventManager.Result.Success -> player.sendMessage(mm(result.message))
            is EventManager.Result.Error -> player.sendMessage(mm(result.message))
        }
    }
}