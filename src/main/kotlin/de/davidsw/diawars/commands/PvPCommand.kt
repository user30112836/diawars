package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.PvPManager
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class PvPCommand(private val plugin: Diawars): CommandExecutor, TabCompleter {
    private val manager = plugin.pvpManager
    private val store = plugin.store.pvpStatusStore

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage(mm("<red>Dieser Befehl kann nur von Spielern ausgeführt werden!</red>"))
            return true
        }

        when {
            args.isEmpty() -> {
                handleToggle(sender)
            }

            args[0].equals("cancel", ignoreCase = true) -> {
                handleCancel(sender)
            }

            args[0].equals("status", ignoreCase = true) -> {
                handleStatus(sender)
            }

            args[0].equals("toggle", ignoreCase = true) -> {
                handleToggle(sender)
            }

            else -> {
                sendHelp(sender)
            }
        }

        return true
    }

    private fun handleToggle(player: Player) {
        val result = manager.togglePvP(player.uniqueId)

        when (result) {
            PvPManager.ToggleResult.ENABLING -> {
                val message = mm("""                    
                    <green><bold>PvP-AKTIVIERUNG</bold></green>
                    
                    <gray>Dein PvP wird in <yellow>5 Minuten</yellow> aktiviert</gray>
                    <gray>Du kannst dann wieder angreifen und angegriffen werden</gray>
                    
                    <gray>Abbrechen: </gray><yellow>/pvp cancel</yellow>
                """.trimIndent())
                player.sendMessage(message)
            }

            PvPManager.ToggleResult.DISABLING -> {
                val message = mm("""
                    <red><bold>PvP-DEAKTIVIERUNG</bold></red>
                    
                    <gray>Dein PvP wird in <yellow>5 Minuten</yellow> deaktiviert</gray>
                    <gray>Du kannst dann nicht mehr angreifen oder angegriffen werden</gray>
                    
                    <gray>Abbrechen: </gray><yellow>/pvp cancel</yellow>
                """.trimIndent())
                player.sendMessage(message)
            }

            PvPManager.ToggleResult.ALREADY_PENDING -> {
                val remaining = store.getRemainingTime(player.uniqueId)
                val minutes = remaining / 60
                val seconds = remaining % 60
                val duration = buildString {
                    if (minutes.toInt() != 0) append(" ${minutes}m")
                    if (seconds.toInt() != 0) append(" ${seconds}s")
                }
                val message = mm("""
                    <red>Du hast bereits eine wartende PvP-Änderung!</red>
                    
                    <gray>Verbleibende Zeit:</gray><yellow>$duration</yellow>
                    <gray>Abbrechen: </gray><yellow>/pvp cancel</yellow>
                """.trimIndent())
                player.sendMessage(message)
            }
        }
    }

    private fun handleCancel(player: Player) {
        if (manager.cancelToggle(player.uniqueId)) {
            val message = mm("""
                <green><bold>PvP-ÄNDERUNG ABGEBROCHEN</bold></green>
                
                <gray>Deine wartende PvP-Änderung wurde erfolgreich abgebrochen</gray>
            """.trimIndent())
            player.sendMessage(message)
        } else {
            player.sendMessage(mm("<red>Du hast keine wartende PvP-Änderung!</red>"))
        }
    }

    private fun handleStatus(player: Player) {
        val currentStatus = store.isPvPEnabled(player.uniqueId)
        val statusText = if (currentStatus) "<green>✔ <bold>AKTIVIERT</bold></green>" else "<red>✖ <bold>DEAKTIVIERT</bold></red>"
        var toggleText = ""

        if (store.hasPendingToggle(player.uniqueId)) {
            val remaining = store.getRemainingTime(player.uniqueId)
            val minutes = remaining / 60
            val seconds = remaining % 60
            val duration = buildString {
                if (minutes.toInt() != 0) append(" ${minutes}m")
                if (seconds.toInt() != 0) append(" ${seconds}s")
            }
            toggleText = "<gray>⏱ Wartende Änderung:</gray><yellow>$duration</yellow>"
        }

        val message = mm("""
            <yellow><bold>DEIN PvP-Status</bold></yellow>
            
            <gray>Aktueller Status: </gray>$statusText
            $toggleText
        """.trimIndent())
        player.sendMessage(message)
    }

    private fun sendHelp(player: Player) {
        val message = mm("""
            <yellow><bold>PvP-BEFEHLE</bold></yellow>
            
            <yellow>/pvp</yellow><gray> - PvP an/ausschalten</gray>
            <yellow>/pvp status</yellow><gray> - Status anzeigen</gray>
            <yellow>/pvp cancel</yellow><gray> - Änderung abbrechen</gray>
            <yellow>/pvp toggle</yellow><gray> - PvP an/ausschalten</gray>
        """.trimIndent())
        player.sendMessage(message)
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            val list = mutableListOf("status")
            if (sender is Player) {
                list += if (store.hasPendingToggle(sender.uniqueId)) {
                    "cancel"
                } else {
                    "toggle"
                }
            }
            return list.filter {
                it.startsWith(args[0].lowercase())
            }
        }
        return emptyList()
    }
}