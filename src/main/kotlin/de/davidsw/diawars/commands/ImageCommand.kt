package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.ImageManager
import de.davidsw.diawars.util.BedrockUtil
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

        // Bedrock players have no client mod, so neither upload nor map
        // handling is available to them. The menu is hidden as well.
        if (BedrockUtil.isBedrock(sender)) {
            sender.sendMessage(mm("<red>Die Bilder-Funktion ist auf Bedrock nicht verfügbar.</red>"))
            return true
        }

        if (plugin.imageManager.isBanned(sender.uniqueId)) {
            sender.sendMessage(mm("<red>Du bist für die Bilder-Funktion gesperrt! Wende dich an einen Admin.</red>"))
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            null -> listImages(sender)

            "upload" -> {
                if (args.size > 1) {
                    sendUsage(sender)
                    return true
                }
                plugin.imageManager.requestUploadUi(sender)
            }

            "get" -> {
                // Image names may contain spaces (split uploads use "name (1)"),
                // so everything after "get" is the file name.
                val file = args.drop(1).joinToString(" ").trim()
                if (file.isEmpty()) {
                    sendUsage(sender)
                    return true
                }
                respond(sender, plugin.imageManager.giveImage(sender, sender.uniqueId, file))
            }

            else -> sendUsage(sender)
        }
        return true
    }

    private fun listImages(player: Player) {
        val images = plugin.imageManager.listImages(player.uniqueId)
        if (images.isEmpty()) {
            player.sendMessage(mm("<gray>Du hast noch keine Bilder hochgeladen. Nutze <yellow>/image upload</yellow> oder das Bilder-Menü.</gray>"))
        } else {
            player.sendMessage(mm("<gold>=== Deine Bilder ===</gold>\n<gray>${images.joinToString(", ") { escape(it) }}</gray>\n<gray>Verwendung: /image get &lt;datei&gt;</gray>"))
        }
    }

    private fun respond(player: Player, result: ImageManager.Result) {
        when (result) {
            is ImageManager.Result.Success -> player.sendMessage(mm(result.message))
            is ImageManager.Result.Error -> player.sendMessage(mm(result.message))
        }
    }

    private fun sendUsage(player: Player) {
        player.sendMessage(
            mm(
                """
                <gold>=== Bilder-Befehle ===</gold>
                <yellow>/image get &lt;datei&gt;</yellow><gray> - Karte mit deinem Bild erhalten</gray>
                <yellow>/image upload</yellow><gray> - Bild über die Client-Mod hochladen</gray>
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
        if (sender !is Player) return emptyList()
        if (BedrockUtil.isBedrock(sender)) return emptyList()
        if (plugin.imageManager.isBanned(sender.uniqueId)) return emptyList()
        if (args.size == 1) {
            return listOf("get", "upload").filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size >= 2 && args[0].equals("get", ignoreCase = true)) {
            val prefix = args.drop(1).joinToString(" ")
            return plugin.imageManager.listImages(sender.uniqueId)
                .filter { it.startsWith(prefix, ignoreCase = true) }
        }
        return emptyList()
    }
}
