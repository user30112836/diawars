package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Shows the mod whitelist: the client-mod dialog opens the server's allowed
 * mods plus the request field for new mods. On Bedrock (and thus also for
 * console) this is not available and errors instead.
 */
class ModsCommand(private val plugin: Diawars) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return true
        }
        plugin.modWhitelistManager.openDialog(player)
        return true
    }
}
