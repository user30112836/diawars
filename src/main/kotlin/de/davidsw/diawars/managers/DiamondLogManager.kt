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

    private val logLock = Any()
    private val pendingLines = ArrayDeque<String>()
    @Volatile
    private var flushScheduled = false

    init {
        LogFiles.resolve(plugin, "").parentFile?.mkdirs()
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
            val cap = limit.coerceAtLeast(1)
            val window = ArrayDeque<String>(cap)
            val file = currentLogFile()
            if (file.exists()) {
                file.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (window.size >= cap) window.removeFirst()
                        window.addLast(line)
                    }
                }
            }
            synchronized(logLock) {
                for (line in pendingLines) {
                    if (window.size >= cap) window.removeFirst()
                    window.addLast(line)
                }
            }
            window.toList()
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
            enqueueLine(line)
        } catch (e: Exception) {
            plugin.logger.severe("Could not write to diamond log: ${e.message}")
        }
    }

    /** Queues a line and ensures exactly one async flush is scheduled. */
    private fun enqueueLine(line: String) {
        synchronized(logLock) { pendingLines.addLast(line) }
        scheduleFlush()
    }

    private fun scheduleFlush() {
        synchronized(logLock) {
            if (flushScheduled) return
            flushScheduled = true
        }
        try {
            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable { flushQueue() })
        } catch (e: IllegalStateException) {
            // Scheduler unavailable (shutdown): write synchronously instead of losing lines.
            flushQueue()
        } catch (e: IllegalArgumentException) {
            flushQueue()
        }
    }

    private fun flushQueue() {
        val batch: List<String>
        synchronized(logLock) {
            if (pendingLines.isEmpty()) {
                flushScheduled = false
                return
            }
            batch = pendingLines.toList()
            pendingLines.clear()
        }
        try {
            currentLogFile().appendText(batch.joinToString(System.lineSeparator()) + System.lineSeparator())
        } catch (e: Exception) {
            plugin.logger.severe("Could not write to diamond log: ${e.message}")
        }
        synchronized(logLock) {
            flushScheduled = false
            // Lines may have arrived while writing; flush again instead of losing them.
            if (pendingLines.isNotEmpty()) scheduleFlush()
        }
    }

    fun flushSync() {
        flushQueue()
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