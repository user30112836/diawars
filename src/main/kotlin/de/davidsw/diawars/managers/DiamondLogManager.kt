package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.LogFiles
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class DiamondAction {
    PICKUP, DROP, DESPAWN, CRAFT, PLACE, BREAK, REWARD, EXPLODE, TRADE
}

class DiamondLogManager(private val plugin: Diawars) {
    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    init {
        LogFiles.resolve(plugin, "").parentFile.mkdir()
    }

    private fun currentLogFile(): File {
        val fileName = "${LocalDate.now().format(fileNameFormatter)}.txt"
        val file = LogFiles.resolve(plugin, fileName)
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }
        return file
    }

    fun getRecentEntries(limit: Int = 15): List<String> {
        return try {
            currentLogFile().readLines().takeLast(limit)
        } catch (e: Exception) {
            plugin.logger.severe("Could not read diamond log: ${e.message}")
            emptyList()
        }
    }

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

        val timestamp = LocalDateTime.now().format(timeFormatter)
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
            currentLogFile().appendText(line + System.lineSeparator())
        } catch (e: Exception) {
            plugin.logger.severe("Could not write to diamond log: ${e.message}")
        }
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