package de.davidsw.diawars.managers

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import de.davidsw.diawars.Diawars
import de.davidsw.diawars.listeners.ImageListener
import de.davidsw.diawars.stores.ModRequest
import de.davidsw.diawars.util.BedrockUtil
import de.davidsw.diawars.util.ConfigFiles
import de.davidsw.diawars.util.MiniMessageHelper.escape
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player

/**
 * One whitelisted player-installable mod: [checkId] is the mod id the
 * client-info check tests against (Fabric/Forge id, e.g. "oculus"), while
 * [displayName] is the player-facing name shown in the whitelist dialog.
 */
data class AllowedMod(val checkId: String, val displayName: String)

/**
 * Mod whitelist: which client mods may join the server, shown to players as a
 * mod list and extensible through display-name requests.
 *
 * <p>Two parts in <code>mod_whitelist.yml</code> (libraries stay plain ids):
 * <ul>
 *   <li><b>allowed_mods</b> — mods players install themselves. Every entry
 *   carries a [check](ing) id and a display name; legacy plain-string entries
 *   get an auto-generated display name so old configs keep working.</li>
 *   <li><b>libraries</b> — dependencies shipped by the mods above, checked on
 *   their id only.</li>
 * </ul></p>
 *
 * <p>Players open the list in the client-mod dialog ([DIALOG_CHANNEL], JSON:
 * display names, the player's installed ones + request cap) via the main-menu
 * button or <code>/mods</code>; a request for a new mod ([REQUEST_CHANNEL],
 * JSON: name) only needs its display name, is de-duplicated against the
 * whitelist and open requests, then lands in the
 * [de.davidsw.diawars.stores.ModRequestStore] for admin review
 * (<code>/admin mods list|resolve</code>). Bedrock players have no client
 * mod for this: no menu button, the command answers with an error; players
 * with an outdated mod get the list in chat instead.</p>
 */
class ModWhitelistManager(private val plugin: Diawars) {
    companion object {
        /** Server -> client signal to open the whitelist dialog in the mod. */
        const val DIALOG_CHANNEL = "diawars:whitelist_dialog"

        /** Client -> server submit of a "please add this mod" request. */
        const val REQUEST_CHANNEL = "diawars:whitelist_request"

        /** Upper bound for the requested display name (dialog field + store). */
        const val MAX_REQUEST_LENGTH = 40

        // Fabric mod id uses a hyphen ("diawars-client"), Forge forbids hyphens
        // so the Forge port uses an underscore ("diawars_client"). Both must
        // always be allowed.
        private val defaultMods = listOf("diawars-client", "diawars_client", "java", "mixinextras")
    }

    private val whitelistFile = ConfigFiles.resolve(plugin, "mod_whitelist.yml")
    private var whitelist: Set<String> = emptySet()
    private var allowedMods: List<AllowedMod> = emptyList()
    private var allowedDisplayNames: Set<String> = emptySet()

    sealed interface Result {
        data class Success(val message: String) : Result
        data class Error(val message: String) : Result
    }

    init {
        loadFromConfig()
    }

    fun loadFromConfig() {
        val config = YamlConfiguration.loadConfiguration(whitelistFile)

        // allowed_mods entries are check/display mappings; plain strings are
        // the legacy format and get an auto display name.
        val allowed = mutableListOf<AllowedMod>()
        val rawAllowed = config.get("allowed_mods") as? List<*> ?: emptyList<Any>()
        for (raw in rawAllowed) {
            when (raw) {
                is Map<*, *> -> {
                    val check = raw["check"]?.toString()?.trim()
                    if (check.isNullOrEmpty()) {
                        plugin.logger.warning("Skipped allowed_mods entry without check id: $raw")
                        continue
                    }
                    val display = raw["display"]?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                        ?: prettyName(check)
                    allowed += AllowedMod(check, display)
                }

                is String -> allowed += AllowedMod(raw.trim(), prettyName(raw))

                else -> plugin.logger.warning("Skipped unexpected allowed_mods entry: $raw")
            }
        }
        // Sorted once at load so menu/dialog consumers never re-sort per call.
        allowedMods = allowed.sortedBy { it.displayName.lowercase() }
        allowedDisplayNames = allowedMods.map { it.displayName.lowercase() }.toSet()

        // "mods" is the legacy single-list format, still read for backwards
        // compatibility with existing server configs.
        // The check set only contains check ids (what clients report);
        // display names are purely cosmetic.
        whitelist = (allowedMods.map { it.checkId.lowercase() } +
                config.getStringList("libraries") +
                config.getStringList("mods")).map { it.lowercase() }.toSet()
        plugin.logger.info("Loaded ${allowedMods.size} allowed mod(s), ${whitelist.size} whitelisted mod(s).")
    }

