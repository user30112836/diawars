package de.davidsw.diawars.managers

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import de.davidsw.diawars.Diawars
import de.davidsw.diawars.listeners.AdminModListener
import de.davidsw.diawars.listeners.ImageListener
import de.davidsw.diawars.stores.EventState
import de.davidsw.diawars.util.DateTimeParser
import de.davidsw.diawars.util.MiniMessageHelper.mm
import de.davidsw.diawars.util.PlayerNameCache
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Bridges the {@code /admin} features into the Diawars Admin client mod.
 *
 * <p>The admin mod announces itself with {@code diawars:admin_client_info}
 * (see [AdminModListener]); only admins may do that — non-admins are kicked
 * and online admins are notified. The GUI itself is pulled data:
 * {@code /admin} without arguments sends {@code diawars:admin_open} and the
 * mod then fetches the lists it shows via {@code diawars:admin_fetch}
 * (sections: overview, bugs, reports, report, mods, events, images,
 * images_player, log, players, eventids). The answers go out as
 * {@code diawars:admin_data} JSON. Actions are <b>not</b> a separate channel:
 * the mod runs the regular {@code /admin ...} commands itself, so permission
 * checks, validation, logging and chat feedback stay server-side in one
 * place ([AdminCommand]).</p>
 */
class AdminGuiManager(private val plugin: Diawars) {

    companion object {
        // No shared state yet; reserved for future GUI constants.
    }

    /** Players whose client announced the admin mod (validated admin state). */
    private val adminModUsers = mutableSetOf<UUID>()

    // ------------------------------------------------------------------
    // admin mod tracking
    // ------------------------------------------------------------------

    fun markAdminModUser(id: UUID) = synchronized(adminModUsers) { adminModUsers.add(id) }

    fun clearAdminModUser(id: UUID) = synchronized(adminModUsers) { adminModUsers.remove(id) }

    fun hasAdminMod(id: UUID): Boolean = synchronized(adminModUsers) { id in adminModUsers }

    /** GUI may open only for admins whose client announced the admin mod. */
    fun canOpenGui(player: Player): Boolean =
        player.hasPermission("diawars.admin") &&
            hasAdminMod(player.uniqueId) &&
            AdminModListener.OPEN_CHANNEL in player.listeningPluginChannels

    /**
     * Opens the admin GUI in the client mod. Returns false when the player is
     * not an admin-mod admin (the command then keeps its old help output).
     */
    fun openGui(player: Player): Boolean {
        if (!canOpenGui(player)) return false
        return try {
            player.sendPluginMessage(plugin, AdminModListener.OPEN_CHANNEL, ImageListener.writeVarIntPrefixedUtf8("{}"))
            true
        } catch (e: Exception) {
            plugin.logger.warning("Could not open the admin GUI for ${player.name}: ${e.message}")
            false
        }
    }

    // ------------------------------------------------------------------
    // admin mod join validation (kick non-admins, notify admins)
    // ------------------------------------------------------------------

