package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.EventManager
import de.davidsw.diawars.stores.EventConfig
import de.davidsw.diawars.stores.EventGameRules
import de.davidsw.diawars.stores.EventPotionEffect
import de.davidsw.diawars.stores.EventState
import de.davidsw.diawars.util.DateTimeParser
import de.davidsw.diawars.util.MiniMessageHelper.mm
import de.davidsw.diawars.util.PotionEffectParser
import org.bukkit.Bukkit.getOfflinePlayer
import org.bukkit.GameMode
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

            "config" -> handleConfig(sender, args)

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
                respond(sender, plugin.eventManager.acceptEvent(args[1], startEpoch, endEpoch))
            }

            "reject" -> {
                if (!requireAdmin(sender)) return true
                if (args.size < 3) {
                    sender.sendMessage(mm("<red>Verwendung: /event reject &lt;id&gt; &lt;grund&gt;</red>"))
                    return true
                }
                respond(sender, plugin.eventManager.rejectEvent(args[1], args.slice(2 until args.size).joinToString(" ")))
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
                if ((!target.hasPlayedBefore() && !target.isOnline)) {
                    sender.sendMessage(mm("<red>Dieser Spieler ist unbekannt!</red>"))
                    return true
                }

                plugin.rewardManager.grantDiamondReward(target.uniqueId, amount)

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
            val timeInfo = if (event.startTime > 0) {
                " <dark_gray>(${DateTimeParser.parseToString(event.startTime)} - ${DateTimeParser.parseToString(event.endTime)})</dark_gray>"
            } else ""
            lines += "<gray>- <yellow>${event.id}</yellow> <white>${event.name}</white>$timeInfo</gray>"
        }
        sender.sendMessage(mm(lines.joinToString("\n")))
    }

    private fun requireAdmin(sender: Player): Boolean {
        if (!sender.hasPermission("diawars.admin")) {
            sender.sendMessage("No Permission!")
            return false
        }
        return true
    }

    private fun handleConfig(player: Player, args: Array<out String>) {
        val session = plugin.eventManager.getSession(player.uniqueId)
        if (session == null || session.mode != EventManager.SessionMode.BUILD) {
            player.sendMessage(mm("<red>Du kannst die Event-Konfiguration nur während des Bauens an deinem eigenen Event ändern!</red>"))
            return
        }

        val eventId = session.eventId
        val configStore = plugin.store.eventConfigStore

        if (args.size < 2) {
            sendConfigHelp(player)
            return
        }

        when (args[1].lowercase()) {
            "info" -> sendConfigInfo(player, configStore.getConfig(eventId))

            "gamemode" -> {
                val mode = args.getOrNull(2)?.uppercase()?.let { runCatching { GameMode.valueOf(it) }.getOrNull() }
                if (mode == null) {
                    player.sendMessage(mm("<red>Verwendung: /event config gamemode &lt;survival|creative|adventure|spectator&gt;</red>"))
                    return
                }
                configStore.update(eventId) { it.copy(gameMode = mode) }
                player.sendMessage(mm("<green>✓ Spielmodus für Beitretende wurde auf <gold>${mode.name}</gold> gesetzt!</green>"))
            }

            "time" -> {
                when (args.getOrNull(2)?.lowercase()) {
                    "advance" -> {
                        configStore.update(eventId) { it.copy(advanceTime = true) }
                        player.sendMessage(mm("<green>✓ Die Tageszeit läuft jetzt normal weiter.</green>"))
                    }
                    "fixed" -> {
                        val ticks = args.getOrNull(3)?.toLongOrNull()
                        if (ticks == null || ticks !in 0..24000) {
                            player.sendMessage(mm("<red>Verwendung: /event config time fixed &lt;0-24000&gt;</red>"))
                            return
                        }
                        configStore.update(eventId) { it.copy(advanceTime = false, fixedTime = ticks) }
                        player.sendMessage(mm("<green>✓ Die Tageszeit wurde fixiert auf <gold>$ticks</gold> Ticks.</green>"))
                    }
                    else -> player.sendMessage(mm("<red>Verwendung: /event config time &lt;advance|fixed&gt; [ticks]</red>"))
                }
            }

            "gamerule" -> {
                val key = args.getOrNull(2)?.lowercase()
                if (key == null || key !in EventGameRules.CONFIGURABLE.keys) {
                    player.sendMessage(mm("<red>Verwendung: /event config gamerule &lt;${EventGameRules.CONFIGURABLE.keys.joinToString("|")}&gt; &lt;on|off&gt;</red>"))
                    return
                }
                val boolValue = when (args.getOrNull(3)?.lowercase()) {
                    "on", "true" -> true
                    "off", "false" -> false
                    else -> {
                        player.sendMessage(mm("<red>Verwendung: /event config gamerule $key &lt;on|off&gt;</red>"))
                        return
                    }
                }
                configStore.update(eventId) { it.copy(gameRules = it.gameRules + (key to boolValue)) }
                player.sendMessage(mm("<green>✓ Gamerule <gold>$key</gold> wurde auf <gold>${if (boolValue) "an" else "aus"}</gold> gesetzt!</green>"))
            }

            "effect" -> handleEffectConfig(player, eventId, args)

            "inventory" -> handleInventoryConfig(player, eventId, args)

            else -> sendConfigHelp(player)
        }
    }

    private fun handleEffectConfig(player: Player, eventId: String, args: Array<out String>) {
        val configStore = plugin.store.eventConfigStore
        when (args.getOrNull(2)?.lowercase()) {
            "add" -> {
                val type = args.getOrNull(3)?.let { PotionEffectParser.parse(it) }
                if (type == null) {
                    player.sendMessage(mm("<red>Verwendung: /event config effect add &lt;effekt&gt; [level]</red>"))
                    return
                }
                val amplifier = ((args.getOrNull(4)?.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
                configStore.update(eventId) { config ->
                    val filtered = config.effects.filterNot { it.type == PotionEffectParser.name(type) }
                    config.copy(effects = filtered + EventPotionEffect(PotionEffectParser.name(type), amplifier))
                }
                player.sendMessage(mm("<green>✓ Effekt <gold>${PotionEffectParser.name(type)} ${amplifier + 1}</gold> hinzugefügt!</green>"))
            }
            "remove" -> {
                val type = args.getOrNull(3)?.let { PotionEffectParser.parse(it) }
                if (type == null) {
                    player.sendMessage(mm("<red>Verwendung: /event config effect remove &lt;effekt&gt;</red>"))
                    return
                }
                configStore.update(eventId) { config -> config.copy(effects = config.effects.filterNot { it.type == PotionEffectParser.name(type) }) }
                player.sendMessage(mm("<green>✓ Effekt <gold>${PotionEffectParser.name(type)}</gold> entfernt!</green>"))
            }
            "list" -> {
                val effects = configStore.getConfig(eventId).effects
                if (effects.isEmpty()) {
                    player.sendMessage(mm("<gray>Keine Effekte konfiguriert.</gray>"))
                    return
                }
                val lines = mutableListOf("<gold>=== Konfigurierte Effekte ===</gold>")
                effects.forEach { lines += "<gray>- <white>${it.type}</white> <gray>Level</gray> <white>${it.amplifier + 1}</white>" }
                player.sendMessage(mm(lines.joinToString("\n")))
            }
            else -> player.sendMessage(mm("<red>Verwendung: /event config effect &lt;add|remove|list&gt;</red>"))
        }
    }

    private fun handleInventoryConfig(player: Player, eventId: String, args: Array<out String>) {
        val configStore = plugin.store.eventConfigStore
        when (args.getOrNull(2)?.lowercase()) {
            "set" -> {
                configStore.update(eventId) {
                    it.copy(
                        startingInventory = player.inventory.storageContents.toList(),
                        startingArmor = player.inventory.armorContents.toList(),
                        startingOffHand = player.inventory.itemInOffHand.clone(),
                    )
                }
                player.sendMessage(mm("<green>✓ Dein aktuelles Inventar wurde als Start-Inventar für neue Beitretende gespeichert!</green>"))
            }
            "clear" -> {
                configStore.update(eventId) { it.copy(startingInventory = emptyList(), startingArmor = emptyList(), startingOffHand = null) }
                player.sendMessage(mm("<green>✓ Start-Inventar wurde zurückgesetzt (leer).</green>"))
            }
            else -> player.sendMessage(mm("<red>Verwendung: /event config inventory &lt;set|clear&gt;</red>"))
        }
    }

    private fun sendConfigHelp(player: Player) {
        player.sendMessage(mm("""
            <gold>=== Event-Konfiguration ===</gold>
            <yellow>/event config info</yellow><gray> - Aktuelle Konfiguration anzeigen</gray>
            <yellow>/event config gamemode <modus></yellow><gray> - Spielmodus für Beitretende</gray>
            <yellow>/event config time advance</yellow><gray> - Tageszeit läuft normal</gray>
            <yellow>/event config time fixed <ticks></yellow><gray> - Tageszeit fixieren</gray>
            <yellow>/event config gamerule <regel> <on|off></yellow><gray> - Gamerule setzen</gray>
            <yellow>/event config effect add <effekt> [level]</yellow><gray> - Effekt hinzufügen</gray>
            <yellow>/event config effect remove <effekt></yellow><gray> - Effekt entfernen</gray>
            <yellow>/event config effect list</yellow><gray> - Konfigurierte Effekte anzeigen</gray>
            <yellow>/event config inventory set</yellow><gray> - Aktuelles Inventar als Start-Inventar speichern</gray>
            <yellow>/event config inventory clear</yellow><gray> - Start-Inventar zurücksetzen</gray>
        """.trimIndent()))
    }

    private fun sendConfigInfo(player: Player, config: EventConfig) {
        val lines = mutableListOf(
            "<gold>=== Event-Konfiguration ===</gold>",
            "<gray>Spielmodus: <white>${config.gameMode.name}</white></gray>",
            "<gray>Tageszeit: <white>${if (config.advanceTime) "Läuft normal" else "Fixiert auf ${config.fixedTime}"}</white></gray>",
        )
        EventGameRules.CONFIGURABLE.keys.forEach { key ->
            val value = config.gameRules[key] ?: EventGameRules.DEFAULTS[key] ?: true
            lines += "<gray>Gamerule <white>$key</white>: ${if (value) "<green>an</green>" else "<red>aus</red>"}</gray>"
        }
        lines += "<gray>Effekte: <white>${if (config.effects.isEmpty()) "Keine" else config.effects.joinToString(", ") { "${it.type} ${it.amplifier + 1}" }}</white></gray>"
        lines += "<gray>Start-Inventar: <white>${if (config.startingInventory.any { it != null }) "Konfiguriert" else "Standard (leer)"}</white></gray>"
        player.sendMessage(mm(lines.joinToString("\n")))
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
            "<yellow>/event create <name></yellow><gray> - Neues Event erstellen</gray>",
            "<yellow>/event config <option></yellow><gray> - Event während des Bauens konfigurieren</gray>",
            "<yellow>/event resume</yellow><gray> - Weiterbauen an deinem Event</gray>",
            "<yellow>/event submit</yellow><gray> - Event zur Prüfung einreichen</gray>",
            "<yellow>/event cancel</yellow><gray> - Event abbrechen und löschen</gray>",
            "<yellow>/event join <id></yellow><gray> - Aktivem Event beitreten</gray>",
            "<yellow>/event leave</yellow><gray> - Event verlassen</gray>",
            "<yellow>/event list <pending|accepted|active></yellow><gray> - Events auflisten</gray>",
        )
        if (player.hasPermission("diawars.admin")) {
            lines += "<yellow>/event review <id></yellow><gray> - Eingereichtes Event prüfen</gray>"
            lines += "<yellow>/event accept <id> <start> <ende></yellow><gray> - Event annehmen (Format: ${DateTimeParser.FORMAT_HINT})</gray>"
            lines += "<yellow>/event reject <id> <grund></yellow><gray> - Event ablehnen</gray>"
            lines += "<yellow>/event reward <spieler> <anzahl></yellow><gray> - Diamanten an Spieler vergeben</gray>"
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
            val subs = mutableListOf("create", "resume", "submit", "cancel", "join", "leave", "list", "config")
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

                "config" -> return listOf("info", "gamemode", "time", "gamerule", "effect", "inventory")
                    .filter { it.startsWith(args[1].lowercase()) }
            }
        }

        if (args.size == 3 && args[0].equals("config", true)) {
            return when (args[1].lowercase()) {
                "gamemode" -> listOf("survival", "creative", "adventure", "spectator").filter { it.startsWith(args[2].lowercase()) }
                "time" -> listOf("advance", "fixed").filter { it.startsWith(args[2].lowercase()) }
                "gamerule" -> EventGameRules.CONFIGURABLE.keys.filter { it.startsWith(args[2].lowercase()) }
                "effect" -> listOf("add", "remove", "list").filter { it.startsWith(args[2].lowercase()) }
                "inventory" -> listOf("set", "clear").filter { it.startsWith(args[2].lowercase()) }
                else -> emptyList()
            }
        }

        if (args.size == 4 && args[0].equals("config", true) && args[1].equals("gamerule", true)) {
            return listOf("on", "off").filter { it.startsWith(args[3].lowercase()) }
        }

        return emptyList()
    }
}