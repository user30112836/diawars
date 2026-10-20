package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.ReportManager
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Player reports: {@code /report image} (map in main hand),
 * {@code /report player <name>}, the mod-dialog reason
 * ({@code /report reason <text>}) and the optional screenshot attachment
 * ({@code /report shot}). Admin review lives in {@code /admin report ...}.
 */
class ReportCommand(private val plugin: Diawars) : CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            null -> sendHelp(sender)

            "image" -> {
                if (args.size > 1) {
                    sendHelp(sender)
                    return true
                }
                respond(sender, plugin.reportManager.startImageReport(sender))
            }

            "player" -> {
                val target = args.getOrNull(1)
                if (target == null || args.size > 2) {
                    sender.sendMessage(mm("<red>Verwendung: /report player &lt;name&gt;</red>"))
                    return true
                }
                respond(sender, plugin.reportManager.startPlayerReport(sender, target))
            }

            "reason" -> {
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /report reason &lt;grund&gt;</red>"))
                    return true
                }
                respond(sender, plugin.reportManager.submitReason(sender, args.slice(1 until args.size).joinToString(" ")))
            }

            "shot" -> {
                if (args.size > 1) {
                    sendHelp(sender)
                    return true
                }
                respond(sender, plugin.reportManager.requestShot(sender))
            }

            else -> sendHelp(sender)
        }
        return true
    }

    private fun respond(player: Player, result: ReportManager.Result) {
        when (result) {
            is ReportManager.Result.Success -> player.sendMessage(mm(result.message))
            is ReportManager.Result.Error -> player.sendMessage(mm(result.message))
        }
    }

    private fun sendHelp(player: Player) {
        player.sendMessage(
            mm(
                """
                <gold>=== Report-Befehle ===</gold>
                <yellow>/report image</yellow><gray> - Das Bild der Karte in deiner Haupthand melden</gray>
                <yellow>/report player &lt;name&gt;</yellow><gray> - Einen Spieler melden</gray>
                <yellow>/report shot</yellow><gray> - Screenshot deines Bildschirms an den letzten Report anhängen</gray>
                """.trimIndent(),
            ),
        )
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (args.size == 1) {
            return listOf("image", "player", "reason", "shot").filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && args[0].equals("player", ignoreCase = true)) {
            return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
        }
        return emptyList()
    }
}