    /**
     * Validates an admin-mod announcement: admins are registered, everyone
     * else is kicked and the online admins are notified.
     */
    fun handleAdminModUser(player: Player) {
        if (player.hasPermission("diawars.admin")) {
            markAdminModUser(player.uniqueId)
            return
        }

        val name = player.name
        plugin.logger.warning("Kicked $name: admin mod is only allowed for diawars.admin holders.")
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (player.isOnline) {
                player.kick(
                    mm(
                        "<red>Die Diawars Admin-Mod ist nur für Server-Admins! " +
                                "Bitte entferne sie und verbinde dich erneut.</red>",
                    ),
                )
            }
            plugin.server.onlinePlayers
                .filter { it.uniqueId != player.uniqueId && it.hasPermission("diawars.admin") }
                .forEach {
                    it.sendMessage(
                        mm(
                            "<yellow>⚠ <gold>$name</gold> wurde gekickt: die Diawars Admin-Mod " +
                                    "darf nur von Server-Admins verwendet werden.</yellow>",
                        ),
                    )
                }
        })
    }

    // ------------------------------------------------------------------
    // data fetches (admin mod GUI pulls)
    // ------------------------------------------------------------------

    /**
     * Answers one {@code diawars:admin_fetch} (JSON {@code {section, filter}})
     * with a {@code diawars:admin_data} answer. The section build runs on the
     * main thread (store/YAML state is main-thread data); the send itself is
     * safe from there as well.
     */
    fun handleFetch(player: Player, section: String, filter: String) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!player.isOnline || !player.hasPermission("diawars.admin")) return@Runnable
            if (!hasAdminMod(player.uniqueId)) return@Runnable

            val data = try {
                buildSection(section, filter, player)
            } catch (e: Exception) {
                plugin.logger.warning("Admin GUI data for section '$section' failed: ${e.message}")
                null
            }
            if (data == null) return@Runnable
            sendData(player, section, filter, data)
        })
    }

    private fun sendData(player: Player, section: String, filter: String, data: JsonObject) {
        val answer = JsonObject().apply {
            addProperty("section", section)
            addProperty("filter", filter)
            add("data", data)
        }
        try {
            player.sendPluginMessage(
                plugin,
                AdminModListener.DATA_CHANNEL,
                ImageListener.writeVarIntPrefixedUtf8(answer.toString()),
            )
        } catch (e: Exception) {
            plugin.logger.warning("Could not send admin GUI data '$section' to ${player.name}: ${e.message}")
        }
    }

    private fun buildSection(section: String, filter: String, admin: Player): JsonObject? = when (section) {
        "overview" -> buildOverview()
        "bugs" -> buildBugs(admin)
        "reports" -> buildReports(admin)
        "report" -> buildReport(filter)
        "mods" -> buildMods(admin)
        "events" -> buildEvents(filter)
        "images" -> buildImageOverview()
        "images_player" -> buildImagesPlayer(filter)
        "log" -> buildLog(filter)
        "players" -> buildPlayers()
        "pvp" -> buildPvp()
        "eventids" -> buildEventIds()
        else -> null
    }

    // ------------------------------------------------------------------
    // section builders
    // ------------------------------------------------------------------

    private fun buildOverview(): JsonObject = JsonObject().apply {
        addProperty("bugs", plugin.store.bugStore.getUnresolved().size)
        addProperty("reports", plugin.store.reportStore.getOpen().size)
        addProperty("mods", plugin.store.modRequestStore.getOpen().size)
        addProperty("pendingEvents", plugin.eventManager.listByState(EventState.SUBMITTED).size)
        addProperty("onlinePlayers", plugin.server.onlinePlayers.size)
        addProperty("lobbyLocked", plugin.lobbyManager.locked)
        addProperty("lobbyPlayers", plugin.lobbyManager.lobbyPlayerCount())
        addProperty("nightVision", plugin.config.getBoolean("night-vision-toggle", true))
    }

    private fun buildBugs(admin: Player): JsonObject {
        // Same read-mark behavior as /admin bug list: viewed lists stay read.
        plugin.store.bugStore.markRead(admin.uniqueId)
        val items = JsonArray()
        plugin.store.bugStore.getUnresolved().forEach { bug ->
            items.add(JsonObject().apply {
                addProperty("id", bug.id)
                addProperty("reporter", PlayerNameCache.nameOf(bug.reporter))
                addProperty("time", DateTimeParser.parseToString(bug.reportedAt))
                addProperty("text", bug.description)
            })
        }
        return JsonObject().apply { add("items", items) }
    }

    private fun buildReports(admin: Player): JsonObject {
        // Same read-mark behavior as /admin report list.
        plugin.store.reportStore.markRead(admin.uniqueId)
        val items = JsonArray()
        plugin.store.reportStore.getOpen().forEach { report ->
            items.add(JsonObject().apply {
                addProperty("id", report.id)
                addProperty("reporter", PlayerNameCache.nameOf(report.reporter))
                addProperty("source", report.source.name)
                addProperty("player", report.targetName)
                addProperty("imageOwner", report.targetOwner?.let { PlayerNameCache.nameOf(it) })
                addProperty("image", report.targetFile)
                addProperty("reason", report.reason)
                addProperty("shot", report.shot)
                addProperty("time", DateTimeParser.parseToString(report.reportedAt))
            })
        }
        return JsonObject().apply { add("items", items) }
    }

    private fun buildReport(id: String): JsonObject {
        val report = plugin.store.reportStore.get(id)
            ?: return JsonObject().apply {
                addProperty("error", "Dieser Report existiert nicht!")
            }
        return JsonObject().apply {
            addProperty("id", report.id)
            addProperty("reporter", PlayerNameCache.nameOf(report.reporter))
            addProperty("time", DateTimeParser.parseToString(report.reportedAt))
            addProperty("source", report.source.name)
            addProperty("player", report.targetName)
            addProperty("imageOwner", report.targetOwner?.let { PlayerNameCache.nameOf(it) })
            // Raw owner UUID for the admin mod's image viewer pull.
            addProperty("imageOwnerUuid", report.targetOwner?.toString())
            addProperty("image", report.targetFile)
            addProperty("reason", report.reason)
            addProperty("shot", report.shot)
            addProperty("resolved", report.resolved)
        }
    }

    private fun buildMods(admin: Player): JsonObject {
        // Same read-mark behavior as /admin mods list.
        plugin.store.modRequestStore.markRead(admin.uniqueId)
        val items = JsonArray()
        plugin.store.modRequestStore.getOpen().forEach { request ->
            items.add(JsonObject().apply {
                addProperty("id", request.id)
                addProperty("requester", request.requesterName)
                addProperty("mod", request.displayName)
                addProperty("time", DateTimeParser.parseToString(request.requestedAt))
            })
        }
        return JsonObject().apply { add("items", items) }
    }

    private fun buildEvents(stateFilter: String): JsonObject {
        val filter = stateFilter.lowercase()
        val items = JsonArray()
        if (filter == "past") {
            // Past events: ENDED (plus legacy REJECTED entries), newest first.
            plugin.eventManager.listPastEvents().forEach { event ->
                items.add(JsonObject().apply {
                    addProperty("id", event.id)
                    addProperty("name", event.name)
                    addProperty("creator", PlayerNameCache.nameOf(event.creator))
                    addProperty("start", if (event.startTime > 0) DateTimeParser.parseToString(event.startTime) else "")
                    addProperty("end", if (event.endTime > 0) DateTimeParser.parseToString(event.endTime) else "")
                })
            }
        } else {
            val state = when (filter) {
                "pending" -> EventState.SUBMITTED
                "accepted" -> EventState.ACCEPTED
                else -> EventState.ACTIVE
            }
            plugin.eventManager.listByState(state).forEach { event ->
                items.add(JsonObject().apply {
                    addProperty("id", event.id)
                    addProperty("name", event.name)
                    addProperty("creator", PlayerNameCache.nameOf(event.creator))
                    addProperty("start", if (event.startTime > 0) DateTimeParser.parseToString(event.startTime) else "")
                    addProperty("end", if (event.endTime > 0) DateTimeParser.parseToString(event.endTime) else "")
                })
            }
        }
        return JsonObject().apply { add("items", items) }
    }

    private fun buildImageOverview(): JsonObject {
        val items = JsonArray()
        // Same filter as /admin image list: players with images, a zero limit
        // or a ban are shown (admins want to see those).
        plugin.imageManager.getAllKnownPlayers()
            .map { uuid -> Triple(uuid, plugin.imageManager.getImageCount(uuid), plugin.store.imageLimitStore.getLimit(uuid)) }
            .filter { (uuid, count, limit) -> count > 0 || limit == 0 || plugin.store.imageBanStore.isBanned(uuid) }
            .sortedBy { (uuid, _, _) -> plugin.server.getOfflinePlayer(uuid).name?.lowercase() ?: uuid.toString() }
            .forEach { (uuid, count, limit) ->
                items.add(JsonObject().apply {
                    addProperty("uuid", uuid.toString())
                    addProperty("name", plugin.server.getOfflinePlayer(uuid).name ?: uuid.toString())
                    addProperty("count", count)
                    addProperty("limit", limit)
                    addProperty("banned", plugin.store.imageBanStore.isBanned(uuid))
                })
            }
        return JsonObject().apply { add("items", items) }
    }

    private fun buildImagesPlayer(uuidFilter: String): JsonObject {
        val uuid = try {
            UUID.fromString(uuidFilter)
        } catch (_: IllegalArgumentException) {
            return JsonObject().apply { addProperty("error", "Unbekannter Spieler!") }
        }
        val offline = plugin.server.getOfflinePlayer(uuid)
        return JsonObject().apply {
            addProperty("uuid", uuid.toString())
            addProperty("name", offline.name ?: "?")
            addProperty("limit", plugin.store.imageLimitStore.getLimit(uuid))
            addProperty("banned", plugin.store.imageBanStore.isBanned(uuid))
            val files = JsonArray()
            plugin.imageManager.listImages(uuid).forEach { file -> files.add(file) }
            add("items", files)
        }
    }

    private fun buildLog(limitFilter: String): JsonObject {
        val limit = limitFilter.toIntOrNull()?.coerceIn(1, 100) ?: 15
        return JsonObject().apply {
            addProperty("limit", limit)
            val entries = JsonArray()
            plugin.diamondLogManager.getRecentEntries(limit).forEach { entry -> entries.add(entry) }
            add("items", entries)
        }
    }

    private fun buildPlayers(): JsonObject {
        val items = JsonArray()
        plugin.server.onlinePlayers
            .sortedBy { it.name.lowercase() }
            .forEach { player ->
                items.add(JsonObject().apply {
                    addProperty("name", player.name)
                    addProperty("uuid", player.uniqueId.toString())
                })
            }
        return JsonObject().apply { add("items", items) }
    }

    /** Online players with their current PvP state (PvP-Verwaltung list). */
    private fun buildPvp(): JsonObject {
        val items = JsonArray()
        plugin.server.onlinePlayers
            .sortedBy { it.name.lowercase() }
            .forEach { player ->
                items.add(JsonObject().apply {
                    addProperty("name", player.name)
                    addProperty("uuid", player.uniqueId.toString())
                    addProperty("pvp", plugin.store.pvpStatusStore.isPvPEnabled(player.uniqueId))
                })
            }
        return JsonObject().apply { add("items", items) }
    }

    private fun buildEventIds(): JsonObject {
        val items = JsonArray()
        plugin.store.eventStore.getAll()
            .sortedBy { it.id }
            .forEach { event ->
                items.add(JsonObject().apply {
                    addProperty("id", event.id)
                    addProperty("name", event.name)
                    addProperty("state", event.state.name)
                })
            }
        return JsonObject().apply { add("items", items) }
    }
}
