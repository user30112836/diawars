package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SelfKillCommand(private val plugin: Diawars): CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return false
        if (plugin.pvpManager.isInFight(sender.uniqueId)) {
            val message = mm("<red>Du kannst keinen SelfKill machen wenn du in einem Kampf bist!</red>")
            sender.sendMessage(message)
            return true
        }
        sender.health = 0.0
        return true
    }
}