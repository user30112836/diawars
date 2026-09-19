package de.davidsw.diawars.util

import org.bukkit.Bukkit
import java.util.UUID

object PlayerNameCache {
    private const val TTL_MILLIS = 60_000L
    private const val MAX_ENTRIES = 512

    private data class Entry(val name: String, val storedAt: Long)

    private val cache = mutableMapOf<UUID, Entry>()

    @Synchronized
    fun nameOf(playerId: UUID): String {
        val now = System.currentTimeMillis()
        cache[playerId]?.let { if (now - it.storedAt < TTL_MILLIS) return it.name }
        val name = Bukkit.getOfflinePlayer(playerId).name ?: "Unbekannt"
        cache[playerId] = Entry(name, now)
        if (cache.size > MAX_ENTRIES) {
            val expired = now - TTL_MILLIS
            val iterator = cache.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().value.storedAt < expired) iterator.remove()
            }
        }
        return name
    }
}
