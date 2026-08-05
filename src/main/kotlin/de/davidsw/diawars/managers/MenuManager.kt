package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MenuUtils
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
        val TITLE_EVENT = mm("<green>Events</green>")
        val TITLE_VAULT = mm("<green>Vault-Verwaltung</green>")
        val TITLE_VAULT_LIST = mm("<green>Vault-Liste</green>")
        val TITLE_MANUAL = mm("<green>Handbuch</green>")
    }

    fun openMainMenu(player: Player, memorize: Boolean = true) {
        val inv = openMenuInv(player, TITLE_MAIN, memorize)
        startUpdater({ plugin.menu.mainMenu.populateMainMenu(inv, player) }, player)
    }

    fun openBorderMenu(player: Player, memorize: Boolean = true) {
        val inv = openMenuInv(player, TITLE_BORDER, memorize)
        startUpdater({ plugin.menu.borderMenu.populateBorderMenu(inv, player) }, player)
    }

    fun openScoreboardMenu(player: Player, memorize: Boolean = true) {
        val inv = openMenuInv(player, TITLE_SCOREBOARD, memorize)
        startUpdater({ plugin.menu.scoreboardMenu.populateScoreboardMenu(inv, player) }, player)
    }

    fun openEventMenu(player: Player, memorize: Boolean = true) {
        val inv = openMenuInv(player, TITLE_EVENT, memorize)
        startUpdater({ plugin.menu.eventMenu.populateEventMenu(inv, player) }, player)
    }

    fun openVaultMenu(player: Player, memorize: Boolean = true) {
        val inv = openMenuInv(player, TITLE_VAULT, memorize)
        startUpdater({ plugin.menu.vaultMenu.populateVaultMenu(inv, player) }, player)
    }

    fun openVaultListMenu(player: Player, memorize: Boolean = true) {
        val inv = openMenuInv(player, TITLE_VAULT_LIST, memorize)
        startUpdater({ plugin.menu.vaultListMenu.populateVaultListMenu(inv, player) }, player)
    }

    fun openManualMenu(player: Player, memorize: Boolean = true){
        val inv = openMenuInv(player, TITLE_MANUAL, memorize)
        startUpdater({ plugin.menu.manualMenu.populateManualMenu(inv, player) }, player)
    }

    private fun openMenuInv(player: Player, title: Component, memorize: Boolean): Inventory {
        menuInvSwap[player.uniqueId] = true
        if (memorize) {
            val playerHistory: MutableList<Component> = history[player.uniqueId] ?: mutableListOf()
            val playerPosition = position[player.uniqueId] ?: -1
            while (playerHistory.size > playerPosition + 1) playerHistory.removeLast()
            playerHistory.add(title)
            history[player.uniqueId] = playerHistory
            position[player.uniqueId] = playerPosition + 1
        }
        val inv = Bukkit.createInventory(null, 54, title)
        fillBorder(inv, player)
        player.openInventory(inv)
        return inv
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
            TITLE_EVENT -> openEventMenu(player, false)
            TITLE_VAULT -> openVaultMenu(player, false)
            TITLE_VAULT_LIST -> openVaultListMenu(player, false)
            TITLE_MANUAL -> openManualMenu(player, false)
            else -> {}
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
            TITLE_EVENT -> openEventMenu(player, false)
            TITLE_VAULT -> openVaultMenu(player, false)
            TITLE_VAULT_LIST -> openVaultListMenu(player, false)
            TITLE_MANUAL -> openManualMenu(player, false)
            else -> {}
        }
    }

    private fun fillBorder(inv: Inventory, player: Player) {
        val pane = MenuUtils.item(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 0 until 9) inv.setItem(i, pane)
        for (i in 45 until 54) inv.setItem(i, pane)
        for (i in listOf(9, 18, 27, 36)) inv.setItem(i, pane)
        for (i in listOf(17, 26, 35, 44)) inv.setItem(i, pane)

        val playerHistory = history[player.uniqueId] ?: return
        val playerPosition = position[player.uniqueId] ?: return
        if (playerPosition > 0) {
            inv.setItem(48, MenuUtils.skullFromValue(
                value = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTg5NGRhNjk1OTY1NDhjNzRkOTY0ZTk5YjdhNGM5MjE3NjEwZjFhMjdjOTkxZGZhNDRkYWE1ZGE3NzFkODI1In19fQ==",
                name = mm("<gray><bold>← Zurück</bold></gray>"),
            ))
        }
        inv.setItem(49, MenuUtils.item(
            material = Material.STRUCTURE_VOID,
            name = mm("<gray><bold>Schließen</bold></gray>"),
        ))
        if (playerHistory.size > playerPosition + 1) {
            inv.setItem(50, MenuUtils.skullFromValue(
                value = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzc0NTNhYzJkN2E0MmE5MzY3NGZiZjYyY2FmMzMxYzcxNDNkY2JiY2M0ZjJiYWJiYzJmNjViOTUxNzQyMTQifX19",
                name = mm("<gray><bold>Weiter →</bold></gray>"),
            ))
        }
    }
}