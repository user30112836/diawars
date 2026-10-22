package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.plugin.messaging.PluginMessageListener

/**
 * Answers the client mods' handshake probe (mod selfdisable, see the client
 * {@code ServerHandshake}): the mods send an empty {@code diawars:handshake}
 * on every server/world join and only talk to the server once it is answered.
 * The answer is an empty {@code diawars:handshake_ack} payload, sent only as
 * a response — foreign servers never see the channel and older mod versions
 * never probe, so both keep their old behavior.
 */
class HandshakeListener(
    private val plugin: Diawars
) : Listener, PluginMessageListener {

    companion object {
        /** Serverbound: the mods send an empty probe on every (world) join. */
        const val CHANNEL = "diawars:handshake"

        /** Clientbound: empty answer confirming the plugin is present. */
        const val ACK_CHANNEL = "diawars:handshake_ack"
    }

    override fun onPluginMessageReceived(
        channel: String,
        player: Player,
        message: ByteArray
    ) {
        if (channel != CHANNEL) {
            return
        }
        // Answer directly (lowest latency); the mod disables itself after a
        // short grace period, so a slow reply would leave it "disabled" on
        // Diawars servers while the join kick timer runs.
        try {
            player.sendPluginMessage(plugin, ACK_CHANNEL, ByteArray(0))
        } catch (e: Exception) {
            plugin.logger.warning(
                "Handshake ack to ${player.name} failed: ${e.message}"
            )
        }
    }
}
