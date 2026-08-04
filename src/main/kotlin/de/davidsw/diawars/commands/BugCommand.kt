package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.BugManager
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class BugCommand(private val plugin: Diawars): CommandExecutor, TabCompleter {
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
            "report" -> {
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /bug report &lt;beschreibung&gt;</red>"))
                    return true
                }
                respond(sender, plugin.bugManager.reportBug(sender, args.slice(1 until args.size).joinToString(" ")))
            }

            "list" -> {
                if (!requireAdmin(sender)) return true
                plugin.store.bugStore.markRead(sender.uniqueId)
                sender.sendMessage(mm(plugin.bugManager.formatList().joinToString("\n")))
            }

            "resolve", "fix" -> {
                if (!requireAdmin(sender)) return true
                if (args.size < 2) {
                    sender.sendMessage(mm("<red>Verwendung: /bug resolve &lt;id&gt;</red>"))
                    return true
                }
                respond(sender, plugin.bugManager.resolveBug(args[1]))
            }

            else -> sendHelp(sender)
        }

        return true
    }

    private fun requireAdmin(sender: Player): Boolean {
        if (!sender.hasPermission("diawars.admin")) {
            sender.sendMessage(mm("<red>Du hast keine Berechtigung für diesen Befehl!</red>"))
            return false
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
        val lines = mutableListOf(
            "<gold>=== Bug-Befehle ===</gold>",
            "<yellow>/bug report <beschreibung></yellow><gray> - Einen Fehler melden</gray>",
        )
        if (player.hasPermission("diawars.admin")) {
            lines += "<yellow>/bug list</yellow><gray> - Offene Bugs auflisten</gray>"
            lines += "<yellow>/bug resolve <id></yellow><gray> - Bug als behoben markieren</gray>"
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
            val subs = mutableListOf("report")
            if (sender.hasPermission("diawars.admin")) {
                subs += listOf("list", "resolve")
            }
            return subs.filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && args[0].equals("resolve", ignoreCase = true) && sender.hasPermission("diawars.admin")) {
            return plugin.store.bugStore.getUnresolved().map { it.id }
                .filter { it.startsWith(args[1]) }
        }
        return emptyList()
    }
}