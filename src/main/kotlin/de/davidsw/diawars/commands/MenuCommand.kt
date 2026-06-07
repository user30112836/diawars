package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class MenuCommand(private val plugin: Diawars): CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return false
        plugin.menuManager.openMainMenu(sender)
        return true
    }
}