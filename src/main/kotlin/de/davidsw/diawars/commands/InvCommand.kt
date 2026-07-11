package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MaterialSets
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class InvCommand(private val plugin: Diawars): CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return true
        }

        val item = sender.inventory.itemInMainHand

        when (item.type) {
            in MaterialSets.SHULKER_BOXES -> {
                if (!plugin.shulkerAccessManager.openHeldShulker(sender)) {
                    sender.sendMessage(mm("<red>Diese Shulker-Box konnte nicht geöffnet werden!</red>"))
                }
            }

            Material.ENDER_CHEST -> {
                plugin.shulkerAccessManager.openHeldEnderChest(sender)
            }

            else -> {
                sender.sendMessage(mm("<red>Du musst eine Shulker-Box oder eine Enderkiste in der Hand haben!</red>"))
            }
        }

        return true
    }
}