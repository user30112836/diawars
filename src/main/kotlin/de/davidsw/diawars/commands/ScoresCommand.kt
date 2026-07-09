package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.stores.ScoreboardComponent
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
            "sidebar" -> handleSidebar(sender, args)
            else -> {
                sender.sendMessage(mm("<red>Unbekannter Befehl! Verwendung: /scores &lt;info&gt;</red>"))
            }
        }

        return true
    }

    private fun handleSidebar(sender: Player, args: Array<out String>) {
        val store = plugin.store.scoreboardPreferencesStore

        if (args.size < 2) {
            val current = store.isSidebarEnabled(sender.uniqueId)
            store.setSidebarEnabled(sender.uniqueId, !current)
            sender.sendMessage(mm(if (!current) "<green>✓ Sidebar wurde aktiviert!</green>" else "<red>✗ Sidebar wurde deaktiviert!</red>"))
            return
        }

        when (args[1].lowercase()) {
            "list", "status" -> {
                val pref = store.getPreference(sender.uniqueId)
                val lines = mutableListOf(
                    "<gold>=== Sidebar-Einstellungen ===</gold>",
                    "<gray>Sidebar: </gray>${if (pref.sidebarEnabled) "<green>Aktiviert</green>" else "<red>Deaktiviert</red>"}",
                )
                ScoreboardComponent.entries.forEach { component ->
                    val enabled = component in pref.enabledComponents
                    lines += "<gray>- ${component.label} (<yellow>${component.configKey}</yellow>): </gray>${if (enabled) "<green>An</green>" else "<red>Aus</red>"}"
                }
                sender.sendMessage(mm(lines.joinToString("\n")))
            }

            "on", "enable" -> {
                store.setSidebarEnabled(sender.uniqueId, true)
                sender.sendMessage(mm("<green>✓ Sidebar wurde aktiviert!</green>"))
            }

            "off", "disable" -> {
                store.setSidebarEnabled(sender.uniqueId, false)
                sender.sendMessage(mm("<red>✗ Sidebar wurde deaktiviert!</red>"))
            }

            "reset", "default" -> {
                store.resetToDefault(sender.uniqueId)
                sender.sendMessage(mm("<green>✓ Sidebar-Einstellungen wurden auf Standard zurückgesetzt!</green>"))
            }

            else -> {
                val component =
                    ScoreboardComponent.entries.firstOrNull { it.configKey.equals(args[1], ignoreCase = true) }
                if (component == null) {
                    sender.sendMessage(
                        mm(
                            "<red>Unbekannte Komponente! Verwendung: /scores sidebar &lt;${
                                ScoreboardComponent.entries.joinToString("|") { it.configKey }
                            }&gt;</red>"
                        )
                    )
                    return
                }
                store.toggleComponent(sender.uniqueId, component)
                val enabled = store.isComponentEnabled(sender.uniqueId, component)
                sender.sendMessage(
                    mm(
                        if (enabled) "<green>✓ ${component.label} wird jetzt angezeigt!</green>"
                        else "<red>✗ ${component.label} wird jetzt ausgeblendet!</red>"
                    )
                )
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String?> {
        if (args.size == 1) {
            return listOf("info", "sidebar")
                .filter { it.startsWith(args[0].lowercase()) }
        }

        if (args.size == 2 && args[0].equals("sidebar", ignoreCase = true)) {
            val options = listOf("on", "off", "list", "reset") + ScoreboardComponent.entries.map { it.configKey }
            return options.filter { it.startsWith(args[1].lowercase()) }
        }

        return emptyList()
    }
}