package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.ImageManager
import de.davidsw.diawars.util.MiniMessageHelper.escape
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ImageCommand(private val plugin: Diawars) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return true
        }

        if (args.isEmpty()) {
            val images = plugin.imageManager.listImages(sender.uniqueId)
            if (images.isEmpty()) {
                sender.sendMessage(mm("<gray>Du hast noch keine Bilder hochgeladen. Nutze die Diawars-Client-Mod zum Hochladen.</gray>"))
            } else {
                sender.sendMessage(mm("<gold>=== Deine Bilder ===</gold>\n<gray>${images.joinToString(", ") { escape(it) }}</gray>\n<gray>Verwendung: /image &lt;datei&gt;</gray>"))
            }
            return true
        }

        if (args.size > 1) {
            sender.sendMessage(mm("<red>Verwendung: /image &lt;datei&gt;</red>"))
            return true
        }

        val result = plugin.imageManager.giveImage(sender, sender.uniqueId, args[0])
        when (result) {
            is ImageManager.Result.Success -> sender.sendMessage(mm(result.message))
            is ImageManager.Result.Error -> sender.sendMessage(mm(result.message))
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (sender !is Player) return emptyList()
        if (args.size == 1) {
            return plugin.imageManager.listImages(sender.uniqueId)
                .filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}
