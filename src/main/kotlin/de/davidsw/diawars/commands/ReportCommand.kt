package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.ReportManager
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Command-line report path (for everyone, mandatory for players without the
 * client mod): {@code /report <player>|- [grund]}. A screenshot is always
 * taken when the mod can do it and the map in the main hand is appended if
 * possible (see ReportManager#submitCommandReport). The richer report dialog
 * opens from the "Melden" main-menu button.
 */
class ReportCommand(private val plugin: Diawars) : CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        if (args[0] == "-") {
            // "-" means: no player target (reason/image only).
            respond(sender, plugin.reportManager.submitCommandReport(sender, null, reasonOf(args)))
            return true
        }

        respond(sender, plugin.reportManager.submitCommandReport(sender, args[0], reasonOf(args)))
        return true
    }

    /** What was typed after the player token ({@code -}); empty strings become null. */
    private fun reasonOf(args: Array<out String>): String? =
        args.drop(1).joinToString(" ").trim().ifEmpty { null }

    private fun respond(player: Player, result: ReportManager.Result) {
        when (result) {
            is ReportManager.Result.Success -> player.sendMessage(mm(result.message))
            is ReportManager.Result.Error -> player.sendMessage(mm(result.message))
        }
    }

    private fun sendHelp(player: Player) {
        player.sendMessage(
            mm(
                """
                <gold>=== Report ===</gold>
                <yellow>/report &lt;spieler|-&gt; [grund]</yellow><gray> - Spieler und/oder eine Bild-Karte (in der Haupthand) melden</gray>
                <gray>Ein Screenshot deines Bildschirms wird automatisch angehängt.</gray>
                <gray>Komfortabler: der "Melden"-Button im Hauptmenü öffnet den Report-Dialog.</gray>
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
            return (listOf("-") + Bukkit.getOnlinePlayers().map { it.name })
                .filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}
