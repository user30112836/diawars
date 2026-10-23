package de.davidsw.diawars.listeners

import com.google.gson.Gson
import com.google.gson.JsonObject
import de.davidsw.diawars.Diawars
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.plugin.messaging.PluginMessageListener

/**
 * Server side of the Diawars Admin client mod (separate channels so the
 * player client mod and the admin mod can coexist):
 * - {@code diawars:admin_handshake} → empty {@code diawars:admin_handshake_ack}
 *   answer (mod self-disable, mirrors [HandshakeListener]),
 * - {@code diawars:admin_client_info} → registers the player as an admin-mod
 *   user via [AdminGuiManager.handleAdminModUser]; non-admins are kicked and
 *   online admins are notified,
 * - {@code diawars:admin_fetch} → GUI data pulls via
 *   [AdminGuiManager.handleFetch] (main-thread build, JSON answer).
 */
class AdminModListener(
    private val plugin: Diawars
) : Listener, PluginMessageListener {

    companion object {
        /** Serverbound: the admin mod sends an empty probe on every (world) join. */
        const val HANDSHAKE_CHANNEL = "diawars:admin_handshake"

        /** Clientbound: empty answer confirming the plugin is present. */
        const val ACK_CHANNEL = "diawars:admin_handshake_ack"

        /** Serverbound: {"version": "..."} announcement of the admin mod client. */
        const val CLIENT_INFO_CHANNEL = "diawars:admin_client_info"

        /** Serverbound: {"section": "...", "filter": "..."} GUI data request. */
        const val FETCH_CHANNEL = "diawars:admin_fetch"

        /** Clientbound: {"section", "filter", "data"} answer to a fetch. */
        const val DATA_CHANNEL = "diawars:admin_data"

        /** Clientbound: open the admin GUI (sent by the bare /admin command). */
        const val OPEN_CHANNEL = "diawars:admin_open"

        private val GSON = Gson()
    }

    override fun onPluginMessageReceived(
        channel: String,
        player: Player,
        message: ByteArray
    ) {
        when (channel) {
            HANDSHAKE_CHANNEL -> {
                // Answer directly (lowest latency); the mod disables itself after
                // a short grace period without an ack.
                try {
                    player.sendPluginMessage(plugin, ACK_CHANNEL, ByteArray(0))
                } catch (e: Exception) {
                    plugin.logger.warning(
                        "Admin handshake ack to ${player.name} failed: ${e.message}"
                    )
                }
            }

            CLIENT_INFO_CHANNEL -> plugin.adminGuiManager.handleAdminModUser(player)

            FETCH_CHANNEL -> {
                val doc: JsonObject? = try {
                    GSON.fromJson(ImageListener.readVarIntPrefixedUtf8(message), JsonObject::class.java)
                } catch (_: Exception) {
                    null
                }
                val section = doc?.get("section")?.takeIf { it.isJsonPrimitive }?.asString ?: return
                val filter = doc.get("filter")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                plugin.adminGuiManager.handleFetch(player, section, filter)
            }
        }
    }
}
