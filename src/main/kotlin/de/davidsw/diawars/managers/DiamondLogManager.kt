package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class DiamondAction {
    PICKUP, DROP, DESPAWN, CRAFT, PLACE, BREAK, REWARD, EXPLODE, TRADE
}

/**
 * Renders diamond log lines and hands them to [de.davidsw.diawars.stores.DiamondLogStore]
 * (SQLite, was `logs/YYYY-MM-DD.txt`). Formatting stays here because it is
 * presentation; the store only persists the finished line.
 */
class DiamondLogManager(private val plugin: Diawars) {
    private val dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val store get() = plugin.store.diamondLogStore

    fun getRecentEntries(limit: Int = 15): List<String> = store.recent(limit)

    fun log(
        action: DiamondAction,
        material: Material,
        amount: Int,
        playerId: UUID? = null,
        playerName: String? = null,
        location: Location? = null,
        details: String? = null,
    ) {
        if (amount <= 0) return
        if (material != Material.DIAMOND && material != Material.DIAMOND_BLOCK) return

        val now = LocalDateTime.now()
        val timestamp = now.format(timeFormatter)
        val name = playerName ?: playerId?.let { Bukkit.getOfflinePlayer(it).name } ?: "N/A"
        val locText = location?.let { "${it.world?.name ?: "?"} ${it.blockX},${it.blockY},${it.blockZ}" } ?: "N/A"

        val line = buildString {
            append("[$timestamp] ")
            append(action.name.padEnd(8))
            append(" | $material x$amount")
            append(" | Spieler: $name")
            append(" | Ort: $locText")
            if (details != null) append(" | $details")
        }

        try {
            store.append(now.atZone(ZoneId.systemDefault()).toEpochSecond(), now.toLocalDate().format(dayFormatter), line)
        } catch (e: Exception) {
            plugin.logger.severe("Could not write to diamond log: ${e.message}")
        }
    }

    fun flushSync() {
        store.flushNow(isFinal = true)
    }

    fun log(
        action: DiamondAction,
        material: Material,
        amount: Int,
        player: Player,
        location: Location? = player.location,
        details: String? = null,
    ) = log(action, material, amount, player.uniqueId, player.name, location, details)

    fun log(
        action: DiamondAction,
        material: Material,
        amount: Int,
        entity: Entity,
        location: Location? = entity.location,
        details: String? = null,
    ) = log(
        action, material, amount,
        playerId = (entity as? Player)?.uniqueId,
        playerName = if (entity is Player) entity.name else entity.type.name,
        location = location,
        details = details,
    )
}
