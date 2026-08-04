package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit.getOfflinePlayer
import org.bukkit.entity.Player

class BugManager(private val plugin: Diawars) {
    private val store get() = plugin.store.bugStore

    sealed class Result {
        data class Success(val message: String): Result()
        data class Error(val message: String): Result()
    }

    fun reportBug(player: Player, description: String): Result {
        if (description.isBlank()) {
            return Result.Error("<red>Bitte gib eine Beschreibung des Fehlers an!</red>")
        }

        val bug = store.addBug(player.uniqueId, description)

        plugin.server.onlinePlayers
            .filter { it.hasPermission("diawars.admin") }
            .forEach {
                it.sendMessage(mm("<yellow>⚠ <gold>${player.name}</gold> hat einen neuen Bug gemeldet (ID: <white>${bug.id}</white>). Nutze <white>/bug list</white> für Details.</yellow>"))
            }

        return Result.Success("<green>✓ Danke! Dein Bug wurde gemeldet (ID: <gold>${bug.id}</gold>).</green>")
    }

    fun formatList(): List<String> {
        val bugs = store.getUnresolved()
        if (bugs.isEmpty()) {
            return listOf("<gray>Es gibt derzeit keine offenen Bugs.</gray>")
        }

        val lines = mutableListOf("<gold>=== Offene Bugs (${bugs.size}) ===</gold>")
        bugs.forEach { bug ->
            val reporterName = getOfflinePlayer(bug.reporter).name ?: "Unbekannt"
            lines += "<gray>- <yellow>#${bug.id}</yellow> <white>$reporterName</white>: ${bug.description}</gray>"
        }
        return lines
    }

    fun resolveBug(id: String): Result {
        return if (store.resolve(id)) {
            Result.Success("<green>✓ Bug <gold>#$id</gold> wurde als behoben markiert.</green>")
        } else {
            Result.Error("<red>Dieser Bug existiert nicht oder wurde bereits behoben!</red>")
        }
    }

    fun handleAdminJoin(admin: Player) {
        if (!admin.hasPermission("diawars.admin")) return
        if (store.hasUnread(admin.uniqueId)) {
            val count = store.getUnresolved().size
            admin.sendMessage(mm("<yellow>⚠ Es gibt <gold>$count</gold> offene(n) Bug-Report(s). Nutze <white>/bug list</white> um sie anzusehen.</yellow>"))
        }
    }
}