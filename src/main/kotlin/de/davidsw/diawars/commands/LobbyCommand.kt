package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class LobbyCommand(private val plugin: Diawars): CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return true
        }

        if (plugin.eventManager.getSession(sender.uniqueId) != null) {
            sender.sendMessage(mm("<red>Du kannst die Lobby nicht betreten während du dich in einem Event befindest! Nutze zuerst /event leave.</red>"))
            return true
        }

        if (plugin.lobbyManager.isInLobby(sender.uniqueId)) {
            if (plugin.lobbyManager.leaveLobby(sender)) {
                sender.sendMessage(mm("<green>Du hast die Lobby verlassen.</green>"))
            }
        } else {
            if (plugin.lobbyManager.sendToLobby(sender)) {
                sender.sendMessage(mm("<green>Willkommen in der Lobby!</green>"))
            } else {
                sender.sendMessage(mm("<red>Die Lobby konnte nicht geladen werden!</red>"))
            }
        }

        return true
    }
}