package de.davidsw.diawars.listeners

import com.google.gson.Gson
import com.google.gson.JsonObject
import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.ReportManager
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.messaging.PluginMessageListener

/**
 * Report-system hooks: admins get a joint notice about open reports
 * (mirrors BugListener), quitting players drop their screenshot upload
 * session, and the client-mod report dialog submits whole reports through
 * [ReportManager.SUBMIT_CHANNEL] here (JSON: player, reason, shot, handImage).
 */
class ReportListener(private val plugin: Diawars) : Listener, PluginMessageListener {

    companion object {
        private val GSON = Gson()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.reportManager.handleAdminJoin(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        plugin.reportManager.clearPlayer(event.player.uniqueId)
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != ReportManager.SUBMIT_CHANNEL) return

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

        val playerName = doc.get("player")?.takeIf { it.isJsonPrimitive }?.asString
        val reason = doc.get("reason")?.takeIf { it.isJsonPrimitive }?.asString
        val shot = doc.get("shot")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        val handImage = doc.get("handImage")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false

        val result = plugin.reportManager.handleSubmit(player, playerName, reason, shot, handImage)
        val messageText = when (result) {
            is ReportManager.Result.Success -> result.message
            is ReportManager.Result.Error -> result.message
        }
        player.sendMessage(mm(messageText))
    }
}
