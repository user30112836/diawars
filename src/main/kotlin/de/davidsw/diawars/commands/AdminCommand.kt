package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.BugManager
import de.davidsw.diawars.managers.EventManager
import de.davidsw.diawars.managers.Team
import de.davidsw.diawars.stores.EventState
import de.davidsw.diawars.util.DateTimeParser
import de.davidsw.diawars.util.MiniMessageHelper.escape
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class AdminCommand(private val plugin: Diawars) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("diawars.admin")) {
            sender.sendMessage(mm("<red>Du hast keine Berechtigung für diesen Befehl!</red>"))
            return true
        }

        if (args.isEmpty()) {
            // Admin-mod admins get the client mod GUI here; everyone else keeps
            // the chat help (also the fallback for outdated/missing admin mod).
            if (sender is Player && plugin.adminGuiManager.openGui(sender)) {
                return true
            }
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> handleReload(sender)
            "inv" -> handleInv(sender, args.drop(1))
            "log" -> handleLog(sender, args.drop(1))
            "bug" -> handleBug(sender, args.drop(1))
            "report" -> handleReport(sender, args.drop(1))
            "mods" -> handleMods(sender, args.drop(1))
            "event" -> handleEvent(sender, args.drop(1))
            "pvp" -> handlePvp(sender, args.drop(1))
            "lobby" -> handleLobby(sender, args.drop(1))
            "msg" -> handleMsg(sender, args.drop(1))
            "image" -> handleImage(sender, args.drop(1))
            "team" -> handleTeam(sender, args.drop(1))
            "vault" -> handleVaultConfig(sender, args.drop(1))
            "config" -> handleConfig(sender, args.drop(1))
            else -> sendHelp(sender)
        }

        return true
    }

    // ------------------------------------------------------------------
    // reload
    // ------------------------------------------------------------------

    /**
     * Non-blocking player resolution: online players first, then the local
     * UserCache. Never triggers a (blocking) Mojang profile lookup like the
     * deprecated getOfflinePlayer(String) does. Returns null for unknown names.
     */
    private fun resolveKnownPlayer(name: String): OfflinePlayer? {
        Bukkit.getPlayerExact(name)?.let { return it }
        val cached = Bukkit.getOfflinePlayerIfCached(name) ?: return null
        if (!cached.hasPlayedBefore() && !cached.isOnline) return null
        return cached
    }

    private fun handleReload(sender: CommandSender) {
        try {
            plugin.reloadPluginConfigs()
            sender.sendMessage(mm("<green>✓ Konfiguration erfolgreich neu geladen!</green>"))
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload configs: ${e.message}")
            sender.sendMessage(mm("<red>Fehler beim Neuladen der Konfiguration! Siehe Konsole für Details.</red>"))
        }
    }

    // ------------------------------------------------------------------
    // inv (moved from /admininv)
    // ------------------------------------------------------------------

    private fun handleInv(sender: CommandSender, args: List<String>) {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return
        }
        if (args.size < 2) {
            sendInvHelp(sender)
            return
        }

        val target = resolveKnownPlayer(args[1])
        if (target == null) {
            sender.sendMessage(mm("<red>Dieser Spieler ist unbekannt!</red>"))
            return
        }

        when (args[0].lowercase()) {
            "main" -> plugin.inventoryInspectManager.openMainInventory(sender, target)

            "event" -> {
                val eventId = args.getOrNull(2)
                if (eventId == null) {
                    sender.sendMessage(mm("<red>Verwendung: /admin inv event &lt;spieler&gt; &lt;event-id&gt;</red>"))
                    return
                }
                plugin.inventoryInspectManager.openEventInventory(sender, target, eventId)
            }

            "enderchest" -> plugin.inventoryInspectManager.openEnderChest(sender, target, args.getOrNull(2))

            else -> sendInvHelp(sender)
        }
    }

    private fun sendInvHelp(player: Player) {
        player.sendMessage(
            mm(
                """
                <gold>=== Inventar-Inspektion ===</gold>
                <yellow>/admin inv main &lt;spieler&gt;</yellow><gray> - Hauptinventar (Normalwelt)</gray>
                <yellow>/admin inv event &lt;spieler&gt; &lt;event-id&gt;</yellow><gray> - Event-Inventar</gray>
                <yellow>/admin inv enderchest &lt;spieler&gt; [event-id]</yellow><gray> - Enderkiste</gray>
                """.trimIndent(),
            ),
        )
    }

    // ------------------------------------------------------------------
    // log (moved from /log)
    // ------------------------------------------------------------------

    private fun handleLog(sender: CommandSender, args: List<String>) {
        val limit = args.getOrNull(0)?.toIntOrNull()?.coerceIn(1, 100) ?: 15
        val entries = plugin.diamondLogManager.getRecentEntries(limit)

        if (entries.isEmpty()) {
            sender.sendMessage(mm("<gray>Es gibt heute noch keine Log-Einträge.</gray>"))
            return
        }

        val lines = mutableListOf("<gold>=== Diamond-Log (letzte ${entries.size}) ===</gold>")
        entries.forEach { lines += "<gray>$it</gray>" }
        sender.sendMessage(mm(lines.joinToString("\n")))
    }

    // ------------------------------------------------------------------
    // bug (moved from /bug list|resolve; /bug report stays player command)
    // ------------------------------------------------------------------

    private fun handleBug(sender: CommandSender, args: List<String>) {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return
        }

        when (args.getOrNull(0)?.lowercase()) {
            "list" -> {
                plugin.store.bugStore.markRead(sender.uniqueId)
                sender.sendMessage(mm(plugin.bugManager.formatList().joinToString("\n")))
            }

            "resolve", "fix" -> {
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /admin bug resolve &lt;id&gt;</red>"))
                    return
                }
                respondBug(sender, plugin.bugManager.resolveBug(args[1]))
            }

            else -> sendBugHelp(sender)
        }
    }

    private fun respondBug(player: Player, result: BugManager.Result) {
        when (result) {
            is BugManager.Result.Success -> player.sendMessage(mm(result.message))
            is BugManager.Result.Error -> player.sendMessage(mm(result.message))
        }
    }

    private fun sendBugHelp(player: Player) {
        player.sendMessage(
            mm(
                """
                <gold>=== Bug-Administration ===</gold>
                <yellow>/admin bug list</yellow><gray> - Offene Bugs auflisten</gray>
                <yellow>/admin bug resolve &lt;id&gt;</yellow><gray> - Bug als behoben markieren</gray>
                """.trimIndent(),
            ),
        )
    }

    // ------------------------------------------------------------------
    // report (player reports incl. screenshot attachments)
    // ------------------------------------------------------------------

    private fun handleReport(sender: CommandSender, args: List<String>) {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return
        }

        when (args.getOrNull(0)?.lowercase()) {
            "list" -> {
                plugin.store.reportStore.markRead(sender.uniqueId)
                sender.sendMessage(mm(plugin.reportManager.formatList().joinToString("\n")))
            }

            "view" -> {
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /admin report view &lt;id&gt;</red>"))
                    return
                }
                respondReport(sender, plugin.reportManager.formatView(args[1]))
            }

            "shot" -> {
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /admin report shot &lt;id&gt;</red>"))
                    return
                }
                respondReport(sender, plugin.reportManager.giveShotMap(sender, args[1]))
            }

            "resolve", "close" -> {
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /admin report resolve &lt;id&gt;</red>"))
                    return
                }
                respondReport(sender, plugin.reportManager.resolve(args[1]))
            }

            else -> sendReportHelp(sender)
        }
    }

    private fun respondReport(player: Player, result: de.davidsw.diawars.managers.ReportManager.Result) {
        when (result) {
            is de.davidsw.diawars.managers.ReportManager.Result.Success -> player.sendMessage(mm(result.message))
            is de.davidsw.diawars.managers.ReportManager.Result.Error -> player.sendMessage(mm(result.message))
        }
    }

    private fun sendReportHelp(player: Player) {
        player.sendMessage(
            mm(
                """
                <gold>=== Report-Administration ===</gold>
                <yellow>/admin report list</yellow><gray> - Offene Reports auflisten</gray>
                <yellow>/admin report view &lt;id&gt;</yellow><gray> - Report-Details ansehen</gray>
                <yellow>/admin report shot &lt;id&gt;</yellow><gray> - Screenshot-Karte erhalten</gray>
                <yellow>/admin report resolve &lt;id&gt;</yellow><gray> - Report als behandelt markieren</gray>
                """.trimIndent(),
            ),
        )
    }

    // ------------------------------------------------------------------
    // mods (whitelist requests from the client-mod dialog)
    // ------------------------------------------------------------------

    private fun handleMods(sender: CommandSender, args: List<String>) {
        when (args.getOrNull(0)?.lowercase()) {
            "list" -> {
                if (sender is Player) plugin.store.modRequestStore.markRead(sender.uniqueId)
                sender.sendMessage(mm(plugin.modWhitelistManager.formatRequestList().joinToString("\n")))
            }

            "resolve", "close" -> {
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /admin mods resolve &lt;id&gt;</red>"))
                    return
                }
                respondMods(sender, plugin.modWhitelistManager.resolveRequest(args[1]))
            }

            "whitelist" -> handleModWhitelist(sender, args.drop(1))

            else -> sendModsHelp(sender)
        }
    }

    private fun respondMods(sender: CommandSender, result: de.davidsw.diawars.managers.ModWhitelistManager.Result) {
        when (result) {
            is de.davidsw.diawars.managers.ModWhitelistManager.Result.Success -> sender.sendMessage(mm(result.message))
            is de.davidsw.diawars.managers.ModWhitelistManager.Result.Error -> sender.sendMessage(mm(result.message))
        }
    }

    private fun sendModsHelp(sender: CommandSender) {
        sender.sendMessage(
            mm(
                """
                <gold>=== Mod-Anfrage-Administration ===</gold>
                <yellow>/admin mods list</yellow><gray> - Offene Mod-Anfragen auflisten</gray>
                <yellow>/admin mods resolve &lt;id&gt;</yellow><gray> - Anfrage als behandelt markieren</gray>
                """.trimIndent(),
            ),
        )
    }

    // ------------------------------------------------------------------
    // event (admin parts moved from /event; player parts stay in /event)
    // ------------------------------------------------------------------

    private fun handleEvent(sender: CommandSender, args: List<String>) {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return
        }

        if (args.isEmpty()) {
            sendEventHelp(sender)
            return
        }

        when (args[0].lowercase()) {
            "review" -> {
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /admin event review &lt;id&gt;</red>"))
                    return
                }
                respondEvent(sender, plugin.eventManager.reviewEvent(sender, args[1]))
            }

            "accept" -> {
                if (args.size < 4) {
                    sender.sendMessage(mm("<red>Verwendung: /admin event accept &lt;id&gt; &lt;start&gt; &lt;ende&gt;</red>"))
                    return
                }
                val startEpoch = DateTimeParser.parseToEpochSeconds(args[2])
                val endEpoch = DateTimeParser.parseToEpochSeconds(args[3])
                if (startEpoch == null || endEpoch == null) {
                    sender.sendMessage(mm("<red>Ungültiges Datum/Uhrzeit! Format: ${DateTimeParser.FORMAT_HINT} (z.B. 10.07.2026-18:00)</red>"))
                    return
                }
                respondEvent(sender, plugin.eventManager.acceptEvent(args[1], startEpoch, endEpoch))
            }

            "reject" -> {
                if (args.size < 3) {
                    sender.sendMessage(mm("<red>Verwendung: /admin event reject &lt;id&gt; &lt;grund&gt;</red>"))
                    return
                }
                respondEvent(sender, plugin.eventManager.rejectEvent(args[1], args.drop(2).joinToString(" ")))
            }

            "set" -> {
                if (args.size < 3) {
                    sender.sendMessage(mm("<red>Verwendung: /admin event set &lt;id&gt; &lt;building|accepted&gt; [start] [ende]</red>"))
                    return
                }
                when (args[2].lowercase()) {
                    "building" -> respondEvent(sender, plugin.eventManager.adminSetState(args[1], building = true, 0L, 0L))
                    "accepted" -> {
                        if (args.size < 5) {
                            sender.sendMessage(mm("<red>Verwendung: /admin event set &lt;id&gt; accepted &lt;start&gt; &lt;ende&gt;</red>"))
                            return
                        }
                        val startEpoch = DateTimeParser.parseToEpochSeconds(args[3])
                        val endEpoch = DateTimeParser.parseToEpochSeconds(args[4])
                        if (startEpoch == null || endEpoch == null) {
                            sender.sendMessage(mm("<red>Ungültiges Datum/Uhrzeit! Format: ${DateTimeParser.FORMAT_HINT} (z.B. 10.07.2026-18:00)</red>"))
                            return
                        }
                        respondEvent(sender, plugin.eventManager.adminSetState(args[1], building = false, startEpoch, endEpoch))
                    }
                    else -> sender.sendMessage(mm("<red>Verwendung: /admin event set &lt;id&gt; &lt;building|accepted&gt; [start] [ende]</red>"))
                }
            }

            "reward" -> {
                if (args.size < 3) {
                    sender.sendMessage(mm("<red>Verwendung: /admin event reward &lt;spieler&gt; &lt;anzahl&gt;</red>"))
                    return
                }
                val amount = args[2].toIntOrNull()
                if (amount == null || amount <= 0) {
                    sender.sendMessage(mm("<red>Die Anzahl der Diamanten muss positiv sein!</red>"))
                    return
                }
                val target = resolveKnownPlayer(args[1])
                if (target == null) {
                    sender.sendMessage(mm("<red>Dieser Spieler ist unbekannt!</red>"))
                    return
                }

                plugin.rewardManager.grantDiamondReward(target.uniqueId, amount)

                val targetName = target.name
                sender.sendMessage(mm("<green>✓ <gold>$amount Diamant(en)</gold> wurden an <gold>$targetName</gold> vergeben!</green>"))
            }

            "list" -> handleEventList(sender, args.drop(1))

            else -> sendEventHelp(sender)
        }
    }

    private fun handleEventList(sender: Player, args: List<String>) {
        val filter = args.getOrNull(0)?.lowercase()
        if (filter == "past" || filter == "ended") {
            val past = plugin.eventManager.listPastEvents()
            if (past.isEmpty()) {
                sender.sendMessage(mm("<gray>Keine Events in diesem Status gefunden.</gray>"))
                return
            }
            val lines = mutableListOf("<gold>=== Events (VERGANGEN) ===</gold>")
            past.forEach { event ->
                val timeInfo = if (event.endTime > 0) {
                    " <dark_gray>(beendet: ${DateTimeParser.parseToString(event.endTime)})</dark_gray>"
                } else {
                    ""
                }
                lines += "<gray>- <yellow>${event.id}</yellow> <white>${escape(event.name)}</white>$timeInfo</gray>"
            }
            sender.sendMessage(mm(lines.joinToString("\n")))
            return
        }
        val state = when (filter) {
            "pending" -> EventState.SUBMITTED
            "accepted" -> EventState.ACCEPTED
            "active", null -> EventState.ACTIVE
            else -> {
                sender.sendMessage(mm("<red>Verwendung: /admin event list &lt;pending|accepted|active|past&gt;</red>"))
                return
            }
        }

        val events = plugin.eventManager.listByState(state)
        if (events.isEmpty()) {
            sender.sendMessage(mm("<gray>Keine Events in diesem Status gefunden.</gray>"))
            return
        }

        val lines = mutableListOf("<gold>=== Events (${state.name}) ===</gold>")
        events.forEach { event ->
            val timeInfo = if (event.startTime > 0) {
                " <dark_gray>(${DateTimeParser.parseToString(event.startTime)} - ${DateTimeParser.parseToString(event.endTime)})</dark_gray>"
            } else {
                ""
            }
            lines += "<gray>- <yellow>${event.id}</yellow> <white>${escape(event.name)}</white>$timeInfo</gray>"
        }
        sender.sendMessage(mm(lines.joinToString("\n")))
    }

    private fun respondEvent(player: Player, result: EventManager.Result) {
        when (result) {
            is EventManager.Result.Success -> player.sendMessage(mm(result.message))
            is EventManager.Result.Error -> player.sendMessage(mm(result.message))
        }
    }

    private fun sendEventHelp(player: Player) {
        player.sendMessage(
            mm(
                """
                <gold>=== Event-Administration ===</gold>
                <yellow>/admin event review &lt;id&gt;</yellow><gray> - Eingereichtes Event prüfen</gray>
                <yellow>/admin event accept &lt;id&gt; &lt;start&gt; &lt;ende&gt;</yellow><gray> - Event annehmen (Format: ${DateTimeParser.FORMAT_HINT})</gray>
                <yellow>/admin event reject &lt;id&gt; &lt;grund&gt;</yellow><gray> - Event ablehnen</gray>
                <yellow>/admin event set &lt;id&gt; &lt;building|accepted&gt; [start] [ende]</yellow><gray> - Zustand zurücksetzen (Format: ${DateTimeParser.FORMAT_HINT})</gray>
                <yellow>/admin event reward &lt;spieler&gt; &lt;anzahl&gt;</yellow><gray> - Diamanten an Spieler vergeben</gray>
                <yellow>/admin event list &lt;pending|accepted|active|past&gt;</yellow><gray> - Events auflisten</gray>
                """.trimIndent(),
            ),
        )
    }

    // ------------------------------------------------------------------
    // pvp (moved from /pvp admin)
    // ------------------------------------------------------------------

    private fun handlePvp(sender: CommandSender, args: List<String>) {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return
        }

        if (args.size < 2) {
            sender.sendMessage(
                mm(
                    """
                    <gold>=== PvP Admin ===</gold>
                    <gray>/admin pvp &lt;spieler|all&gt; on</gray>
                    <gray>/admin pvp &lt;spieler|all&gt; off</gray>
                    """.trimIndent(),
                ),
            )
            return
        }

        val targetArg = args[0]
        val status = when (args[1].lowercase()) {
            "on", "enable" -> true
            "off", "disable" -> false
            else -> {
                sender.sendMessage(mm("<red>Verwende 'on' oder 'off'</red>"))
                return
            }
        }

        val manager = plugin.pvpManager
        val store = plugin.store.pvpStatusStore

        if (targetArg.equals("all", ignoreCase = true)) {
            var count = 0
            plugin.server.onlinePlayers.forEach { online ->
                plugin.messageManager.sendOrQueue(
                    online,
                    "<yellow>Dein PvP Status wurde von einem Admin geändert!</yellow>",
                    true,
                )
                manager.cancelToggle(online.uniqueId)
                store.applyPvPStatus(online.uniqueId, status)
                count++
            }
            sender.sendMessage(mm("<green>✓ PvP wurde für <gold>$count</gold> Spieler ${if (status) "aktiviert" else "deaktiviert"}!</green>"))
            return
        }

        val target = plugin.server.getPlayer(targetArg)
        if (target == null) {
            sender.sendMessage(mm("<red>Spieler nicht gefunden oder offline!</red>"))
            return
        }

        plugin.messageManager.sendOrQueue(
            target,
            "<yellow>Dein PvP Status wurde von einem Admin geändert!</yellow>",
            true,
        )
        manager.cancelToggle(target.uniqueId)
        store.applyPvPStatus(target.uniqueId, status)
        sender.sendMessage(mm("<green>✓ PvP von <gold>${target.name}</gold> wurde ${if (status) "aktiviert" else "deaktiviert"}!</green>"))
    }

    // ------------------------------------------------------------------
    // lobby lock
    // ------------------------------------------------------------------

    private fun handleLobby(sender: CommandSender, args: List<String>) {
        when (args.getOrNull(0)?.lowercase()) {
            "lock" -> {
                if (plugin.lobbyManager.locked) {
                    sender.sendMessage(mm("<yellow>Die Lobby ist bereits gesperrt!</yellow>"))
                    return
                }
                plugin.lobbyManager.locked = true
                sender.sendMessage(mm("<green>✓ Die Lobby wurde gesperrt! Spieler können sie nicht mehr verlassen.</green>"))
                Bukkit.broadcast(mm("<red><bold>Die Lobby wurde von einem Admin gesperrt! Ihr könnt sie vorerst nicht verlassen.</bold></red>"))
            }

            "unlock" -> {
                if (!plugin.lobbyManager.locked) {
                    sender.sendMessage(mm("<yellow>Die Lobby ist derzeit nicht gesperrt!</yellow>"))
                    return
                }
                plugin.lobbyManager.locked = false
                sender.sendMessage(mm("<green>✓ Die Lobby wurde wieder freigegeben!</green>"))
                Bukkit.broadcast(mm("<green>Die Lobby wurde wieder freigegeben!</green>"))
            }

            "status" -> {
                val state = if (plugin.lobbyManager.locked) "<red>gesperrt</red>" else "<green>offen</green>"
                val count = plugin.lobbyManager.lobbyPlayerCount()
                sender.sendMessage(mm("<gray>Lobby-Status: $state <dark_gray>($count Spieler in der Lobby)</dark_gray></gray>"))
            }

            else -> sendLobbyHelp(sender)
        }
    }

    private fun sendLobbyHelp(sender: CommandSender) {
        sender.sendMessage(
            mm(
                """
                <gold>=== Lobby-Administration ===</gold>
                <yellow>/admin lobby lock</yellow><gray> - Lobby sperren (Spieler können nicht raus)</gray>
                <yellow>/admin lobby unlock</yellow><gray> - Lobby wieder freigeben</gray>
                <yellow>/admin lobby status</yellow><gray> - Sperrstatus anzeigen</gray>
                """.trimIndent(),
            ),
        )
    }

    // ------------------------------------------------------------------
    // msg (server announcements to a player, a team or everyone)
    // ------------------------------------------------------------------

    private fun handleMsg(sender: CommandSender, args: List<String>) {
        if (args.size < 2) {
            sender.sendMessage(mm("<red>Verwendung: /admin msg &lt;spieler|team-a|team-b|all&gt; &lt;nachricht&gt;</red>"))
            return
        }

        val text = args.drop(1).joinToString(" ")
        // Same bracket style as the shared prefix helper, but tagged [Server].
        val tagged = "<dark_gray>[<gold>Server</gold>]</dark_gray> $text"
        val targetArg = args[0]

        if (targetArg.equals("all", ignoreCase = true)) {
            val count = plugin.server.onlinePlayers.size
            Bukkit.broadcast(mm(tagged))
            sender.sendMessage(mm("<green>✓ Nachricht an <gold>$count</gold> Spieler gesendet!</green>"))
            return
        }

        resolveTeam(targetArg)?.let { team ->
            var count = 0
            plugin.server.onlinePlayers.forEach { online ->
                if (plugin.teamManager.getPlayerTeam(online.uniqueId) == team) {
                    online.sendMessage(mm(tagged))
                    count++
                }
            }
            sender.sendMessage(mm("<green>✓ Nachricht an <gold>$count</gold> Online-Spieler von <gold>${escape(team.displayName)}</gold> gesendet!</green>"))
            return
        }

        val target = resolveKnownPlayer(targetArg)
        if (target == null) {
            sender.sendMessage(mm("<red>Dieser Spieler ist unbekannt! Verwende einen Spielernamen, team-a, team-b oder all.</red>"))
            return
        }
        // Queued when the player is offline or AFK, like other admin messages.
        plugin.messageManager.sendOrQueue(target.uniqueId, tagged)
        sender.sendMessage(mm("<green>✓ Nachricht an <gold>${target.name}</gold> gesendet!</green>"))
    }

    private fun resolveTeam(arg: String): Team? = Team.entries.firstOrNull { team ->
        arg.equals(team.configKey, ignoreCase = true) ||
            arg.equals(team.displayName.replace(" ", ""), ignoreCase = true) ||
            arg.equals(team.name.removePrefix("TEAM_"), ignoreCase = true)
    }

    // ------------------------------------------------------------------
    // image
    // ------------------------------------------------------------------

    private fun handleImage(sender: CommandSender, args: List<String>) {
        when (args.getOrNull(0)?.lowercase()) {
            "limit" -> handleImageLimit(sender, args.drop(1))
            "list" -> handleImageList(sender, args.drop(1))
            "inspect" -> handleImageInspect(sender, args.drop(1))
            "remove" -> handleImageRemove(sender, args.drop(1))
            "ban" -> handleImageBan(sender, args.drop(1))
            "unban" -> handleImageUnban(sender, args.drop(1))
            else -> sendImageHelp(sender)
        }
    }

    private fun handleImageBan(sender: CommandSender, args: List<String>) {
        if (args.size != 1) {
            sender.sendMessage(mm("<red>Verwendung: /admin image ban &lt;spieler&gt;</red>"))
            return
        }
        val target = resolveKnownPlayer(args[0])
        if (target == null) {
            sender.sendMessage(mm("<red>Dieser Spieler ist unbekannt!</red>"))
            return
        }
        if (plugin.store.imageBanStore.isBanned(target.uniqueId)) {
            sender.sendMessage(mm("<red>${escape(target.name ?: "?")} ist bereits für die Bilder-Funktion gesperrt!</red>"))
            return
        }
        plugin.imageManager.banPlayer(target.uniqueId)
        val name = escape(target.name ?: "?")
        sender.sendMessage(mm("<green>✓ <gold>$name</gold> ist jetzt für die Bilder-Funktion gesperrt!</green>"))
        target.player?.sendMessage(mm("<red>Du wurdest für die Bilder-Funktion gesperrt! Deine Bilder und Karten sind vorerst deaktiviert.</red>"))
    }

    private fun handleImageUnban(sender: CommandSender, args: List<String>) {
        if (args.size != 1) {
            sender.sendMessage(mm("<red>Verwendung: /admin image unban &lt;spieler&gt;</red>"))
            return
        }
        val target = resolveKnownPlayer(args[0])
        if (target == null) {
            sender.sendMessage(mm("<red>Dieser Spieler ist unbekannt!</red>"))
            return
        }
        if (!plugin.store.imageBanStore.isBanned(target.uniqueId)) {
            sender.sendMessage(mm("<red>${escape(target.name ?: "?")} ist nicht für die Bilder-Funktion gesperrt!</red>"))
            return
        }
        plugin.imageManager.unbanPlayer(target.uniqueId)
        val name = escape(target.name ?: "?")
        sender.sendMessage(mm("<green>✓ <gold>$name</gold> ist wieder für die Bilder-Funktion freigeschaltet!</green>"))
        target.player?.sendMessage(mm("<green>Du bist wieder für die Bilder-Funktion freigeschaltet! Deine Bilder und Karten funktionieren wieder.</green>"))
    }

    private fun handleImageLimit(sender: CommandSender, args: List<String>) {
        if (args.size < 2) {
            sender.sendMessage(mm("<red>Verwendung: /admin image limit &lt;inc|dec&gt; &lt;spieler&gt;</red>"))
            return
        }
        val target = resolveKnownPlayer(args[1])
        if (target == null) {
            sender.sendMessage(mm("<red>Dieser Spieler ist unbekannt!</red>"))
            return
        }
        val newLimit = when (args[0].lowercase()) {
            "inc", "increase", "+" -> plugin.store.imageLimitStore.increaseLimit(target.uniqueId)
            "dec", "decrease", "-" -> plugin.store.imageLimitStore.decreaseLimit(target.uniqueId)
            else -> {
                sender.sendMessage(mm("<red>Verwendung: /admin image limit &lt;inc|dec&gt; &lt;spieler&gt;</red>"))
                return
            }
        }
        val count = plugin.imageManager.getImageCount(target.uniqueId)
        sender.sendMessage(mm("<green>✓ Bildlimit von <gold>${escape(target.name ?: "?")}</gold> auf <gold>$newLimit</gold> gesetzt! ($count/$newLimit Bilder)</green>"))
    }

    private fun handleImageList(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            val entries = plugin.imageManager.getAllKnownPlayers()
                .map { uuid ->
                    val count = plugin.imageManager.getImageCount(uuid)
                    val limit = plugin.store.imageLimitStore.getLimit(uuid)
                    Triple(uuid, count, limit)
                }
                // Exclude zero-image players, except when their limit is also 0
                // or they are banned (admins want to see those).
                .filter { (uuid, count, limit) ->
                    count > 0 || limit == 0 || plugin.store.imageBanStore.isBanned(uuid)
                }
                .sortedBy { (uuid, _, _) -> plugin.server.getOfflinePlayer(uuid).name?.lowercase() ?: uuid.toString() }

            if (entries.isEmpty()) {
                sender.sendMessage(mm("<gray>Keine Spieler mit Bildern gefunden.</gray>"))
                return
            }
            val lines = mutableListOf("<gold>=== Bilder-Übersicht ===</gold>")
            entries.forEach { (uuid, count, limit) ->
                val name = escape(plugin.server.getOfflinePlayer(uuid).name ?: uuid.toString())
                val nameTag = if (plugin.store.imageBanStore.isBanned(uuid)) "<red>$name</red>" else "<yellow>$name</yellow>"
                lines += "<gray>- $nameTag <white>$count/$limit</white></gray>"
            }
            sender.sendMessage(mm(lines.joinToString("\n")))
            return
        }

        // /admin image list <player>
        val target = resolveKnownPlayer(args[0])
        if (target == null) {
            sender.sendMessage(mm("<red>Dieser Spieler ist unbekannt!</red>"))
            return
        }
        val images = plugin.imageManager.listImages(target.uniqueId)
        val limit = plugin.store.imageLimitStore.getLimit(target.uniqueId)
        val banSuffix = if (plugin.store.imageBanStore.isBanned(target.uniqueId)) " <red>[GESPERRT]</red>" else ""
        if (images.isEmpty()) {
            sender.sendMessage(mm("<gray>${escape(target.name ?: "?")} hat keine Bilder gespeichert. (Limit: $limit)</gray>$banSuffix"))
            return
        }
        val lines = mutableListOf("<gold>=== Bilder von ${escape(target.name ?: "?")} (${images.size}/$limit) ===</gold>$banSuffix")
        images.forEach { lines += "<gray>- <yellow>${escape(it)}</yellow></gray>" }
        sender.sendMessage(mm(lines.joinToString("\n")))
    }

    private fun handleImageInspect(sender: CommandSender, args: List<String>) {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return
        }
        if (args.size < 2) {
            sender.sendMessage(mm("<red>Verwendung: /admin image inspect &lt;spieler&gt; &lt;datei&gt;</red>"))
            return
        }
        val target = resolveKnownPlayer(args[0])
        if (target == null) {
            sender.sendMessage(mm("<red>Dieser Spieler ist unbekannt!</red>"))
            return
        }
        // File names may contain spaces: join everything after the player name.
        val file = args.drop(1).joinToString(" ").trim()
        val result = plugin.imageManager.giveImage(sender, target.uniqueId, file)
        when (result) {
            is de.davidsw.diawars.managers.ImageManager.Result.Success -> sender.sendMessage(mm(result.message))
            is de.davidsw.diawars.managers.ImageManager.Result.Error -> sender.sendMessage(mm(result.message))
        }
    }

    private fun handleImageRemove(sender: CommandSender, args: List<String>) {
        if (args.size < 2) {
            sender.sendMessage(mm("<red>Verwendung: /admin image remove &lt;spieler&gt; &lt;datei|all&gt;</red>"))
            return
        }
        val target = resolveKnownPlayer(args[0])
        if (target == null) {
            sender.sendMessage(mm("<red>Dieser Spieler ist unbekannt!</red>"))
            return
        }
        // File names may contain spaces: join everything after the player name.
        val file = args.drop(1).joinToString(" ").trim()
        if (file.equals("all", ignoreCase = true)) {
            val removed = plugin.imageManager.deleteAllImages(target.uniqueId)
            sender.sendMessage(mm("<green>✓ <gold>$removed</gold> Bild(er) von <gold>${escape(target.name ?: "?")}</gold> gelöscht!</green>"))
            return
        }
        if (!plugin.imageManager.hasImage(target.uniqueId, file)) {
            sender.sendMessage(mm("<red>Dieses Bild existiert nicht!</red>"))
            return
        }
        plugin.imageManager.deleteImage(target.uniqueId, file)
        sender.sendMessage(mm("<green>✓ Bild <gold>${escape(file)}</gold> von <gold>${escape(target.name ?: "?")}</gold> gelöscht!</green>"))
    }

    private fun sendImageHelp(sender: CommandSender) {
        sender.sendMessage(
            mm(
                """
                <gold>=== Bilder-Administration ===</gold>
                <yellow>/admin image limit &lt;inc|dec&gt; &lt;spieler&gt;</yellow><gray> - Bildlimit um 10 erhöhen/senken</gray>
                <yellow>/admin image list</yellow><gray> - Alle Spieler mit Bildanzahl und Limit</gray>
                <yellow>/admin image list &lt;spieler&gt;</yellow><gray> - Bilder eines Spielers auflisten</gray>
                <yellow>/admin image inspect &lt;spieler&gt; &lt;datei&gt;</yellow><gray> - Bild eines Spielers ansehen</gray>
                <yellow>/admin image remove &lt;spieler&gt; &lt;datei|all&gt;</yellow><gray> - Bild(er) löschen</gray>
                <yellow>/admin image ban &lt;spieler&gt;</yellow><gray> - Spieler für die Bilder-Funktion sperren</gray>
                <yellow>/admin image unban &lt;spieler&gt;</yellow><gray> - Sperre aufheben</gray>
                """.trimIndent(),
            ),
        )
    }

    // ------------------------------------------------------------------
    // config (server settings persisted to config.yml)
    // ------------------------------------------------------------------

    private fun handleConfig(sender: CommandSender, args: List<String>) {
        if (args.isEmpty() || args[0].equals("list", ignoreCase = true)) {
            val prefix = args.getOrNull(1)
            sender.sendMessage(mm("<gold>=== Konfiguration ===</gold>"))
            plugin.configManager.all()
                .filter { prefix == null || it.key.startsWith(prefix, ignoreCase = true) }
                .forEach { opt ->
                    sender.sendMessage(mm("<yellow>${opt.key}</yellow><gray> = </gray><white>${plugin.configManager.displayValue(opt.key)}</white><gray> (${opt.type}, Standard: ${opt.default})</gray>"))
                }
            sender.sendMessage(mm("<gray>/admin config set <schluessel> <wert> zum Aendern, /admin config get <schluessel></gray>"))
            return
        }
        if (args[0].equals("get", ignoreCase = true)) {
            if (args.size < 2) {
                sender.sendMessage(mm("<red>Verwendung: /admin config get &lt;schluessel&gt;</red>"))
                return
            }
            val key = args[1]
            val opt = plugin.configManager.option(key)
            if (opt == null) {
                sender.sendMessage(mm("<red>Unbekannte Einstellung: $key</red>"))
                return
            }
            sender.sendMessage(mm("<yellow>$key</yellow><gray> = </gray><white>${plugin.configManager.displayValue(key)}</white>"))
            return
        }
        if (args[0].equals("set", ignoreCase = true)) {
            if (args.size < 3) {
                sender.sendMessage(mm("<red>Verwendung: /admin config set &lt;schluessel&gt; &lt;wert&gt;</red>"))
                return
            }
            val key = args[1]
            val value = args.drop(2).joinToString(" ")
            sender.sendMessage(mm(plugin.configManager.set(key, value)))
            return
        }
        // Backwards-compatible shorthand: /admin config <key> <value...>
        if (args.size >= 2) {
            val key = args[0]
            val value = args.drop(1).joinToString(" ")
            sender.sendMessage(mm(plugin.configManager.set(key, value)))
            return
        }
        sender.sendMessage(mm("<red>Verwendung: /admin config [list|get|set] ...</red>"))
    }


    // ------------------------------------------------------------------
    // team config (teams.yml)
    // ------------------------------------------------------------------

    private fun handleTeam(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(mm("<red>Verwendung: /admin team list|name|spawn|player ...</red>"))
            return
        }
        when (args[0].lowercase()) {
            "list" -> {
                Team.entries.forEach { team ->
                    val members = plugin.teamManager.getTeamMembers(team).size
                    val spawn = plugin.teamManager.getSpawnLocation(team)
                    sender.sendMessage(mm("<yellow>${team.configKey}</yellow><gray>: ${team.displayName}, Spawn=${spawn?.world?.name ?: "?"} ${spawn?.blockX ?: 0}/${spawn?.blockY ?: 0}/${spawn?.blockZ ?: 0}, Mitglieder=$members</gray>"))
                }
            }
            "name" -> {
                if (args.size < 3) {
                    sender.sendMessage(mm("<red>Verwendung: /admin team name &lt;team-a|team-b&gt; &lt;name&gt;</red>"))
                    return
                }
                val team = Team.entries.firstOrNull { it.configKey.equals(args[1], ignoreCase = true) }
                    ?: run { sender.sendMessage(mm("<red>Unbekanntes Team: ${args[1]}</red>")); return }
                val name = args.drop(2).joinToString(" ")
                plugin.teamManager.setDisplayName(team, name)
                sender.sendMessage(mm("<green>OK ${team.configKey} display-name = $name</green>"))
            }
            "spawn" -> {
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /admin team spawn &lt;team-a|team-b&gt; [world x y z yaw pitch] (oder am eigenen Standort)</red>"))
                    return
                }
                val team = Team.entries.firstOrNull { it.configKey.equals(args[1], ignoreCase = true) }
                    ?: run { sender.sendMessage(mm("<red>Unbekanntes Team: ${args[1]}</red>")); return }
                if (args.size >= 8) {
                    val world = args[2]
                    val x = args[3].toDoubleOrNull(); val y = args[4].toDoubleOrNull(); val z = args[5].toDoubleOrNull()
                    val yaw = args[6].toFloatOrNull(); val pitch = args[7].toFloatOrNull()
                    if (x == null || y == null || z == null || yaw == null || pitch == null) {
                        sender.sendMessage(mm("<red>Ungueltige Koordinaten.</red>"))
                        return
                    }
                    plugin.teamManager.setSpawn(team, world, x, y, z, yaw, pitch)
                } else {
                    val p = sender as? org.bukkit.entity.Player
                        ?: run { sender.sendMessage(mm("<red>Standort nur als Spieler oder Koordinaten angeben.</red>")); return }
                    plugin.teamManager.setSpawn(team, p.world.name, p.location.x, p.location.y, p.location.z, p.location.yaw, p.location.pitch)
                }
                sender.sendMessage(mm("<green>OK Spawn fuer ${team.configKey} gesetzt.</green>"))
            }
            "player" -> {
                if (args.size < 4) {
                    sender.sendMessage(mm("<red>Verwendung: /admin team player add|remove &lt;team&gt; &lt;spieler&gt;</red>"))
                    return
                }
                val action = args[1].lowercase()
                val team = Team.entries.firstOrNull { it.configKey.equals(args[2], ignoreCase = true) }
                    ?: run { sender.sendMessage(mm("<red>Unbekanntes Team: ${args[2]}</red>")); return }
                val target = resolveKnownPlayer(args[3])
                    ?: run { sender.sendMessage(mm("<red>Unbekannter Spieler: ${args[3]}</red>")); return }
                when (action) {
                    "add" -> { plugin.teamManager.addPlayer(team, target.uniqueId); sender.sendMessage(mm("<green>OK Spieler hinzugefuegt.</green>")) }
                    "remove" -> { plugin.teamManager.removePlayer(team, target.uniqueId); sender.sendMessage(mm("<green>OK Spieler entfernt.</green>")) }
                    else -> sender.sendMessage(mm("<red>add oder remove.</red>"))
                }
            }
            else -> sender.sendMessage(mm("<red>Unbekannter Unterbefehl.</red>"))
        }
    }

    // ------------------------------------------------------------------
    // vault config (vaults.yml)
    // ------------------------------------------------------------------

    private fun handleVaultConfig(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(mm("<red>Verwendung: /admin vault list|set|add|remove ...</red>"))
            return
        }
        when (args[0].lowercase()) {
            "list" -> {
                plugin.vaultManager.getAllVaults().forEach { v ->
                    sender.sendMessage(mm("<yellow>${v.id}</yellow><gray>: ${v.displayName}, team=${v.team.configKey}, world=${v.world}, min=${v.minX}/${v.minY}/${v.minZ}, max=${v.maxX}/${v.maxZ}</gray>"))
                }
            }
            "set" -> {
                if (args.size < 4) {
                    sender.sendMessage(mm("<red>Verwendung: /admin vault set &lt;id&gt; &lt;feld&gt; &lt;wert&gt;</red>"))
                    return
                }
                val id = args[1]; val field = args[2]; val value = args.drop(3).joinToString(" ")
                sender.sendMessage(mm(plugin.vaultManager.setVaultField(id, field, value)))
            }
            "remove" -> {
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /admin vault remove &lt;id&gt;</red>"))
                    return
                }
                if (plugin.vaultManager.removeVault(args[1])) sender.sendMessage(mm("<green>OK Vault entfernt.</green>"))
                else sender.sendMessage(mm("<red>Vault nicht gefunden.</red>"))
            }
            "add" -> {
                // /admin vault add <id> <team> <world> x1 y1 z1 x2 y2 z2 [display-name...]
                if (args.size < 10) {
                    sender.sendMessage(mm("<red>Verwendung: /admin vault add &lt;id&gt; &lt;team&gt; &lt;world&gt; x1 y1 z1 x2 y2 z2 [name...]</red>"))
                    return
                }
                val id = args[1]; val team = args[2]; val world = args[3]
                val nums = args.slice(4..9).map { it.toIntOrNull() }
                if (nums.any { it == null }) { sender.sendMessage(mm("<red>Ungueltige Koordinaten.</red>")); return }
                val name = if (args.size > 10) args.drop(10).joinToString(" ") else id
                sender.sendMessage(mm(plugin.vaultManager.addVault(id, name, world, team, nums.take(3).map { it!! }, nums.drop(3).map { it!! })))
            }
            else -> sender.sendMessage(mm("<red>Unbekannter Unterbefehl.</red>"))
        }
    }

    // ------------------------------------------------------------------
    // mod whitelist config (mod_whitelist.yml)
    // ------------------------------------------------------------------

    private fun handleModWhitelist(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(mm("<red>Verwendung: /admin mods whitelist list|add|remove|library ...</red>"))
            return
        }
        when (args[0].lowercase()) {
            "list" -> {
                sender.sendMessage(mm("<gold>=== Erlaubte Mods ===</gold>"))
                plugin.modWhitelistManager.getAllowedMods().forEach { m ->
                    sender.sendMessage(mm("<gray>- ${m.checkId} = ${m.displayName}</gray>"))
                }
                sender.sendMessage(mm("<gold>=== Libraries ===</gold>"))
                plugin.modWhitelistManager.getLibraries().forEach { id -> sender.sendMessage(mm("<gray>- $id</gray>")) }
            }
            "add" -> {
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /admin mods whitelist add &lt;check&gt; [display]</red>"))
                    return
                }
                val check = args[1]
                val display = if (args.size > 2) args.drop(2).joinToString(" ") else null
                sender.sendMessage(mm(plugin.modWhitelistManager.addAllowedMod(check, display)))
            }
            "remove" -> {
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /admin mods whitelist remove &lt;check&gt;</red>"))
                    return
                }
                if (plugin.modWhitelistManager.removeAllowedMod(args[1])) sender.sendMessage(mm("<green>OK Mod entfernt.</green>"))
                else sender.sendMessage(mm("<red>Mod nicht gefunden.</red>"))
            }
            "library" -> {
                if (args.size < 3) {
                    sender.sendMessage(mm("<red>Verwendung: /admin mods whitelist library add|remove &lt;id&gt;</red>"))
                    return
                }
                when (args[1].lowercase()) {
                    "add" -> sender.sendMessage(mm(plugin.modWhitelistManager.addLibrary(args[2])))
                    "remove" -> if (plugin.modWhitelistManager.removeLibrary(args[2])) sender.sendMessage(mm("<green>OK Library entfernt.</green>")) else sender.sendMessage(mm("<red>Library nicht gefunden.</red>"))
                    else -> sender.sendMessage(mm("<red>add oder remove.</red>"))
                }
            }
            else -> sender.sendMessage(mm("<red>Unbekannter Unterbefehl.</red>"))
        }
    }
    // ------------------------------------------------------------------
    // help
    // ------------------------------------------------------------------

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(
            mm(
                """
                <gold>=== Admin-Befehle ===</gold>
                <yellow>/admin reload</yellow><gray> - Lädt alle Konfigurationsdateien neu</gray>
                <yellow>/admin inv main &lt;spieler&gt;</yellow><gray> - Hauptinventar (Normalwelt)</gray>
                <yellow>/admin inv event &lt;spieler&gt; &lt;event-id&gt;</yellow><gray> - Event-Inventar</gray>
                <yellow>/admin inv enderchest &lt;spieler&gt; [event-id]</yellow><gray> - Enderkiste</gray>
                <yellow>/admin log [anzahl]</yellow><gray> - Diamond-Log anzeigen</gray>
                <yellow>/admin bug list</yellow><gray> - Offene Bugs auflisten</gray>
                <yellow>/admin bug resolve &lt;id&gt;</yellow><gray> - Bug als behoben markieren</gray>
                <yellow>/admin report list</yellow><gray> - Offene Reports auflisten</gray>
                <yellow>/admin report view &lt;id&gt;</yellow><gray> - Report-Details ansehen</gray>
                <yellow>/admin report shot &lt;id&gt;</yellow><gray> - Screenshot-Karte erhalten</gray>
                <yellow>/admin report resolve &lt;id&gt;</yellow><gray> - Report als behandelt markieren</gray>
                <yellow>/admin mods list</yellow><gray> - Offene Mod-Anfragen auflisten</gray>
                <yellow>/admin mods resolve &lt;id&gt;</yellow><gray> - Mod-Anfrage als behandelt markieren</gray>
                <yellow>/admin event review &lt;id&gt;</yellow><gray> - Eingereichtes Event prüfen</gray>
                <yellow>/admin event accept &lt;id&gt; &lt;start&gt; &lt;ende&gt;</yellow><gray> - Event annehmen</gray>
                <yellow>/admin event reject &lt;id&gt; &lt;grund&gt;</yellow><gray> - Event ablehnen</gray>
                <yellow>/admin event set &lt;id&gt; &lt;building|accepted&gt; [start] [ende]</yellow><gray> - Zustand zurücksetzen</gray>
                <yellow>/admin event reward &lt;spieler&gt; &lt;anzahl&gt;</yellow><gray> - Diamanten vergeben</gray>
                <yellow>/admin event list &lt;pending|accepted|active|past&gt;</yellow><gray> - Events auflisten</gray>
                <yellow>/admin pvp &lt;spieler|all&gt; &lt;on|off&gt;</yellow><gray> - PvP-Status setzen</gray>
                <yellow>/admin lobby lock|unlock|status</yellow><gray> - Lobby sperren/freigeben</gray>
                <yellow>/admin msg &lt;spieler|team-a|team-b|all&gt; &lt;nachricht&gt;</yellow><gray> - Nachricht mit [Server]-Tag senden</gray>
                <yellow>/admin image limit &lt;inc|dec&gt; &lt;spieler&gt;</yellow><gray> - Bildlimit ändern</gray>
                <yellow>/admin image list [spieler]</yellow><gray> - Bilder-Übersicht</gray>
                <yellow>/admin image inspect &lt;spieler&gt; &lt;datei&gt;</yellow><gray> - Bild ansehen</gray>
                <yellow>/admin image remove &lt;spieler&gt; &lt;datei|all&gt;</yellow><gray> - Bild(er) löschen</gray>
                <yellow>/admin image ban|unban &lt;spieler&gt;</yellow><gray> - Bilder-Funktion sperren/entsperren</gray>
                <yellow>/admin config list [präfix]</yellow><gray> - Alle Einstellungen auflisten</gray>
                <yellow>/admin config get &lt;schluessel&gt;</yellow><gray> - Wert anzeigen</gray>
                <yellow>/admin config set &lt;schluessel&gt; &lt;wert&gt;</yellow><gray> - Wert ändern</gray>
                """.trimIndent(),
            ),
        )
    }

    // ------------------------------------------------------------------
    // tab completion
    // ------------------------------------------------------------------

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (args.size == 1) {
            return listOf("reload", "inv", "log", "bug", "report", "mods", "event", "pvp", "lobby", "msg", "image", "config")
                .filter { it.startsWith(args[0].lowercase()) }
        }

        when (args[0].lowercase()) {
            "inv" -> {
                if (args.size == 2) {
                    return listOf("main", "event", "enderchest").filter { it.startsWith(args[1].lowercase()) }
                }
                if (args.size == 3) {
                    return plugin.server.onlinePlayers.map { it.name }
                        .filter { it.startsWith(args[2], ignoreCase = true) }
                }
                if (args.size == 4 &&
                    (args[1].equals("event", ignoreCase = true) || args[1].equals("enderchest", ignoreCase = true))
                ) {
                    return plugin.store.eventStore.getAll().map { it.id }
                        .filter { it.startsWith(args[3].lowercase()) }
                }
            }

            "bug" -> {
                if (args.size == 2) {
                    return listOf("list", "resolve").filter { it.startsWith(args[1].lowercase()) }
                }
                if (args.size == 3 && args[1].equals("resolve", ignoreCase = true)) {
                    return plugin.store.bugStore.getUnresolved().map { it.id }
                        .filter { it.startsWith(args[2]) }
                }
            }

            "report" -> {
                if (args.size == 2) {
                    return listOf("list", "view", "shot", "resolve").filter { it.startsWith(args[1].lowercase()) }
                }
                if (args.size == 3) {
                    return plugin.store.reportStore.getOpen().map { it.id }
                        .filter { it.startsWith(args[2]) }
                }
            }

            "mods" -> {
                if (args.size == 2) {
                    return listOf("list", "resolve").filter { it.startsWith(args[1].lowercase()) }
                }
                if (args.size == 3 && args[1].equals("resolve", ignoreCase = true)) {
                    return plugin.store.modRequestStore.getOpen().map { it.id }
                        .filter { it.startsWith(args[2]) }
                }
            }

            "event" -> {
                if (args.size == 2) {
                    return listOf("review", "accept", "reject", "set", "reward", "list")
                        .filter { it.startsWith(args[1].lowercase()) }
                }
                if (args.size == 3) {
                    when (args[1].lowercase()) {
                        "review", "accept", "reject" ->
                            return plugin.eventManager.listByState(EventState.SUBMITTED).map { it.id }
                                .filter { it.startsWith(args[2].lowercase()) }
                        "set" ->
                            return plugin.store.eventStore.getAll().sortedBy { it.id }.map { it.id }
                                .filter { it.startsWith(args[2].lowercase()) }
                        "reward" ->
                            return plugin.server.onlinePlayers.map { it.name }
                                .filter { it.startsWith(args[2], ignoreCase = true) }
                        "list" ->
                            return listOf("pending", "accepted", "active", "past")
                                .filter { it.startsWith(args[2].lowercase()) }
                    }
                }
                if (args.size == 4 && args[1].equals("set", ignoreCase = true)) {
                    return listOf("building", "accepted")
                        .filter { it.startsWith(args[3].lowercase()) }
                }
            }

            "pvp" -> {
                if (args.size == 2) {
                    val names = plugin.server.onlinePlayers.map { it.name } + "all"
                    return names.filter { it.startsWith(args[1], ignoreCase = true) }
                }
                if (args.size == 3) {
                    return listOf("on", "off").filter { it.startsWith(args[2].lowercase()) }
                }
            }

            "lobby" -> {
                if (args.size == 2) {
                    return listOf("lock", "unlock", "status").filter { it.startsWith(args[1].lowercase()) }
                }
            }

            "msg" -> {
                if (args.size == 2) {
                    val names = plugin.server.onlinePlayers.map { it.name } +
                        listOf("all", "team-a", "team-b")
                    return names.filter { it.startsWith(args[1], ignoreCase = true) }
                }
            }

            "image" -> {
                if (args.size == 2) {
                    return listOf("limit", "list", "inspect", "remove", "ban", "unban")
                        .filter { it.startsWith(args[1].lowercase()) }
                }
                if (args.size == 3) {
                    when (args[1].lowercase()) {
                        "limit" -> return listOf("inc", "dec").filter { it.startsWith(args[2].lowercase()) }
                        "list", "inspect", "remove", "ban", "unban" ->
                            return plugin.server.onlinePlayers.map { it.name }
                                .filter { it.startsWith(args[2], ignoreCase = true) }
                    }
                }
                if (args.size == 4) {
                    when (args[1].lowercase()) {
                        "limit" ->
                            return plugin.server.onlinePlayers.map { it.name }
                                .filter { it.startsWith(args[3], ignoreCase = true) }
                        "inspect", "remove" -> {
                            val target = plugin.server.getPlayer(args[2])
                                ?: return emptyList()
                            val files = plugin.imageManager.listImages(target.uniqueId).toMutableList()
                            if (args[1].equals("remove", ignoreCase = true)) files.add("all")
                            return files.filter { it.startsWith(args[3], ignoreCase = true) }
                        }
                    }
                }
            }

            "team" -> {
                if (args.size == 2) {
                    return (Team.entries.map { it.configKey } + listOf("list", "name", "spawn", "player"))
                        .filter { it.startsWith(args[1].lowercase(), ignoreCase = true) }
                }
                if (args.size == 3 && (args[1].equals("name", true) || args[1].equals("spawn", true))) {
                    return Team.entries.map { it.configKey }
                        .filter { it.startsWith(args[2].lowercase(), ignoreCase = true) }
                }
                if (args.size == 3 && args[1].equals("player", true)) {
                    return listOf("add", "remove").filter { it.startsWith(args[2].lowercase()) }
                }
                if (args.size == 4 && args[1].equals("player", true)) {
                    return Team.entries.map { it.configKey }
                        .filter { it.startsWith(args[3].lowercase(), ignoreCase = true) }
                }
            }

            "vault" -> {
                if (args.size == 2) {
                    return listOf("list", "set", "add", "remove").filter { it.startsWith(args[1].lowercase()) }
                }
                if (args.size == 3 && (args[1].equals("set", true) || args[1].equals("remove", true))) {
                    return plugin.vaultManager.getAllVaults().map { it.id }
                        .filter { it.startsWith(args[2].lowercase(), ignoreCase = true) }
                }
                if (args.size == 3 && args[1].equals("set", true)) {
                    return listOf("display-name", "world", "team", "corner1.x", "corner1.y", "corner1.z", "corner2.x", "corner2.y", "corner2.z")
                        .filter { it.startsWith(args[2].lowercase(), ignoreCase = true) }
                }
            }

            "config" -> {
                if (args.size == 2) {
                    val subs = listOf("list", "get", "set") + plugin.configManager.all().map { it.key }
                    return subs.filter { it.startsWith(args[1].lowercase(), ignoreCase = true) }
                }
                if (args.size == 3 && (args[1].equals("get", ignoreCase = true) || args[1].equals("set", ignoreCase = true))) {
                    return plugin.configManager.all().map { it.key }
                        .filter { it.startsWith(args[2].lowercase(), ignoreCase = true) }
                }
                if (args.size == 3) {
                    val opt = plugin.configManager.option(args[1])
                    if (opt != null && opt.type == de.davidsw.diawars.managers.ConfigManager.Type.BOOL) {
                        return listOf("true", "false").filter { it.startsWith(args[2].lowercase()) }
                    }
                }
                if (args.size >= 4 && args[1].equals("set", ignoreCase = true)) {
                    val opt = plugin.configManager.option(args[2])
                    if (opt != null && opt.type == de.davidsw.diawars.managers.ConfigManager.Type.BOOL) {
                        return listOf("true", "false").filter { it.startsWith(args[3].lowercase()) }
                    }
                }
            }
        }

        return emptyList()
    }
}
