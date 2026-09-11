package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.BugManager
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class BugCommand(private val plugin: Diawars) : CommandExecutor, TabCompleter {
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

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "report" -> {
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /bug report &lt;beschreibung&gt;</red>"))
                    return true
                }
                respond(sender, plugin.bugManager.reportBug(sender, args.slice(1 until args.size).joinToString(" ")))
            }

            else -> sendHelp(sender)
        }

        return true
    }

    private fun respond(player: Player, result: BugManager.Result) {
        when (result) {
            is BugManager.Result.Success -> player.sendMessage(mm(result.message))
            is BugManager.Result.Error -> player.sendMessage(mm(result.message))
        }
    }

    private fun sendHelp(player: Player) {
        player.sendMessage(
            mm(
                """
                <gold>=== Bug-Befehle ===</gold>
                <yellow>/bug report <beschreibung></yellow><gray> - Einen Fehler melden</gray>
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
            return listOf("report").filter { it.startsWith(args[0].lowercase()) }
        }
        return emptyList()
    }
}
