package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import org.bukkit.ChatColor
import org.bukkit.entity.Player

class MessageManager(private val plugin: Diawars) {
    private var prefix = ""
    private var enterGenericArea = ""
    private var noBuildPermission = ""
    private var returnToZone = ""

    init {
        loadMessages()
    }

    fun loadMessages() {
        prefix = translateColors(plugin.config.getString("messages.prefix") ?: "&8[&6Plugin&8]&r ")
        enterGenericArea = translateColors(plugin.config.getString("messages.enter-generic-area") ?: "&eDu betrittst jetzt den generischen Bereich!")
        noBuildPermission = translateColors(plugin.config.getString("messages.no-build-permission") ?: "&cDu kannst hier nicht bauen oder abbauen!")
        returnToZone = translateColors(plugin.config.getString("messages.return-to-zone") ?: "&aDu bist wieder in deiner Zone!")
    }

    private fun translateColors(message: String): String {
        return ChatColor.translateAlternateColorCodes('&', message)
    }

    fun sendEnterGenericArea(player: Player) {
        player.sendMessage(prefix + enterGenericArea)
        player.sendMessage(prefix + noBuildPermission)
    }

    fun sendReturnToZone(player: Player) {
        player.sendMessage(prefix + returnToZone)
    }

    fun sendNoPermission(player: Player) {
        player.sendMessage(prefix + ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl!")
    }
}