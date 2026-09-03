package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.messaging.PluginMessageListener

class ClientInfoListener(
    private val plugin: Diawars
) : Listener, PluginMessageListener {

    companion object {
        const val CHANNEL = "diawars:client_info"

        private fun readVarIntPrefixedUtf8(bytes: ByteArray): String {
            var index = 0
            var length = 0
            var shift = 0

            while (true) {
                if (index >= bytes.size) {
                    throw IllegalArgumentException("Missing VarInt")
                }

                val byte = bytes[index++].toInt()

                length = length or ((byte and 0x7F) shl shift)

                if (byte and 0x80 == 0) {
                    break
                }

                shift += 7

                if (shift >= 35) {
                    throw IllegalArgumentException("VarInt too big")
                }
            }

            if (length < 0 || index + length > bytes.size) {
                throw IllegalArgumentException("Invalid message length")
            }

            return String(
                bytes,
                index,
                length,
                Charsets.UTF_8
            )
        }
    }

    override fun onPluginMessageReceived(
        channel: String,
        player: Player,
        message: ByteArray
    ) {
        if (channel != CHANNEL) {
            return
        }

        try {
            val json = readVarIntPrefixedUtf8(message)
            plugin.store.clientInfoStore.saveFromJson(
                player.uniqueId,
                json
            )
            plugin.logger.info("Received client info message from ${player.name}: $json")
            plugin.store.clientInfoStore.addReported(player.uniqueId)
        } catch (e: Exception) {
            plugin.logger.warning(
                "Invalid client info message from ${player.name}: ${e.message}"
            )
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (
                player.isOnline &&
                !player.hasPermission("diawars.admin") &&
                !plugin.store.clientInfoStore.hasReported(player.uniqueId)
            ) {
                player.kick(
                    mm(
                        "<red>Du musst die Diawars-Client-Mod installiert haben, " +
                                "um zu spielen! Lade sie herunter und versuche es erneut.</red>"
                    )
                )
            }
        }, 50L)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) = plugin.store.clientInfoStore.removeReported(event.player.uniqueId)
}