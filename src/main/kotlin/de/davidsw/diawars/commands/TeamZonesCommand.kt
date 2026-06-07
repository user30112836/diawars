package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.BorderDensity
import de.davidsw.diawars.util.ColorParser
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
            sender.sendMessage("§6 Plugin v1.0.0")
            sender.sendMessage("§7Verwendung: /teamzones <reload|info|border>")
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
                sender.sendMessage("§6=== TeamZones Info ===")
                sender.sendMessage("§7Zonenteilung: X = 0")
                sender.sendMessage("§7Team A Zone: X > 0 (Westen)")
                sender.sendMessage("§7Team B Zone: X < 0 (Osten)")

                if (sender is Player) {
                    val team = plugin.teamManager.getPlayerTeam(sender.uniqueId)
                    if (team != null) {
                        sender.sendMessage("§7Dein Team: §6${team.displayName}")
                        val inOwnZone = plugin.zoneManager.isInOwnZone(sender)
                        sender.sendMessage("§7In eigener Zone: ${if (inOwnZone) "§aJa" else "§cNein"}")
                    } else {
                        sender.sendMessage("§7Du bist in keinem Team")
                    }

                    val pref = plugin.borderManager.preferences.getPreference(sender.uniqueId)
                    sender.sendMessage("§7Border: ${if (pref.enabled) "§aAktiviert" else "§cDeaktiviert"}")
                }
            }

            "border" -> {
                if (sender !is Player) {
                    sender.sendMessage("§cDieser Befehl kann nur von Spielern ausgeführt werden!")
                    return true
                }

                handleBorderCommand(sender, args)
            }

            else -> {
                sender.sendMessage("§cUnbekannter Befehl. Verwendung: /teamzones <reload|info>")
            }
        }

        return true
    }

    private fun handleBorderCommand(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("§6=== Border Einstellungen ===")
            player.sendMessage("§7/teamzones border <on|off> - Border aktivieren/deaktivieren")
            player.sendMessage("§7/teamzones border type <typ> - Partikel-Typ ändern")
            player.sendMessage("§7/teamzones border color <farbe> - Farbe ändern")
            player.sendMessage("§7/teamzones border distance <zahl> - Sichtweite ändern")
            player.sendMessage("§7/teamzones border reset - Auf Standard zurücksetzen")
            player.sendMessage("§7/teamzones border info - Aktuelle Einstellungen")
            return
        }

        val prefs = plugin.borderManager.preferences

        when (args[1].lowercase()) {
            "on", "enable" -> {
                prefs.setEnabled(player.uniqueId, true)
                player.sendMessage("§a✓ Border wurde aktiviert!")
            }

            "off", "disable" -> {
                prefs.setEnabled(player.uniqueId, false)
                player.sendMessage("§c✗ Border wurde deaktiviert!")
            }

            "type", "particle" -> {
                if (args.size < 3) {
                    player.sendMessage("§cVerwendung: /teamzones border type <REDSTONE|FLAME|GLOW|END_ROD|BARRIER|SOUL|ENCHANT|PORTAL|HEART>")
                    return
                }

                val type = args[2].uppercase()
                if (ParticleParser.parse(type) != null) {
                    prefs.setParticleType(player.uniqueId, type)
                    player.sendMessage("§a✓ Partikel-Typ wurde auf §6$type §agesetzt!")
                } else {
                    player.sendMessage("§cUngültiger Partikel-Typ! Verfügbar: FLAME, GLOW, END_ROD, SOUL, ENCHANT, PORTAL, HEART, DUST")
                }
            }

            "color", "colour" -> {
                if (args.size < 3) {
                    player.sendMessage("§cVerwendung: /teamzones border color <RED|BLUE|GREEN|YELLOW|ORANGE|PURPLE|WHITE|AQUA|R,G,B>")
                    return
                }

                val colorStr = args.slice(2 until args.size).joinToString(" ")
                val color = ColorParser.parse(colorStr)

                if (color != null) {
                    prefs.setColor(player.uniqueId, color)
                    player.sendMessage("§a✓ Border-Farbe wurde geändert!")
                } else {
                    player.sendMessage("§cUngültige Farbe! Verwende z.B. RED, BLUE oder RGB wie: 255,100,0")
                }
            }

            "distance", "render" -> {
                if (args.size < 3) {
                    player.sendMessage("§cVerwendung: /teamzones border distance <8-64>")
                    return
                }

                val distance = args[2].toIntOrNull()
                if (distance != null && distance in 8..64) {
                    prefs.setRenderDistance(player.uniqueId, distance)
                    player.sendMessage("§a✓ Sichtweite wurde auf §6$distance Blöcke §agesetzt!")
                } else {
                    player.sendMessage("§cUngültige Distanz! Verwende eine Zahl zwischen 8 und 64")
                }
            }

            "density" -> {
                if (args.size < 3) {
                    player.sendMessage("§cVerwendung: /teamzones border density <1-8|horizontal|vertical> [<1-8>]")
                    player.sendMessage("§81 = sehr dicht, 8 = sehr dünn")
                    return
                }
                if (args.size == 3) {
                    val value = args[2].toIntOrNull()
                    if (value == null || value !in 1..8) {
                        player.sendMessage("§cUngültiger Wert! Verwende eine Zahl zwischen 1 und 8")
                        return
                    }
                    prefs.setDensity(player.uniqueId, BorderDensity(value, value))
                    player.sendMessage("§a✓ Dichte auf §6$value §agesetzt!")
                    return
                }
                val value = args[3].toIntOrNull()
                if (value == null || value !in 1..8) {
                    player.sendMessage("§cUngültiger Wert! Verwende eine Zahl zwischen 1 und 8")
                    return
                }
                when (args[2].lowercase()) {
                    "horizontal", "h" -> {
                        prefs.setHorizontalDensity(player.uniqueId, value)
                        player.sendMessage("§a✓ Horizontale Dichte auf §6$value §agesetzt!")
                    }
                    "vertical", "v" -> {
                        prefs.setVerticalDensity(player.uniqueId, value)
                        player.sendMessage("§a✓ Vertikale Dichte auf §6$value §agesetzt!")
                    }
                    else -> player.sendMessage("§cUnbekannte Achse! Verwende §6horizontal §coder §6vertical")
                }
            }

            "amount", "count" -> {
                if (args.size < 3) {
                    player.sendMessage("§cVerwendung: /teamzones border amount <1-10>")
                    return
                }
                val amount = args[2].toIntOrNull()
                if (amount != null && amount in 1..10) {
                    prefs.setAmount(player.uniqueId, amount)
                    player.sendMessage("§a✓ Partikel-Menge wurde auf §6$amount §agesetzt!")
                } else {
                    player.sendMessage("§cUngültiger Wert! Verwende eine Zahl zwischen 1 und 10")
                }
            }

            "reset", "default" -> {
                prefs.resetToDefault(player.uniqueId)
                player.sendMessage("§a✓ Border-Einstellungen wurden auf Standard zurückgesetzt!")
            }

            "info", "status" -> {
                val pref = prefs.getPreference(player.uniqueId)
                player.sendMessage("§6=== Deine Border-Einstellungen ===")
                player.sendMessage("§7Status: ${if (pref.enabled) "§aAktiviert" else "§cDeaktiviert"}")
                player.sendMessage("§7Partikel-Typ: §6${pref.particleType}")
                player.sendMessage("§7Farbe: §6${pref.color.red},${pref.color.green},${pref.color.blue}")
                player.sendMessage("§7Sichtweite: §6${pref.renderDistance} Blöcke")
                player.sendMessage("§7Dichte horizontal: §6${pref.density.horizontal} §8(1=dicht, 8=dünn)")
                player.sendMessage("§7Dichte vertikal: §6${pref.density.vertical} §8(1=dicht, 8=dünn)")
            }

            else -> {
                player.sendMessage("§cUnbekannte Option! Verwende: /teamzones border")
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