package de.davidsw.diawars.listeners

import com.google.gson.Gson
import com.google.gson.JsonObject
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

        private val GSON = Gson()

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

            checkModWhitelist(player, json)

            plugin.store.clientInfoStore.saveFromJson(
                player.uniqueId,
                json
            )
            plugin.store.clientInfoStore.addReported(player.uniqueId)
        } catch (e: Exception) {
            plugin.logger.warning(
                "Invalid client info message from ${player.name}: ${e.message}"
            )
        }
    }

    private fun extractModIds(json: String): List<String> {
        val root = GSON.fromJson(json, JsonObject::class.java) ?: return emptyList()
        val modsArray = root.getAsJsonArray("mods") ?: return emptyList()

        return modsArray.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            element.asJsonObject.get("id")?.takeIf { it.isJsonPrimitive }?.asString
        }
    }

    private fun checkModWhitelist(player: Player, json: String) {
        if (player.hasPermission("diawars.admin")) return

        val modIds = try {
            extractModIds(json)
        } catch (e: Exception) {
            plugin.logger.warning("Could not read mod list from client info for ${player.name}: ${e.message}")
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (!player.isOnline) return@Runnable
                player.kick(
                    mm(
                        "<red>Die Authentifizierung auf dem Server ist fehlgeschlagen. Bitte versuche es erneut!</red>"
                    )
                )
            })
            return
        }

        val disallowedMods = plugin.modWhitelistManager.findDisallowedMods(modIds)
        if (disallowedMods.isEmpty()) return
        val kickMessage = when (disallowedMods.size) {
            1 -> {
                val mod = disallowedMods.first()
                mm(
                    "<red>Die Mod <gold>$mod</gold> ist auf diesem Server nicht erlaubt! " +
                            "Bitte entferne sie und verbinde dich erneut.</red>"
                )
            }
            in 2..10 -> {
                val modList = disallowedMods.joinToString(", ")
                mm(
                    "<red>Die folgenden Mods sind auf diesem Server nicht erlaubt: <gold>$modList</gold></red>"
                )
            }
            else -> {
                val modList = disallowedMods.take(5).joinToString(", ")
                mm(
                    "<red>Die folgenden Mods sind auf diesem Server nicht erlaubt: <gold>$modList</gold> + ${disallowedMods.size}</red>"
                )
            }
        }

        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            player.kick(kickMessage)
        })
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