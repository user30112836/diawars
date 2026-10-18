package de.davidsw.diawars.menu

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

class SettingsMenu(private val plugin: Diawars) {
    companion object {
        private const val SLOT_BORDER     = 21
        private const val SLOT_SCOREBOARD = 23
    }

    fun populateSettingsMenu(inv: Inventory, player: Player) {
        val borderPref = plugin.store.borderPreferencesStore.getPreference(player.uniqueId)
        inv.setItem(SLOT_BORDER, item(
            material = Material.END_ROD,
            name = mm("<aqua><bold>Border-Einstellungen</bold></aqua>"),
            lore = listOf(
                mm("<gray>Status: </gray>${if (borderPref.enabled) "<green>Aktiviert</green>" else "<red>Deaktiviert</red>"}"),
                mm("<gray>Partikel: </gray><white>${borderPref.particleType}</white>"),
                mm("<gray>Farbe: </gray><white>${colorToName(borderPref.color)}</white>"),
                mm("<gray>Sichtweite: </gray><white>${borderPref.renderDistance} Blöcke</white>"),
                mm(""),
                mm("<yellow>Klicken zum Öffnen</yellow>"),
            ),
            glow = false,
        ))

        val scoreboardPref = plugin.store.scoreboardPreferencesStore.getPreference(player.uniqueId)
        inv.setItem(SLOT_SCOREBOARD, item(
            material = Material.OAK_SIGN,
            name = mm("<aqua><bold>Sidebar-Einstellungen</bold></aqua>"),
            lore = listOf(
                mm("<gray>Status: </gray>${if (scoreboardPref.sidebarEnabled) "<green>Aktiviert</green>" else "<red>Deaktiviert</red>"}"),
                mm("<gray>Angezeigte Komponenten: </gray><white>${scoreboardPref.enabledComponents.size}/5</white>"),
                mm(""),
                mm("<yellow>Klicken zum Öffnen</yellow>"),
            ),
            glow = false,
        ))
    }

    fun handleSettingsClick(player: Player, slot: Int) {
        when (slot) {
            SLOT_BORDER -> plugin.menuManager.openBorderMenu(player)
            SLOT_SCOREBOARD -> plugin.menuManager.openScoreboardMenu(player)
        }
    }

    private fun colorToName(color: Color): String = when (color) {
        Color.RED    -> "RED"
        Color.BLUE   -> "BLUE"
        Color.GREEN  -> "GREEN"
        Color.YELLOW -> "YELLOW"
        Color.ORANGE -> "ORANGE"
        Color.PURPLE -> "PURPLE"
        Color.WHITE  -> "WHITE"
        Color.AQUA   -> "AQUA"
        else         -> "${color.red},${color.green},${color.blue}"
    }
}
