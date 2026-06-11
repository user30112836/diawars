package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.stores.BorderDensity
import de.davidsw.diawars.util.ColorParser
import de.davidsw.diawars.util.MiniMessageHelper.mm
import de.davidsw.diawars.util.ParticleParser
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class TeamZonesCommand(private val plugin: Diawars): CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(mm("<gray>Verwendung: /teamzones &lt;reload|info|border&gt;</gray>"))
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> {
                if (!sender.hasPermission("teamzones.admin")) {
                    if (sender is Player) {
                        plugin.messageManager.sendNoPermission(sender)
                    } else {
                        sender.sendMessage("No Permission!")
                    }
                    return true
                }

                plugin.reloadPlugin()

                sender.sendMessage("Plugin got reloaded")
            }

            "info" -> {
                val lines = mutableListOf(
                    "<gold>=== TeamZones Info ===</gold>",
                    "<gray>Zonenteilung: X = 0</gray>",
                    "<gray>Team A Zone: X > 0 (Westen)</gray>",
                    "<gray>Team B Zone: X < 0 (Osten)</gray>"
                )

                if (sender is Player) {
                    val team = plugin.teamManager.getPlayerTeam(sender.uniqueId)

                    lines += ""

                    if (team != null) {
                        val inOwnZone = plugin.zoneManager.isInOwnZone(sender)

                        lines += "<gray>Dein Team: <gold>${team.displayName}</gold></gray>"
                        lines += if (inOwnZone) {
                            "<gray>In eigener Zone: <green>Ja</green></gray>"
                        } else {
                            "<gray>In eigener Zone: <red>Nein</red></gray>"
                        }
                    } else {
                        lines += "<gray>Du bist in keinem Team</gray>"
                    }

                    val pref = plugin.store.borderPreferencesStore
                        .getPreference(sender.uniqueId)

                    lines += if (pref.enabled) {
                        "<gray>Border: <green>Aktiviert</green></gray>"
                    } else {
                        "<gray>Border: <red>Deaktiviert</red></gray>"
                    }
                }

                sender.sendMessage(
                    mm(lines.joinToString("\n"))
                )
            }

            "border" -> {
                if (sender !is Player) {
                    sender.sendMessage(mm("<red>Dieser Befehl kann nur von Spielern ausgeführt werden!</red>"))
                    return true
                }

                handleBorderCommand(sender, args)
            }

            else -> {
                sender.sendMessage(mm("<red>Unbekannter Befehl. Verwendung: /teamzones &lt;reload|info&gt;</red>"))
            }
        }

        return true
    }

    private fun handleBorderCommand(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            val message = mm("""
                <gold>=== Border Einstellungen ===</gold>
                <gray>/teamzones border &lt;on|off&gt; - Border aktivieren/deaktivieren</gray>
                <gray>/teamzones border type &lt;typ&gt; - Partikel-Typ ändern</gray>
                <gray>/teamzones border color &lt;farbe&gt; - Farbe ändern</gray>
                <gray>/teamzones border distance &lt;zahl&gt; - Sichtweite ändern</gray>
                <gray>/teamzones border reset - Auf Standard zurücksetzen</gray>
                <gray>/teamzones border info - Aktuelle Einstellungen</gray>
            """.trimIndent())
            player.sendMessage(message)
            return
        }

        val prefs = plugin.store.borderPreferencesStore

        when (args[1].lowercase()) {
            "on", "enable" -> {
                prefs.setEnabled(player.uniqueId, true)
                player.sendMessage(mm("<green>✓ Border wurde aktiviert!</green>"))
            }

            "off", "disable" -> {
                prefs.setEnabled(player.uniqueId, false)
                player.sendMessage(mm("<red>✗ Border wurde deaktiviert!</red>"))
            }

            "type", "particle" -> {
                if (args.size < 3) {
                    player.sendMessage(mm("<red>Verwendung: /teamzones border type &lt;REDSTONE|FLAME|GLOW|END_ROD|BARRIER|SOUL|ENCHANT|PORTAL|HEART&gt;</red>"))
                    return
                }

                val type = args[2].uppercase()
                if (ParticleParser.parse(type) != null) {
                    prefs.setParticleType(player.uniqueId, type)
                    player.sendMessage(mm("<green>✓ Partikel-Typ wurde auf <gold>$type</gold> gesetzt!</green>"))
                } else {
                    player.sendMessage(mm("<red>Ungültiger Partikel-Typ! Verfügbar: FLAME, GLOW, END_ROD, SOUL, ENCHANT, PORTAL, HEART, DUST</red>"))
                }
            }

            "color", "colour" -> {
                if (args.size < 3) {
                    player.sendMessage(mm("<red>Verwendung: /teamzones border color &lt;RED|BLUE|GREEN|YELLOW|ORANGE|PURPLE|WHITE|AQUA|R,G,B&gt;</red>"))
                    return
                }

                val colorStr = args.slice(2 until args.size).joinToString(" ")
                val color = ColorParser.parse(colorStr)

                if (color != null) {
                    prefs.setColor(player.uniqueId, color)
                    player.sendMessage(mm("<green>✓ Border-Farbe wurde geändert!</green>"))
                } else {
                    player.sendMessage(mm("<red>Ungültige Farbe! Verwende z.B. RED, BLUE oder RGB wie: 255,100,0</red>"))
                }
            }

            "distance", "render" -> {
                if (args.size < 3) {
                    player.sendMessage(mm("<red>Verwendung: /teamzones border distance &lt;8-64&gt;</red>"))
                    return
                }

                val distance = args[2].toIntOrNull()
                if (distance != null && distance in 8..64) {
                    prefs.setRenderDistance(player.uniqueId, distance)
                    player.sendMessage(mm("<green>✓ Sichtweite wurde auf <gold>$distance</gold> Blöcke gesetzt!</green>"))
                } else {
                    player.sendMessage(mm("<red>Ungültige Distanz! Verwende eine Zahl zwischen 8 und 64</red>"))
                }
            }

            "density" -> {
                if (args.size < 3) {
                    player.sendMessage(mm("<red>Verwendung: /teamzones border density &lt;1-8|horizontal|vertical&gt; [&lt;1-8&gt;]"))
                    player.sendMessage(mm("<dark_gray>1 = sehr dicht, 8 = sehr dünn</dark_gray>"))
                    return
                }
                if (args.size == 3) {
                    val value = args[2].toIntOrNull()
                    if (value == null || value !in 1..8) {
                        player.sendMessage(mm("<red>Ungültiger Wert! Verwende eine Zahl zwischen 1 und 8</red>"))
                        return
                    }
                    prefs.setDensity(player.uniqueId, BorderDensity(value, value))
                    player.sendMessage(mm("<green>✓ Dichte auf <gold>$value</gold> gesetzt!</green>"))
                    return
                }
                val value = args[3].toIntOrNull()
                if (value == null || value !in 1..8) {
                    player.sendMessage(mm("<red>Ungültiger Wert! Verwende eine Zahl zwischen 1 und 8</red>"))
                    return
                }
                when (args[2].lowercase()) {
                    "horizontal", "h" -> {
                        prefs.setHorizontalDensity(player.uniqueId, value)
                        player.sendMessage(mm("<green>✓ Horizontale Dichte auf <gold>$value</gold> gesetzt!</green>"))
                    }
                    "vertical", "v" -> {
                        prefs.setVerticalDensity(player.uniqueId, value)
                        player.sendMessage(mm("<green>✓ Vertikale Dichte auf <gold>$value</gold> gesetzt!</green>"))
                    }
                    else -> player.sendMessage(mm("<red>Unbekannte Achse! Verwende <gold>horizontal</gold> oder <gold>vertical</gold></red>"))
                }
            }

            "amount", "count" -> {
                if (args.size < 3) {
                    player.sendMessage(mm("<red>Verwendung: /teamzones border amount &lt;1-10&gt;</red>"))
                    return
                }
                val amount = args[2].toIntOrNull()
                if (amount != null && amount in 1..10) {
                    prefs.setAmount(player.uniqueId, amount)
                    player.sendMessage(mm("<green>✓ Partikel-Menge wurde auf <gold>$amount</gold> gesetzt!</green>"))
                } else {
                    player.sendMessage(mm("<red>Ungültiger Wert! Verwende eine Zahl zwischen 1 und 10</red>"))
                }
            }

            "reset", "default" -> {
                prefs.resetToDefault(player.uniqueId)
                player.sendMessage(mm("<green>✓ Border-Einstellungen wurden auf Standard zurückgesetzt!</green>"))
            }

            "info", "status" -> {
                val pref = prefs.getPreference(player.uniqueId)
                val message = mm("""
                    <gold>=== Deine Border-Einstellungen ===</gold>
                    <gray>Status: ${if (pref.enabled) "<green>Aktiviert</green>" else "<red>Deaktiviert</red>"}</gray>
                    <gray>Partikel-Typ: <gold>${pref.particleType}</gold></gray>
                    <gray>Farbe: <gold>${pref.color.red},${pref.color.green},${pref.color.blue}</gold></gray>
                    <gray>Sichtweite: <gold>${pref.renderDistance} Blöcke</gold></gray>
                    <gray>Dichte horizontal: <gold>${pref.density.horizontal}</gold> <dark_gray>(1=dicht, 8=dünn)</dark_gray></gray>
                    <gray>Dichte vertikal: <gold>${pref.density.vertical}</gold> <dark_gray>(1=dicht, 8=dünn)</dark_gray></gray>
                """.trimIndent())
                player.sendMessage(message)
            }

            else -> {
                player.sendMessage(mm("<red>Unbekannte Option! Verwende: /teamzones border</red>"))
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String?> {
        if (args.size == 1) {
            val subcommands = mutableListOf("info", "border")
            if (sender.hasPermission("teamzones.admin")) {
                subcommands.add("reload")
            }
            return subcommands.filter { it.startsWith(args[0].lowercase()) }
        }

        if (args.size == 2 && args[0].lowercase() == "border") {
            return listOf("on", "off", "type", "color", "distance", "density", "amount", "reset", "info")
                .filter { it.startsWith(args[1].lowercase()) }
        }

        if (args.size == 3 && args[0].lowercase() == "border") {
            when (args[1].lowercase()) {
                "type", "particle" -> {
                    return listOf("FLAME", "GLOW", "END_ROD", "SOUL", "ENCHANT", "PORTAL", "HEART", "DUST")
                        .filter { it.startsWith(args[2].uppercase()) }
                }
                "color", "colour" -> {
                    return listOf("RED", "BLUE", "GREEN", "YELLOW", "ORANGE", "PURPLE", "WHITE", "AQUA")
                        .filter { it.startsWith(args[2].uppercase()) }
                }
                "distance", "render" -> {
                    return listOf("8", "16", "24", "32", "48", "64")
                        .filter { it.startsWith(args[2]) }
                }
                "density" -> {
                    return listOf("horizontal", "vertical", "1", "2", "3", "4", "5", "6", "7", "8")
                        .filter { it.startsWith(args[2].lowercase()) }
                }
                "amount", "count" -> {
                    return listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
                       .filter { it.startsWith(args[2]) }
                }
            }
        }

        if (args.size == 4 && args[0].lowercase() == "border" && args[1].lowercase() == "density") {
            return listOf("1", "2", "3", "4", "5", "6", "7", "8")
                .filter { it.startsWith(args[3]) }
        }

        return emptyList()
    }
}