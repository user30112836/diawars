package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit.getOfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class AdminInvCommand(private val plugin: Diawars): CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return true
        }
        if (!sender.hasPermission("diawars.admin")) {
            sender.sendMessage(mm("<red>Du hast keine Berechtigung für diesen Befehl!</red>"))
            return true
        }
        if (args.size < 2) {
            sendHelp(sender)
            return true
        }

        val target = getOfflinePlayer(args[1])
        if (!target.hasPlayedBefore() && !target.isOnline) {
            sender.sendMessage(mm("<red>Dieser Spieler ist unbekannt!</red>"))
            return true
        }

        when (args[0].lowercase()) {
            "main" -> plugin.inventoryInspectManager.openMainInventory(sender, target)

            "event" -> {
                val eventId = args.getOrNull(2)
                if (eventId == null) {
                    sender.sendMessage(mm("<red>Verwendung: /admininv event &lt;spieler&gt; &lt;event-id&gt;</red>"))
                    return true
                }
                plugin.inventoryInspectManager.openEventInventory(sender, target, eventId)
            }

            "enderchest" -> plugin.inventoryInspectManager.openEnderChest(sender, target, args.getOrNull(2))

            else -> sendHelp(sender)
        }

        return true
    }

    private fun sendHelp(player: Player) {
        player.sendMessage(mm("""
            <gold>=== Inventar-Inspektion ===</gold>
            <yellow>/admininv main &lt;spieler&gt;</yellow><gray> - Hauptinventar (Normalwelt)</gray>
            <yellow>/admininv event &lt;spieler&gt; &lt;event-id&gt;</yellow><gray> - Event-Inventar</gray>
            <yellow>/admininv enderchest &lt;spieler&gt; [event-id]</yellow><gray> - Enderkiste</gray>
        """.trimIndent()))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("main", "event", "enderchest").filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2) {
            return plugin.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
        }
        if (args.size == 3 && (args[0].equals("event", true) || args[0].equals("enderchest", true))) {
            return plugin.store.eventStore.getAll().map { it.id }.filter { it.startsWith(args[2].lowercase()) }
        }
        return emptyList()
    }
}