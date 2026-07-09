package de.davidsw.diawars.menu

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.stores.ScoreboardComponent
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

class ScoreboardMenu(private val plugin: Diawars) {
    companion object {
        private const val SLOT_SIDEBAR_TOGGLE   = 20
        private const val SLOT_TEAM_DIAMONDS    = 22
        private const val SLOT_OPPONENTS_DIAMONDS = 23
        private const val SLOT_PLAYER_DIAMONDS  = 24
        private const val SLOT_ZONE_STATUS      = 31
        private const val SLOT_RESET            = 40

        private val COMPONENT_SLOTS = mapOf(
            SLOT_TEAM_DIAMONDS to ScoreboardComponent.TEAM_DIAMONDS,
            SLOT_OPPONENTS_DIAMONDS to ScoreboardComponent.OPPONENTS_DIAMONDS,
            SLOT_PLAYER_DIAMONDS to ScoreboardComponent.PLAYER_DIAMONDS,
            SLOT_ZONE_STATUS to ScoreboardComponent.ZONE_STATUS,
        )

        private val COMPONENT_MATERIAL = mapOf(
            ScoreboardComponent.TEAM_DIAMONDS to Material.DIAMOND,
            ScoreboardComponent.OPPONENTS_DIAMONDS to Material.DIAMOND_BLOCK,
            ScoreboardComponent.PLAYER_DIAMONDS to Material.NETHER_STAR,
            ScoreboardComponent.ZONE_STATUS to Material.COMPASS,
        )
    }

    fun populateScoreboardMenu(inv: Inventory, player: Player) {
        val store = plugin.store.scoreboardPreferencesStore
        val pref = store.getPreference(player.uniqueId)

        inv.setItem(SLOT_SIDEBAR_TOGGLE, item(
            material = if (pref.sidebarEnabled) Material.LIME_WOOL else Material.RED_WOOL,
            name = mm(if (pref.sidebarEnabled) "<red><bold>Sidebar deaktivieren</bold></red>" else "<green><bold>Sidebar aktivieren</bold></green>"),
            lore = listOf(
                mm("<gray>Status: </gray>${if (pref.sidebarEnabled) "<green>Aktiviert</green>" else "<red>Deaktiviert</red>"}"),
                mm("<gray>Klicken zum Umschalten</gray>"),
            ),
        ))

        for ((slot, component) in COMPONENT_SLOTS) {
            val enabled = component in pref.enabledComponents
            inv.setItem(slot, item(
                material = COMPONENT_MATERIAL[component] ?: Material.PAPER,
                name = mm(if (enabled) "<green><bold>${component.label}</bold></green>" else "<gray><bold>${component.label}</bold></gray>"),
                lore = listOf(
                    mm("<gray>Status: </gray>${if (enabled) "<green>Angezeigt</green>" else "<red>Ausgeblendet</red>"}"),
                    mm(""),
                    mm("<yellow>Klicken zum Umschalten</yellow>"),
                ),
                glow = enabled,
            ))
        }

        inv.setItem(SLOT_RESET, item(
            material = Material.ANVIL,
            name = mm("<red><bold>Auf Standard zurücksetzen</bold></red>"),
            lore = listOf(mm("<dark_gray>Setzt alle Sidebar-Einstellungen zurück</dark_gray>")),
        ))
    }

    fun handleScoreboardClick(player: Player, slot: Int, inv: Inventory) {
        val store = plugin.store.scoreboardPreferencesStore

        when (slot) {
            SLOT_SIDEBAR_TOGGLE -> {
                store.setSidebarEnabled(player.uniqueId, !store.isSidebarEnabled(player.uniqueId))
                populateScoreboardMenu(inv, player)
            }

            SLOT_RESET -> {
                store.resetToDefault(player.uniqueId)
                populateScoreboardMenu(inv, player)
            }

            else -> {
                val component = COMPONENT_SLOTS[slot] ?: return
                store.toggleComponent(player.uniqueId, component)
                populateScoreboardMenu(inv, player)
            }
        }
    }
}