    /** Derives a display name like "Apple Skin" from a mod id like "apple-skin". */
    private fun prettyName(id: String): String =
        id.split('_', '-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    /** Mods players install themselves, display-name sorted. */
    fun getAllowedMods(): List<AllowedMod> = allowedMods

    fun isAllowed(modId: String): Boolean = modId.lowercase() in whitelist || modId.lowercase() in defaultMods

    fun findDisallowedMods(modIds: Collection<String>): Collection<String> = modIds.filter { !isAllowed(it) }

    // ------------------------------------------------------------------
    // whitelist dialog (main-menu button / /mods)
    // ------------------------------------------------------------------

    /**
     * Opens the client-mod whitelist dialog. Bedrock players never see it;
     * Java players without the dialog channel (outdated mod, mod-less admin)
     * get the list in chat instead of opening the menu.
     */
    fun openDialog(player: Player) {
        if (BedrockUtil.isBedrock(player)) {
            player.sendMessage(mm("<red>Die Mod-Liste ist auf Bedrock nicht verfügbar.</red>"))
            return
        }
        if (DIALOG_CHANNEL !in player.listeningPluginChannels) {
            sendChatList(player)
            return
        }
        // Mods the player actually has loaded (reported via client_info), so
        // the dialog can highlight them in the list. Matched by check id.
        val activeModIds = plugin.store.clientInfoStore.get(player.uniqueId)
            ?.activeMods
            ?.map { it.id.lowercase() }
            ?.toSet()
            .orEmpty()
        val doc = JsonObject().apply {
            add("mods", JsonArray().apply { allowedMods.forEach { add(it.displayName) } })
            add("installed", JsonArray().apply {
                allowedMods.filter { it.checkId.lowercase() in activeModIds }
                    .forEach { add(it.displayName) }
            })
            addProperty("requestMax", MAX_REQUEST_LENGTH)
        }
        try {
            player.sendPluginMessage(
                plugin,
                DIALOG_CHANNEL,
                ImageListener.writeVarIntPrefixedUtf8(doc.toString()),
            )
        } catch (e: Exception) {
            plugin.logger.warning("Could not open whitelist dialog for ${player.name}: ${e.message}")
            sendChatList(player)
            return
        }
        // The dialog shows world + chat, not the menu inventory.
        player.closeInventory()
    }

    /** Chat listing for players whose mod cannot open the dialog. */
    private fun sendChatList(player: Player) {
        val lines = mutableListOf("<gold>=== Erlaubte Mods (${allowedMods.size}) ===</gold>")
        allowedMods.forEach { lines += "<gray>- <white>${escape(it.displayName)}</white></gray>" }
        lines += "<red>Dialog-Ansicht und Mod-Anfragen benötigen die aktuelle Diawars-Client-Mod.</red>"
        player.sendMessage(mm(lines.joinToString("\n")))
    }

    // ------------------------------------------------------------------
    // request submit (client -> server, runs separately from commands)
    // ------------------------------------------------------------------

    /**
     * Stores a player request to add a mod (display name only). Checked
     * server-side against the whitelist and open requests; admins are
     * notified and review with <code>/admin mods list|resolve</code>.
     */
    fun handleSubmit(player: Player, name: String?): Result {
        if (BedrockUtil.isBedrock(player)) {
            return Result.Error("<red>Mod-Anfragen sind auf Bedrock nicht möglich.</red>")
        }
        val modName = name?.trim()?.take(MAX_REQUEST_LENGTH)
        if (modName.isNullOrEmpty()) {
            return Result.Error("<red>Gib einen Namen für die Mod ein!</red>")
        }

        val store = plugin.store.modRequestStore
        if (isWhitelistedName(modName)) {
            return Result.Error("<red>Die Mod <yellow>${escape(modName)}</yellow> ist bereits auf der Whitelist!</red>")
        }
        if (store.hasOpenRequest(modName)) {
            return Result.Error("<red>Für <yellow>${escape(modName)}</yellow> gibt es bereits eine offene Anfrage!</red>")
        }

        val request = store.addRequest(player.uniqueId, player.name, modName)
        notifyAdmins(request)
        return Result.Success(
            "<green>✓ Anfrage für die Mod <gold>${escape(request.displayName)}</gold> " +
                    "abgeschickt! (#${request.id})</green>",
        )
    }

    /** True when [name] matches an allowed mod's display name or check id. */
    private fun isWhitelistedName(name: String): Boolean {
        val lowered = name.lowercase()
        return lowered in allowedDisplayNames || allowedMods.any { it.checkId.lowercase() == lowered }
    }

    private fun notifyAdmins(request: ModRequest) {
        plugin.server.onlinePlayers
            .filter { it.hasPermission("diawars.admin") }
            .forEach {
                it.sendMessage(mm(
                    "<yellow>⚠ <gold>${request.requesterName}</gold> fragt an, die Mod " +
                            "<gold>${escape(request.displayName)}</gold> zur Whitelist hinzuzufügen " +
                            "(ID: <white>${request.id}</white>). Nutze <white>/admin mods list</white>.</yellow>",
                ))
            }
    }

    // ------------------------------------------------------------------
    // admin review
    // ------------------------------------------------------------------

    fun formatRequestList(): List<String> {
        val openRequests = plugin.store.modRequestStore.getOpen()
        if (openRequests.isEmpty()) {
            return listOf("<gray>Es gibt derzeit keine offenen Mod-Anfragen.</gray>")
        }
        val lines = mutableListOf("<gold>=== Offene Mod-Anfragen (${openRequests.size}) ===</gold>")
        openRequests.forEach { request ->
            lines += "<gray>- <yellow>#${request.id}</yellow> <white>${escape(request.displayName)}</white> " +
                    "<gray>von</gray> <white>${escape(request.requesterName)}</white></gray>"
        }
        return lines
    }

    fun resolveRequest(id: String): Result {
        return if (plugin.store.modRequestStore.resolve(id)) {
            Result.Success("<green>✓ Mod-Anfrage <gold>#$id</gold> wurde als behandelt markiert.</green>")
        } else {
            Result.Error("<red>Diese Mod-Anfrage existiert nicht oder wurde bereits behandelt!</red>")
        }
    }

    /** Join notice for admins with open mod requests (mirrors reports/bugs). */
    fun handleAdminJoin(admin: Player) {
        if (!admin.hasPermission("diawars.admin")) return
        val store = plugin.store.modRequestStore
        if (!store.hasUnread(admin.uniqueId)) return
        val count = store.getOpen().size
        admin.sendMessage(
            mm("<yellow>⚠ Es gibt <gold>$count</gold> offene(n) Mod-Anfrage(n). " +
                    "Nutze <white>/admin mods list</white> um sie anzusehen.</yellow>"),
        )
    }
}
