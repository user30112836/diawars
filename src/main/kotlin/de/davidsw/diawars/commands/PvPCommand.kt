package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.PvPManager
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import kotlin.math.floor

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

            args[0].equals("admin", ignoreCase = true) -> {
                if (!sender.hasPermission("diawars.admin")) return true
                handleAdmin(sender, args)
            }

            else -> {
                sendHelp(sender)
            }
        }

        return true
    }

    private fun handleAdmin(player: Player, args: Array<out String>) {
        if (args.size < 3) {
            player.sendMessage(mm("""
            <gold>=== PvP Admin ===</gold>
            <gray>/pvp admin &lt;spieler|all&gt; on</gray>
            <gray>/pvp admin &lt;spieler|all&gt; off</gray>
        """.trimIndent()))
            return
        }

        val targetArg = args[1]
        val statusArg = args[2].lowercase()
        val status = when (statusArg) {
            "on", "enable" -> true
            "off", "disable" -> false
            else -> {
                player.sendMessage(mm("<red>Verwende 'on' oder 'off'</red>"))
                return
            }
        }

        if (targetArg.equals("all", ignoreCase = true)) {
            var count = 0
            plugin.server.onlinePlayers.forEach { player ->
                plugin.messageManager.sendOrQueue(
                    player,
                    "<yellow>Dein PvP Status wurde von einem Admin geändert!</yellow>",
                    true
                )
                manager.cancelToggle(player.uniqueId)
                store.applyPvPStatus(player.uniqueId, status)
                count++
            }
            player.sendMessage(mm("<green>✓ PvP wurde für <gold>$count</gold> Spieler ${if (status) "aktiviert" else "deaktiviert"}!</green>"))
            return
        }

        val target = plugin.server.getPlayer(targetArg)
        if (target == null) {
            player.sendMessage(mm("<red>Spieler nicht gefunden oder offline!</red>"))
            return
        }

        plugin.messageManager.sendOrQueue(
            player,
            "<yellow>Dein PvP Status wurde von einem Admin geändert!</yellow>",
            true
        )
        manager.cancelToggle(target.uniqueId)
        store.applyPvPStatus(target.uniqueId, status)
        player.sendMessage(mm("<green>✓ PvP von <gold>${target.name}</gold> wurde ${if (status) "aktiviert" else "deaktiviert"}!</green>"))
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

            PvPManager.ToggleResult.IN_FIGHT -> {
                val remaining = plugin.pvpManager.fightTimeRemaining(player.uniqueId)
                val minutes = floor(remaining.toDouble() / 60).toInt()
                val seconds = remaining % 60
                val duration = if (minutes != 0) "${minutes}:${if (seconds < 10) "0${seconds}" else seconds.toString()}" else seconds.toString()
                val message = mm("""
                    <red>Du kannst den PvPStatus nicht während eines Kampfes ändern!</red>
                    
                    <gray>Verbleibende Zeit:</gray><yellow>$duration</yellow>
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
                list += if (store.hasPendingToggle(sender.uniqueId)) "cancel" else "toggle"
            }
            if (sender.hasPermission("diawars.admin")) list += "admin"
            return list.filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && args[0].equals("admin", ignoreCase = true)) {
            val names = plugin.server.onlinePlayers.map { it.name } + "all"
            return names.filter { it.startsWith(args[1], ignoreCase = true) }
        }
        if (args.size == 3 && args[0].equals("admin", ignoreCase = true)) {
            return listOf("on", "off").filter { it.startsWith(args[2].lowercase()) }
        }
        return emptyList()
    }
}