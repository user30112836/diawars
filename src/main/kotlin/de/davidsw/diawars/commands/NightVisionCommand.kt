package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class NightVisionCommand(private val plugin: Diawars) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return true
        }

        if (!plugin.nightVisionManager.isAvailable()) {
            sender.sendMessage(mm("<red>Nachtsicht ist derzeit deaktiviert!</red>"))
            return true
        }

        val manager = plugin.nightVisionManager
        when (args.getOrNull(0)?.lowercase()) {
            null -> {
                val next = manager.toggle(sender) ?: return true
                sender.sendMessage(
                    mm(if (next) "<green>✓ Nachtsicht aktiviert!</green>" else "<gray>Nachtsicht deaktiviert.</gray>"),
                )
            }
            "on", "enable", "an" -> {
                manager.setEnabled(sender, true)
                sender.sendMessage(mm("<green>✓ Nachtsicht aktiviert!</green>"))
            }
            "off", "disable", "aus" -> {
                manager.setEnabled(sender, false)
                sender.sendMessage(mm("<gray>Nachtsicht deaktiviert.</gray>"))
            }
            else -> sender.sendMessage(mm("<red>Verwendung: /nightvision [on|off]</red>"))
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (args.size == 1) {
            return listOf("on", "off").filter { it.startsWith(args[0].lowercase()) }
        }
        return emptyList()
    }
}
