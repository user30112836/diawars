package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class LogCommand(private val plugin: Diawars): CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("diawars.admin")) {
            sender.sendMessage(mm("<red>Du hast keine Berechtigung für diesen Befehl!</red>"))
            return true
        }

        val limit = args.getOrNull(0)?.toIntOrNull()?.coerceIn(1, 100) ?: 15
        val entries = plugin.diamondLogManager.getRecentEntries(limit)

        if (entries.isEmpty()) {
            sender.sendMessage(mm("<gray>Es gibt heute noch keine Log-Einträge.</gray>"))
            return true
        }

        val lines = mutableListOf("<gold>=== Diamond-Log (letzte ${entries.size}) ===</gold>")
        entries.forEach { lines += "<gray>$it</gray>" }
        sender.sendMessage(mm(lines.joinToString("\n")))

        return true
    }
}