package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.PvPManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class PvPCommand(private val plugin: Diawars): CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cDieser Befehl kann nur von Spielern ausgeführt werden!")
            return true
        }

        when {
            args.isEmpty() -> {
                handleToggle(sender, plugin.pvpManager)
            }

            args[0].equals("cancel", ignoreCase = true) -> {
                handleCancel(sender, plugin.pvpManager)
            }

            args[0].equals("status", ignoreCase = true) -> {
                handleStatus(sender, plugin.pvpManager)
            }

            args[0].equals("toggle", ignoreCase = true) -> {
                handleToggle(sender, plugin.pvpManager)
            }

            else -> {
                sendHelp(sender)
            }
        }

        return true
    }

    private fun handleToggle(player: Player, manager: PvPManager) {
        val result = manager.togglePvP(player.uniqueId)

        when (result) {
            PvPManager.ToggleResult.ENABLING -> {
                player.sendMessage("")
                player.sendMessage("§7§m                                    ")
                player.sendMessage("")
                player.sendMessage("§a§lPvP-AKTIVIERUNG")
                player.sendMessage("")
                player.sendMessage("§7Dein PvP wird in §e5 Minuten §7aktiviert.")
                player.sendMessage("§7Du kannst dann wieder angreifen und")
                player.sendMessage("§7angegriffen werden.")
                player.sendMessage("")
                player.sendMessage("§7Abbrechen: §e/pvp cancel")
                player.sendMessage("")
                player.sendMessage("§7§m                                    ")
            }

            PvPManager.ToggleResult.DISABLING -> {
                player.sendMessage("")
                player.sendMessage("§7§m                                    ")
                player.sendMessage("")
                player.sendMessage("§c§lPvP-DEAKTIVIERUNG")
                player.sendMessage("")
                player.sendMessage("§7Dein PvP wird in §e5 Minuten §7deaktiviert.")
                player.sendMessage("§7Du kannst dann nicht mehr angreifen")
                player.sendMessage("§7oder angegriffen werden.")
                player.sendMessage("")
                player.sendMessage("§7Abbrechen: §e/pvp cancel")
                player.sendMessage("")
                player.sendMessage("§7§m                                    ")
            }

            PvPManager.ToggleResult.ALREADY_PENDING -> {
                val remaining = manager.getRemainingTime(player.uniqueId)
                val minutes = remaining / 60
                val seconds = remaining % 60
                player.sendMessage("§cDu hast bereits eine wartende PvP-Änderung!")
                player.sendMessage("§7Verbleibende Zeit: §e${minutes}m ${seconds}s")
                player.sendMessage("§7Abbrechen: §e/pvp cancel")
            }
        }
    }

    private fun handleCancel(player: Player, manager: PvPManager) {
        if (manager.cancelToggle(player.uniqueId)) {
            player.sendMessage("")
            player.sendMessage("§7§m                                    ")
            player.sendMessage("")
            player.sendMessage("§a§lPvP-ÄNDERUNG ABGEBROCHEN")
            player.sendMessage("")
            player.sendMessage("§7Deine wartende PvP-Änderung wurde")
            player.sendMessage("§7erfolgreich abgebrochen.")
            player.sendMessage("")
            player.sendMessage("§7§m                                    ")
        } else {
            player.sendMessage("§cDu hast keine wartende PvP-Änderung!")
        }
    }

    private fun handleStatus(player: Player, manager: PvPManager) {
        val currentStatus = manager.isPvPEnabled(player.uniqueId)
        val statusText = if (currentStatus) "§a§lAKTIVIERT" else "§c§lDEAKTIVIERT"
        val statusSymbol = if (currentStatus) "§a✔" else "§c✖"

        player.sendMessage("")
        player.sendMessage("§7§m                                    ")
        player.sendMessage("")
        player.sendMessage("§e§lDEIN PvP-STATUS")
        player.sendMessage("")
        player.sendMessage("§7Aktueller Status: $statusSymbol $statusText")

        if (manager.hasPendingToggle(player.uniqueId)) {
            val remaining = manager.getRemainingTime(player.uniqueId)
            val minutes = remaining / 60
            val seconds = remaining % 60
            player.sendMessage("")
            player.sendMessage("§7⏱ Wartende Änderung: §e${minutes}m ${seconds}s")
        }

        player.sendMessage("")
        player.sendMessage("§7§m                                    ")
    }

    private fun sendHelp(player: Player) {
        player.sendMessage("")
        player.sendMessage("§7§m                                    ")
        player.sendMessage("§e§lPvP-BEFEHLE")
        player.sendMessage("")
        player.sendMessage("§e/pvp §7- PvP an/ausschalten")
        player.sendMessage("§e/pvp status §7- Status anzeigen")
        player.sendMessage("§e/pvp cancel §7- Änderung abbrechen")
        player.sendMessage("§e/pvp toggle §7- PvP an/ausschalten")
        player.sendMessage("")
        player.sendMessage("§7§m                                    ")
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
                list += if (plugin.pvpManager.hasPendingToggle(sender.uniqueId)) {
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