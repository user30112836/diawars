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
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> handleReload(sender)
            "inv" -> handleInv(sender, args.drop(1))
            "log" -> handleLog(sender, args.drop(1))
            "bug" -> handleBug(sender, args.drop(1))
            "event" -> handleEvent(sender, args.drop(1))
            "pvp" -> handlePvp(sender, args.drop(1))
            "lobby" -> handleLobby(sender, args.drop(1))
            "msg" -> handleMsg(sender, args.drop(1))
            "image" -> handleImage(sender, args.drop(1))
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
        val state = when (filter) {
            "pending" -> EventState.SUBMITTED
            "accepted" -> EventState.ACCEPTED
            "active", null -> EventState.ACTIVE
            else -> {
                sender.sendMessage(mm("<red>Verwendung: /admin event list &lt;pending|accepted|active&gt;</red>"))
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
                <yellow>/admin event reward &lt;spieler&gt; &lt;anzahl&gt;</yellow><gray> - Diamanten an Spieler vergeben</gray>
                <yellow>/admin event list &lt;pending|accepted|active&gt;</yellow><gray> - Events auflisten</gray>
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
        val result = plugin.imageManager.giveImage(sender, target.uniqueId, args[1])
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
        if (args[1].equals("all", ignoreCase = true)) {
            val removed = plugin.imageManager.deleteAllImages(target.uniqueId)
            sender.sendMessage(mm("<green>✓ <gold>$removed</gold> Bild(er) von <gold>${escape(target.name ?: "?")}</gold> gelöscht!</green>"))
            return
        }
        if (!plugin.imageManager.hasImage(target.uniqueId, args[1])) {
            sender.sendMessage(mm("<red>Dieses Bild existiert nicht!</red>"))
            return
        }
        plugin.imageManager.deleteImage(target.uniqueId, args[1])
        sender.sendMessage(mm("<green>✓ Bild <gold>${escape(args[1])}</gold> von <gold>${escape(target.name ?: "?")}</gold> gelöscht!</green>"))
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
        if (args.size != 2 || !args[0].equals("night-vision-toggle", ignoreCase = true)) {
            sender.sendMessage(mm("<red>Verwendung: /admin config night-vision-toggle &lt;true|false&gt;</red>"))
            return
        }
        val value = when (args[1].lowercase()) {
            "true", "on", "enable" -> true
            "false", "off", "disable" -> false
            else -> {
                sender.sendMessage(mm("<red>Verwendung: /admin config night-vision-toggle &lt;true|false&gt;</red>"))
                return
            }
        }
        plugin.config.set("night-vision-toggle", value)
        plugin.saveConfig()
        plugin.nightVisionManager.refreshGlobalState()
        sender.sendMessage(
            mm("<green>✓ Nachtsicht wurde ${if (value) "aktiviert" else "deaktiviert"}!</green>"),
        )
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
                <yellow>/admin event review &lt;id&gt;</yellow><gray> - Eingereichtes Event prüfen</gray>
                <yellow>/admin event accept &lt;id&gt; &lt;start&gt; &lt;ende&gt;</yellow><gray> - Event annehmen</gray>
                <yellow>/admin event reject &lt;id&gt; &lt;grund&gt;</yellow><gray> - Event ablehnen</gray>
                <yellow>/admin event reward &lt;spieler&gt; &lt;anzahl&gt;</yellow><gray> - Diamanten vergeben</gray>
                <yellow>/admin event list &lt;pending|accepted|active&gt;</yellow><gray> - Events auflisten</gray>
                <yellow>/admin pvp &lt;spieler|all&gt; &lt;on|off&gt;</yellow><gray> - PvP-Status setzen</gray>
                <yellow>/admin lobby lock|unlock|status</yellow><gray> - Lobby sperren/freigeben</gray>
                <yellow>/admin msg &lt;spieler|team-a|team-b|all&gt; &lt;nachricht&gt;</yellow><gray> - Nachricht mit [Server]-Tag senden</gray>
                <yellow>/admin image limit &lt;inc|dec&gt; &lt;spieler&gt;</yellow><gray> - Bildlimit ändern</gray>
                <yellow>/admin image list [spieler]</yellow><gray> - Bilder-Übersicht</gray>
                <yellow>/admin image inspect &lt;spieler&gt; &lt;datei&gt;</yellow><gray> - Bild ansehen</gray>
                <yellow>/admin image remove &lt;spieler&gt; &lt;datei|all&gt;</yellow><gray> - Bild(er) löschen</gray>
                <yellow>/admin image ban|unban &lt;spieler&gt;</yellow><gray> - Bilder-Funktion sperren/entsperren</gray>
                <yellow>/admin config night-vision-toggle &lt;true|false&gt;</yellow><gray> - Nachtsicht erlauben/sperren</gray>
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
            return listOf("reload", "inv", "log", "bug", "event", "pvp", "lobby", "msg", "image", "config")
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

            "event" -> {
                if (args.size == 2) {
                    return listOf("review", "accept", "reject", "reward", "list")
                        .filter { it.startsWith(args[1].lowercase()) }
                }
                if (args.size == 3) {
                    when (args[1].lowercase()) {
                        "review", "accept", "reject" ->
                            return plugin.eventManager.listByState(EventState.SUBMITTED).map { it.id }
                                .filter { it.startsWith(args[2].lowercase()) }
                        "reward" ->
                            return plugin.server.onlinePlayers.map { it.name }
                                .filter { it.startsWith(args[2], ignoreCase = true) }
                        "list" ->
                            return listOf("pending", "accepted", "active")
                                .filter { it.startsWith(args[2].lowercase()) }
                    }
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

            "config" -> {
                if (args.size == 2) {
                    return listOf("night-vision-toggle").filter { it.startsWith(args[1].lowercase()) }
                }
                if (args.size == 3 && args[1].equals("night-vision-toggle", ignoreCase = true)) {
                    return listOf("true", "false").filter { it.startsWith(args[2].lowercase()) }
                }
            }
        }

        return emptyList()
    }
}
