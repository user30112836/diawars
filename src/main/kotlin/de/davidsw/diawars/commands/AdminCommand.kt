package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.BugManager
import de.davidsw.diawars.managers.EventManager
import de.davidsw.diawars.stores.EventState
import de.davidsw.diawars.util.DateTimeParser
import de.davidsw.diawars.util.MiniMessageHelper.escape
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit.getOfflinePlayer
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
            else -> sendHelp(sender)
        }

        return true
    }

    // ------------------------------------------------------------------
    // reload
    // ------------------------------------------------------------------

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

        val target = getOfflinePlayer(args[1])
        if (!target.hasPlayedBefore() && !target.isOnline) {
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
                val target = getOfflinePlayer(args[1])
                if (!target.hasPlayedBefore() && !target.isOnline) {
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
            return listOf("reload", "inv", "log", "bug", "event", "pvp")
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
        }

        return emptyList()
    }
}
