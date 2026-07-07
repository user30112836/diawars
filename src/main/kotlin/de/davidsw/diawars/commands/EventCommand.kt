package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.EventManager
import de.davidsw.diawars.stores.EventState
import de.davidsw.diawars.util.DateTimeParser
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit.getOfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class EventCommand(private val plugin: Diawars): CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "create" -> {
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /event create &lt;name&gt;</red>"))
                    return true
                }
                respond(sender, plugin.eventManager.createEvent(sender, args.slice(1 until args.size).joinToString(" ")))
            }

            "resume" -> respond(sender, plugin.eventManager.resumeBuilding(sender))

            "submit" -> respond(sender, plugin.eventManager.submitEvent(sender))

            "cancel" -> respond(sender, plugin.eventManager.cancelEvent(sender))

            "leave" -> respond(sender, plugin.eventManager.leaveEvent(sender))

            "join" -> {
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /event join &lt;id&gt;</red>"))
                    return true
                }
                respond(sender, plugin.eventManager.joinEvent(sender, args[1]))
            }

            "list" -> handleList(sender, args)

            "review" -> {
                if (!requireAdmin(sender)) return true
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /event review &lt;id&gt;</red>"))
                    return true
                }
                respond(sender, plugin.eventManager.reviewEvent(sender, args[1]))
            }

            "accept" -> {
                if (!requireAdmin(sender)) return true
                if (args.size < 4) {
                    sender.sendMessage(mm("<red>Verwendung: /event accept &lt;id&gt; &lt;start-in-minuten&gt; &lt;dauer-in-minuten&gt;</red>"))
                    return true
                }
                val startEpoch = DateTimeParser.parseToEpochSeconds(args[2])
                val endEpoch = DateTimeParser.parseToEpochSeconds(args[3])
                if (startEpoch == null || endEpoch == null) {
                    sender.sendMessage(mm("<red>Ungültiges Datum/Uhrzeit! Format: ${DateTimeParser.FORMAT_HINT} (z.B. 10.07.2026-18:00)</red>"))
                    return true
                }
                respond(sender, plugin.eventManager.acceptEvent(sender, args[1], startEpoch, endEpoch))
            }

            "reject" -> {
                if (!requireAdmin(sender)) return true
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /event reject &lt;id&gt;</red>"))
                    return true
                }
                respond(sender, plugin.eventManager.rejectEvent(sender, args[1]))
            }

            "reward" -> {
                if (!requireAdmin(sender)) return true
                if (args.size < 3) {
                    sender.sendMessage(mm("<red>Verwendung: /event reward &lt;spieler&gt; &lt;anzahl&gt;</red>"))
                    return true
                }
                val amount = args[2].toIntOrNull()
                if (amount == null || amount <= 0) {
                    sender.sendMessage(mm("<red>Die Anzahl der Diamanten muss positiv sein!</red>"))
                    return true
                }
                val target = getOfflinePlayer(args[1])
                if ((!target.hasPlayedBefore() && !target.isOnline) || target !is Player) {
                    sender.sendMessage(mm("<red>Dieser Spieler ist unbekannt!</red>"))
                    return true
                }

                plugin.rewardManager.grantDiamondReward(target, amount)

                val targetName = target.name
                sender.sendMessage(mm("<green>✓ <gold>$amount Diamant(en)</gold> wurden an <gold>$targetName</gold> vergeben!</green>"))
            }

            else -> sendHelp(sender)
        }

        return true
    }

    private fun handleList(sender: Player, args: Array<out String>) {
        val filter = args.getOrNull(1)?.lowercase()
        val state = when (filter) {
            "pending" -> {
                if (!requireAdmin(sender)) return
                EventState.SUBMITTED
            }
            "accepted" -> EventState.ACCEPTED
            "active", null -> EventState.ACTIVE
            else -> {
                sender.sendMessage(mm("<red>Verwendung: /event list &lt;pending|accepted|active&gt;</red>"))
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
            lines += "<gray>- <yellow>${event.id}</yellow> <white>${event.name}</white></gray>"
        }
        sender.sendMessage(mm(lines.joinToString("\n")))
    }

    private fun requireAdmin(sender: Player): Boolean {
        if (!sender.hasPermission("diawars.admin")) {
            plugin.messageManager.sendNoPermission(sender)
            return false
        }
        return true
    }

    private fun respond(player: Player, result: EventManager.Result) {
        when (result) {
            is EventManager.Result.Success -> player.sendMessage(mm(result.message))
            is EventManager.Result.Error -> player.sendMessage(mm(result.message))
        }
    }

    private fun sendHelp(player: Player) {
        val lines = mutableListOf(
            "<gold>=== Event-Befehle ===</gold>",
            "<yellow>/event create &lt;name&gt;</yellow><gray> - Neues Event erstellen</gray>",
            "<yellow>/event resume</yellow><gray> - Weiterbauen an deinem Event</gray>",
            "<yellow>/event submit</yellow><gray> - Event zur Prüfung einreichen</gray>",
            "<yellow>/event cancel</yellow><gray> - Event abbrechen und löschen</gray>",
            "<yellow>/event join &lt;id&gt;</yellow><gray> - Aktivem Event beitreten</gray>",
            "<yellow>/event leave</yellow><gray> - Event verlassen</gray>",
            "<yellow>/event list &lt;pending|accepted|active&gt;</yellow><gray> - Events auflisten</gray>",
        )
        if (player.hasPermission("diawars.admin")) {
            lines += "<yellow>/event review &lt;id&gt;</yellow><gray> - Eingereichtes Event prüfen</gray>"
            lines += "<yellow>/event accept &lt;id&gt; &lt;start&gt; &lt;ende&gt;</yellow><gray> - Event annehmen (Format: ${DateTimeParser.FORMAT_HINT})</gray>"
            lines += "<yellow>/event reject &lt;id&gt;</yellow><gray> - Event ablehnen</gray>"
            lines += "<yellow>/event reward &lt;spieler&gt; &lt;anzahl&gt;</yellow><gray> - Diamanten an Spieler vergeben</gray>"
        }
        player.sendMessage(mm(lines.joinToString("\n")))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            val subs = mutableListOf("create", "resume", "submit", "cancel", "join", "leave", "list")
            if (sender.hasPermission("diawars.admin")) {
                subs += listOf("review", "accept", "reject", "reward")
            }
            return subs.filter { it.startsWith(args[0].lowercase()) }
        }

        if (args.size == 2) {
            when (args[0].lowercase()) {
                "join" -> return plugin.eventManager.listByState(EventState.ACTIVE).map { it.id }
                    .filter { it.startsWith(args[1].lowercase()) }

                "review", "accept", "reject" -> {
                    if (!sender.hasPermission("diawars.admin")) return emptyList()
                    return plugin.eventManager.listByState(EventState.SUBMITTED).map { it.id }
                        .filter { it.startsWith(args[1].lowercase()) }
                }

                "reward" -> {
                    if (!sender.hasPermission("diawars.admin")) return emptyList()
                    return plugin.server.onlinePlayers.map { it.name }
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                }

                "list" -> {
                    val options = mutableListOf("accepted", "active")
                    if (sender.hasPermission("diawars.admin")) options += "pending"
                    return options.filter { it.startsWith(args[1].lowercase()) }
                }
            }
        }

        return emptyList()
    }
}