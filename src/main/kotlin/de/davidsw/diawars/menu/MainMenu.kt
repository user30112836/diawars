package de.davidsw.diawars.menu

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.PvPManager
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MiniMessageHelper.mm
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

class MainMenu(private val plugin: Diawars) {
    companion object {
        private const val SLOT_PVP_TOGGLE   = 20
        private const val SLOT_BORDER       = 29
        private const val SLOT_SCORES       = 31
        private const val SLOT_ZONE_INFO    = 33
    }

    fun populateMainMenu(inv: Inventory, player: Player) {
        val pvpEnabled = plugin.store.pvpStatusStore.isPvPEnabled(player.uniqueId)
        val hasPending = plugin.store.pvpStatusStore.hasPendingToggle(player.uniqueId)
        val team = plugin.teamManager.getPlayerTeam(player.uniqueId)
        val inZone = plugin.zoneManager.isInOwnZone(player)

        // PvP toggle
        if (hasPending) {
            inv.setItem(SLOT_PVP_TOGGLE, item(
                material = Material.BARRIER,
                name = mm(if (plugin.store.pvpStatusStore.getToggleDestination(player.uniqueId) ?: return) "<yellow><bold>PvP Aktivierung abbrechen</bold></yellow>" else "<yellow><bold>PvP Aktivierung</bold></yellow>"),
                lore = formatPvPToggleCancelLore(player),
                glow = false,
            ))
        } else {
            inv.setItem(SLOT_PVP_TOGGLE, item(
                material = if (pvpEnabled) Material.RED_WOOL else Material.GREEN_WOOL,
                name = mm(if (pvpEnabled) "<red><bold>PvP deaktivieren</bold></red>" else "<green><bold>PvP aktivieren</bold></green>"),
                lore = listOf(
                    mm("<gray>Status: </gray>${if (pvpEnabled) "<green>Aktiviert</green>" else "<red>Deaktiviert</red>"}"),
                    mm("<gray>Klicken zum Umschalten</gray>"),
                    mm("<gray>(5 Min. Verzögerung)</gray"),
                ),
                glow = false,
            ))
        }

        // Border settings
        val borderPref = plugin.store.borderPreferencesStore.getPreference(player.uniqueId)
        inv.setItem(SLOT_BORDER, item(
            material = Material.END_ROD,
            name     = mm("<aqua><bold>Border-Einstellungen</bold></aqua>"),
            lore     = listOf(
                mm("<gray>Status: </gray>${if (borderPref.enabled) "<green>Aktiviert</green>" else "<red>Deaktiviert</red>"}"),
                mm("<gray>Partikel: </gray><white>${borderPref.particleType}</white>"),
                mm("<gray>Farbe: </gray><white>${colorToName(borderPref.color)}</white>"),
                mm("<gray>Sichtweite: </gray><white>${borderPref.renderDistance} Blöcke</white>"),
                mm(""),
                mm("<yellow>Klicken zum Öffnen</yellow>"),
            ),
            glow = false,
        ))

        // Scores
        val playerDia = plugin.store.playerDiamondStore.getStoredCount(player.uniqueId)
        val teamDia   = if (team != null) plugin.store.playerDiamondStore.getTotalTeamCount(team) else 0
        inv.setItem(SLOT_SCORES, item(
            material = Material.DIAMOND,
            name = mm("<aqua><bold>Diamanten</bold></aqua>"),
            lore = listOf(
                mm("<gray>Deine: <aqua>$playerDia</aqua></gray>"),
                mm(if (team != null) "<gray>${team.displayName}: </gray><aqua>$$teamDia</aqua>" else "<dark_gray>Kein Team</dark_gray>"),
            ),
            glow = playerDia > 0,
        ))

        // Zone info
        inv.setItem(SLOT_ZONE_INFO, item(
            material = Material.COMPASS,
            name     = mm("<green><bold>Zonen-Info</bold></green>"),
            lore     = listOf(
                mm("<gray>Team: </gray><white>${team?.displayName ?: "Kein Team"}</white>"),
                mm("<gray>In eigener Zone: ${if (inZone) "<green>Ja</green>" else "<red>Nein</red>"}</gray>"),
            ),
            glow = false,
        ))
    }

    fun handleMainClick(player: Player, slot: Int, inv: Inventory) {
        when (slot) {
            SLOT_PVP_TOGGLE -> {
                val result = plugin.pvpManager.togglePvP(player.uniqueId)
                if (result == PvPManager.ToggleResult.ALREADY_PENDING) {
                    plugin.pvpManager.cancelToggle(player.uniqueId)
                }
                populateMainMenu(inv, player)
            }

            SLOT_BORDER -> plugin.menuManager.openBorderMenu(player)

            SLOT_SCORES -> {
                player.closeInventory()
                plugin.scoresManager.handleInfo(player)
            }

            SLOT_ZONE_INFO -> {
                player.closeInventory()
                val team = plugin.teamManager.getPlayerTeam(player.uniqueId)
                if (team != null) {
                    val inZone = plugin.zoneManager.isInOwnZone(player)
                    player.sendMessage(mm("<gray>Team: </gray><gold>${team.displayName}</gold>"))
                    player.sendMessage(mm("<gray>In eigener Zone: </gray>${if (inZone) "<green>Ja</green>" else "<red>Nein</red>"}"))
                } else {
                    player.sendMessage(mm("<gray>Du bist in keinem Team</gray>"))
                }
            }
        }
    }

    private fun formatPvPToggleCancelLore(player: Player): List<Component> {
        val remaining = plugin.store.pvpStatusStore.getRemainingTime(player.uniqueId)
        val minutes = remaining / 60
        val seconds = remaining % 60
        val duration = buildString {
            if (minutes.toInt() != 0) append(" ${minutes}m")
            if (seconds.toInt() != 0) append(" ${seconds}s")
        }
        return listOf(mm("<gray>Verbleibend: </gray><yellow>${duration}</yellow>"))
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