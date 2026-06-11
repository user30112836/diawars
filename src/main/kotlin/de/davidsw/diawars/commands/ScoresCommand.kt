package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ScoresCommand(private val plugin: Diawars): CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return false

        if (args.isEmpty()) {
            sender.sendMessage(mm("<gray>Verwendung: /scores &lt;info&gt;</gray>"))
            return true
        }

        when (args[0]) {
            "info" -> plugin.scoresManager.handleInfo(sender)
            else -> {
                sender.sendMessage(mm("<red>Unbekannter Befehl! Verwendung: /scores &lt;info&gt;</red>"))
            }
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String?> {
        if (args.size == 1) {
            return listOf("info")
                .filter { it.startsWith(args[0].lowercase()) }
        }

        return emptyList()
    }
}