package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit.getOfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class AdminCommand(private val plugin: Diawars) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("diawars.admin")) {
            sender.sendMessage(mm("<red>Du hast keine Berechtigung für diesen Befehl!</red>"))
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> handleReload(sender)
            "inv" -> handleInv(sender, args.drop(1))
            else -> sendHelp(sender)
        }

        return true
    }

    private fun handleReload(sender: CommandSender) {
        try {
            plugin.reloadPluginConfigs()
            sender.sendMessage(mm("<green>✓ Konfiguration erfolgreich neu geladen!</green>"))
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload configs: ${e.message}")
            sender.sendMessage(mm("<red>Fehler beim Neuladen der Konfiguration! Siehe Konsole für Details.</red>"))
        }
    }

    private fun handleInv(sender: CommandSender, args: List<String>) {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return
        }
        if (args.size < 2) {
            sendInvHelp(sender)
            return
        }

        val target = getOfflinePlayer(args[1])
        if (!target.hasPlayedBefore() && !target.isOnline) {
            sender.sendMessage(mm("<red>Dieser Spieler ist unbekannt!</red>"))
            return
        }

        when (args[0].lowercase()) {
            "main" -> plugin.inventoryInspectManager.openMainInventory(sender, target)

            "event" -> {
                val eventId = args.getOrNull(2)
                if (eventId == null) {
                    sender.sendMessage(mm("<red>Verwendung: /admin inv event &lt;spieler&gt; &lt;event-id&gt;</red>"))
                    return
                }
                plugin.inventoryInspectManager.openEventInventory(sender, target, eventId)
            }

            "enderchest" -> plugin.inventoryInspectManager.openEnderChest(sender, target, args.getOrNull(2))

            else -> sendInvHelp(sender)
        }
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(
            mm(
                """
                <gold>=== Admin-Befehle ===</gold>
                <yellow>/admin reload</yellow><gray> - Lädt alle Konfigurationsdateien neu</gray>
                <yellow>/admin inv main &lt;spieler&gt;</yellow><gray> - Hauptinventar (Normalwelt)</gray>
                <yellow>/admin inv event &lt;spieler&gt; &lt;event-id&gt;</yellow><gray> - Event-Inventar</gray>
                <yellow>/admin inv enderchest &lt;spieler&gt; [event-id]</yellow><gray> - Enderkiste</gray>
                """.trimIndent(),
            ),
        )
    }

    private fun sendInvHelp(player: Player) {
        player.sendMessage(
            mm(
                """
                <gold>=== Inventar-Inspektion ===</gold>
                <yellow>/admin inv main &lt;spieler&gt;</yellow><gray> - Hauptinventar (Normalwelt)</gray>
                <yellow>/admin inv event &lt;spieler&gt; &lt;event-id&gt;</yellow><gray> - Event-Inventar</gray>
                <yellow>/admin inv enderchest &lt;spieler&gt; [event-id]</yellow><gray> - Enderkiste</gray>
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
            return listOf("reload", "inv").filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && args[0].equals("inv", ignoreCase = true)) {
            return listOf("main", "event", "enderchest").filter { it.startsWith(args[1].lowercase()) }
        }
        if (args.size == 3 && args[0].equals("inv", ignoreCase = true)) {
            return plugin.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[2], ignoreCase = true) }
        }
        if (args.size == 4 && args[0].equals("inv", ignoreCase = true) &&
            (args[1].equals("event", ignoreCase = true) || args[1].equals("enderchest", ignoreCase = true))
        ) {
            return plugin.store.eventStore.getAll().map { it.id }.filter { it.startsWith(args[3].lowercase()) }
        }
        return emptyList()
    }
}
