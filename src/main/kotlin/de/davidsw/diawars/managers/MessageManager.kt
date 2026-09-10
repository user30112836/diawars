package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import de.davidsw.diawars.util.MiniMessageHelper.pmm
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID

class MessageManager(private val plugin: Diawars) {
    private val store get() = plugin.store.messageStore

    fun sendOrQueue(playerId: UUID, message: String, prefix: Boolean = false, sender: CommandSender? = null) {
        val player = plugin.server.getPlayer(playerId)
        val isAfk = plugin.afkManager.isAfk(playerId)

        if (player != null && player.isOnline && !isAfk) {
            player.sendMessage(
                if (prefix) {
                    pmm(message)
                } else {
                    mm(message)
                }
            )
        } else {
            store.addPending(playerId, message)
            if (isAfk && sender != null) {
                sender.sendMessage(pmm("<gray>Hinweis: <white>${player?.name ?: "Der Spieler"}</white> ist gerade AFK und sieht deine Nachricht eventuell erst später.</gray>"))
            }
        }
    }

    fun sendOrQueue(player: Player, message: String, prefix: Boolean = false) = sendOrQueue(player.uniqueId, message, prefix)

    fun deliverPending(player: Player) {
        val pending = store.getPending(player.uniqueId)
        if (pending.isEmpty()) return

        pending.forEach { player.sendMessage(mm(it)) }
        store.clearPending(player.uniqueId)
    }
}