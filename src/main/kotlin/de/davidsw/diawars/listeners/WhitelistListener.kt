package de.davidsw.diawars.listeners

import com.google.gson.Gson
import com.google.gson.JsonObject
import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.ModWhitelistManager
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.messaging.PluginMessageListener

/**
 * Mod-whitelist hooks: admins get a joint notice about open mod requests
 * (mirrors bugs/reports), and the client-mod whitelist dialog submits
 * display-name requests through [ModWhitelistManager.REQUEST_CHANNEL] here
 * (JSON: name).
 */
class WhitelistListener(private val plugin: Diawars) : Listener, PluginMessageListener {

    companion object {
        private val GSON = Gson()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.modWhitelistManager.handleAdminJoin(event.player)
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != ModWhitelistManager.REQUEST_CHANNEL) return

        val text = try {
            ImageListener.readVarIntPrefixedUtf8(message)
        } catch (_: Exception) {
            return
        }
        val doc: JsonObject = try {
            GSON.fromJson(text, JsonObject::class.java) ?: return
        } catch (_: Exception) {
            return
        }

        val name = doc.get("name")?.takeIf { it.isJsonPrimitive }?.asString
        val result = plugin.modWhitelistManager.handleSubmit(player, name)
        val messageText = when (result) {
            is ModWhitelistManager.Result.Success -> result.message
            is ModWhitelistManager.Result.Error -> result.message
        }
        player.sendMessage(mm(messageText))
    }
}
