package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.StoreFiles
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class DiamondAction {
    PICKUP, DROP, DESPAWN, CRAFT, PLACE, BREAK, REWARD
}

class DiamondLogManager(private val plugin: Diawars) {
    private val logFile: File = StoreFiles.resolve(plugin, "diamond_log.txt")
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

    init {
        if (!logFile.exists()) {
            logFile.parentFile.mkdirs()
            logFile.createNewFile()
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

        val timestamp = LocalDateTime.now().format(formatter)
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
            logFile.appendText(line + System.lineSeparator())
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
}