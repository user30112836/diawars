package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import de.davidsw.diawars.util.MiniMessageHelper.pmm
import org.bukkit.entity.Player
import java.util.UUID

class MessageManager(private val plugin: Diawars) {
    private val store get() = plugin.store.messageStore

    fun sendOrQueue(playerId: UUID, message: String, prefix: Boolean = false) {
        val player = plugin.server.getPlayer(playerId)
        if (player != null && player.isOnline) {
            player.sendMessage(
                if (prefix) {
                    pmm(message)
                } else {
                    mm(message)
                }
            )
        } else {
            store.addPending(playerId, message)
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