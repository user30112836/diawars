package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MenuUtils.skullFromUrl
import de.davidsw.diawars.util.MiniMessageHelper.mm
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import java.util.UUID

class MenuManager(private val plugin: Diawars) {
    private var taskId = mutableMapOf<UUID, Int>()
    private var position = mutableMapOf<UUID, Int>()
    private val history = mutableMapOf<UUID, MutableList<Component>>()
    private val menuInvSwap = mutableMapOf<UUID, Boolean>()
    companion object {
        val TITLE_MAIN = mm("<green>Diawars</green>")
        val TITLE_BORDER = mm("<green>Border-Einstellungen</green>")
        val TITLE_SCOREBOARD = mm("<green>Sidebar-Einstellungen</green>")
    }

    fun openMainMenu(player: Player, memorize: Boolean = true) {
        menuInvSwap[player.uniqueId] = true
        if (memorize) {
            val playerHistory: MutableList<Component> = history[player.uniqueId] ?: mutableListOf()
            val playerPosition = position[player.uniqueId] ?: -1
            while (playerHistory.size > playerPosition + 1) playerHistory.removeLast()
            playerHistory.add(TITLE_MAIN)
            history[player.uniqueId] = playerHistory
            position[player.uniqueId] = playerPosition + 1
        }
        val inv = Bukkit.createInventory(null, 54, TITLE_MAIN)
        fillBorder(inv, player)
        player.openInventory(inv)
        startUpdater({ plugin.menu.mainMenu.populateMainMenu(inv, player) }, player)
    }

    fun openBorderMenu(player: Player, memorize: Boolean = true) {
        menuInvSwap[player.uniqueId] = true
        if (memorize) {
            val playerHistory: MutableList<Component> = history[player.uniqueId] ?: mutableListOf()
            val playerPosition = position[player.uniqueId] ?: -1
            while (playerHistory.size > playerPosition + 1) playerHistory.removeLast()
            playerHistory.add(TITLE_BORDER)
            history[player.uniqueId] = playerHistory
            position[player.uniqueId] = playerPosition + 1
        }
        val inv = Bukkit.createInventory(null, 54, TITLE_BORDER)
        fillBorder(inv, player)
        player.openInventory(inv)
        startUpdater({ plugin.menu.borderMenu.populateBorderMenu(inv, player) }, player)
    }

    fun openScoreboardMenu(player: Player, memorize: Boolean = true) {
        menuInvSwap[player.uniqueId] = true
        if (memorize) {
            val playerHistory: MutableList<Component> = history[player.uniqueId] ?: mutableListOf()
            val playerPosition = position[player.uniqueId] ?: -1
            while (playerHistory.size > playerPosition + 1) playerHistory.removeLast()
            playerHistory.add(TITLE_SCOREBOARD)
            history[player.uniqueId] = playerHistory
            position[player.uniqueId] = playerPosition + 1
        }
        val inv = Bukkit.createInventory(null, 54, TITLE_SCOREBOARD)
        fillBorder(inv, player)
        player.openInventory(inv)
        startUpdater({ plugin.menu.scoreboardMenu.populateScoreboardMenu(inv, player) }, player)
    }

    private fun startUpdater(func: () -> Unit, player: Player) {
        stopUpdater(player)
        func()
        taskId[player.uniqueId] = plugin.server.scheduler.runTaskTimer(plugin, Runnable { func() }, 0L, 20L).taskId // 20 Ticks = 1 Second
    }

    fun stopUpdater(player: Player) {
        val playerTaskId = taskId[player.uniqueId] ?: return
        if (playerTaskId != -1) {
            plugin.server.scheduler.cancelTask(playerTaskId)
            taskId[player.uniqueId] = -1
        }
    }

    fun navigate(player: Player, slot: Int) {
        when (slot) {
            48 -> back(player)
            49 -> close(player)
            50 -> next(player)
        }
    }

    fun emptyHistory(player: Player) {
        if (menuInvSwap[player.uniqueId] ?: false) {
            menuInvSwap[player.uniqueId] = false
            return
        }
        if (!history.containsKey(player.uniqueId) || !position.containsKey(player.uniqueId)) return
        history.remove(player.uniqueId)
        position.remove(player.uniqueId)
    }

    private fun back(player: Player) {
        val playerHistory = history[player.uniqueId] ?: return
        val playerPosition = position[player.uniqueId] ?: return
        if (playerPosition < 1) return
        val previousInventory = playerHistory[playerPosition - 1]
        position[player.uniqueId] = playerPosition - 1
        when (previousInventory) {
            TITLE_MAIN -> openMainMenu(player, false)
            TITLE_BORDER -> openBorderMenu(player, false)
            TITLE_SCOREBOARD -> openScoreboardMenu(player, false)
        }
    }

    private fun close(player: Player) {
        player.closeInventory()
        stopUpdater(player)
    }

    private fun next(player: Player) {
        val playerHistory = history[player.uniqueId] ?: return
        val playerPosition = position[player.uniqueId] ?: return
        if (playerPosition >= playerHistory.size - 1) return
        val nextInventory = playerHistory[playerPosition + 1]
        position[player.uniqueId] = playerPosition + 1
        when (nextInventory) {
            TITLE_MAIN -> openMainMenu(player, false)
            TITLE_BORDER -> openBorderMenu(player, false)
            TITLE_SCOREBOARD -> openScoreboardMenu(player, false)
        }
    }

    private fun fillBorder(inv: Inventory, player: Player) {
        val pane = item(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until 9) inv.setItem(i, pane)
        for (i in 45 until 54) inv.setItem(i, pane)
        for (i in listOf(9, 18, 27, 36)) inv.setItem(i, pane)
        for (i in listOf(17, 26, 35, 44)) inv.setItem(i, pane)

        val playerHistory = history[player.uniqueId] ?: return
        val playerPosition = position[player.uniqueId] ?: return
        if (playerPosition > 0) {
            inv.setItem(48, skullFromUrl(
                textureUrl = "http://textures.minecraft.net/texture/ca553ee38d14ee2e3526219215448e3466df8ee7c2199494944f42d74776",
                name = mm("<gray><bold>← Zurück</bold></gray>"),
            ))
        }
        inv.setItem(49, item(
            material = Material.STRUCTURE_VOID,
            name = mm("<gray><bold>Schließen</bold></gray>"),
        ))
        if (playerHistory.size > playerPosition + 1) {
            inv.setItem(50, skullFromUrl(
                textureUrl = "http://textures.minecraft.net/texture/ca553ee38d14ee2e3526219215448e3466df8ee7c2199494944f42d74776",
                name = mm("<gray><bold>Weiter →</bold></gray>"),
            ))
        }
    }
}