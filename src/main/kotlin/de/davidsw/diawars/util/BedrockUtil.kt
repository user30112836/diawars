package de.davidsw.diawars.util

import org.bukkit.entity.Player
import java.util.UUID

/**
 * Detects GeyserMC/Bedrock players without a hard dependency on Floodgate or
 * Geyser. Both APIs are looked up via reflection so the plugin still enables
 * on servers without them (everyone is then treated as Java).
 *
 * Detection order:
 * 1. Floodgate API (`org.geysermc.floodgate.api.FloodgateApi.isFloodgatePlayer`)
 * 2. Geyser API (`org.geysermc.geyser.api.GeyserApi.connectionByUuid`)
 */
object BedrockUtil {

    private var floodgateChecked = false
    private var floodgateApi: Any? = null
    private var isFloodgatePlayer: java.lang.reflect.Method? = null

    private var geyserChecked = false
    private var geyserApi: Any? = null
    private var connectionByUuid: java.lang.reflect.Method? = null

    fun isBedrock(player: Player): Boolean = isBedrock(player.uniqueId)

    fun isBedrock(playerId: UUID): Boolean {
        if (isFloodgatePlayer(playerId)) return true
        if (isGeyserPlayer(playerId)) return true
        return false
    }

    private fun isFloodgatePlayer(playerId: UUID): Boolean {
        if (!floodgateChecked) {
            floodgateChecked = true
            try {
                val apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
                floodgateApi = apiClass.getMethod("getInstance").invoke(null)
                isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID::class.java)
            } catch (_: Exception) {
                floodgateApi = null
            }
        }
        val api = floodgateApi ?: return false
        return try {
            isFloodgatePlayer?.invoke(api, playerId) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun isGeyserPlayer(playerId: UUID): Boolean {
        if (!geyserChecked) {
            geyserChecked = true
            try {
                val apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi")
                geyserApi = apiClass.getMethod("api").invoke(null)
                connectionByUuid = apiClass.getMethod("connectionByUuid", UUID::class.java)
            } catch (_: Exception) {
                geyserApi = null
            }
        }
        val api = geyserApi ?: return false
        return try {
            connectionByUuid?.invoke(api, playerId) != null
        } catch (_: Exception) {
            false
        }
    }
}
