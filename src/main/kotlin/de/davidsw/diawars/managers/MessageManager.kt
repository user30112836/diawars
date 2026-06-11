package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.entity.Player

class MessageManager(private val plugin: Diawars) {
    private var prefix = mm("")
    private var enterGenericArea = mm("")
    private var noBuildPermission = mm("")
    private var returnToZone = mm("")

    init {
        loadMessages()
    }

    fun loadMessages() {
        prefix = mm(plugin.config.getString("messages.prefix") ?: "<dark_gray>[<gold>Plugin</gold>] </dark_gray>")
        enterGenericArea = mm(plugin.config.getString("messages.enter-generic-area") ?: "<yellow>Du betrittst jetzt den generischen Bereich!</yellow>")
        noBuildPermission = mm(plugin.config.getString("messages.no-build-permission") ?: "<red>Du kannst hier nicht bauen oder abbauen!</red>")
        returnToZone = mm(plugin.config.getString("messages.return-to-zone") ?: "<green>Du bist wieder in deiner Zone!</green>")
    }

    fun sendEnterGenericArea(player: Player) {
        player.sendMessage(prefix.append(enterGenericArea))
        player.sendMessage(prefix.append(noBuildPermission))
    }

    fun sendReturnToZone(player: Player) {
        player.sendMessage(prefix.append(returnToZone))
    }

    fun sendNoPermission(player: Player) {
        player.sendMessage(prefix.append(mm("<red>Du hast keine Berechtigung für diesen Befehl!</red>")))
    }
}