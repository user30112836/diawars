package de.davidsw.diawars.menu

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.commands.ScoresCommand
import de.davidsw.diawars.managers.PvPManager
import de.davidsw.diawars.util.MenuUtils.item
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
        val team       = plugin.teamManager.getPlayerTeam(player.uniqueId)
        val inZone     = plugin.zoneManager.isInOwnZone(player)

        // PvP toggle
        if (hasPending) {
            inv.setItem(SLOT_PVP_TOGGLE, item(
                material = Material.BARRIER,
                name = if (plugin.store.pvpStatusStore.getToggleDestination(player.uniqueId) ?: return) "§e§lPvP Aktivierung abbrechen" else "§e§lPvP Deaktivierung abbrechen",
                lore = formatPvPToggleCancelLore(player),
                glow = false,
            ))
        } else {
            inv.setItem(SLOT_PVP_TOGGLE, item(
                material = if (pvpEnabled) Material.RED_WOOL else Material.GREEN_WOOL,
                name = if (pvpEnabled) "§c§lPvP deaktivieren" else "§a§lPvP aktivieren",
                lore = listOf(
                    "§7Status: ${if (pvpEnabled) "§aAktiviert" else "§cDeaktiviert"}",
                    "§7Klicken zum Umschalten",
                    "§7(5 Min. Verzögerung)",
                ),
                glow = false,
            ))
        }

        // Border settings
        val borderPref = plugin.store.borderPreferencesStore.getPreference(player.uniqueId)
        inv.setItem(SLOT_BORDER, item(
            material = Material.END_ROD,
            name     = "§b§lBorder-Einstellungen",
            lore     = listOf(
                "§7Status: ${if (borderPref.enabled) "§aAktiviert" else "§cDeaktiviert"}",
                "§7Partikel: §f${borderPref.particleType}",
                "§7Farbe: §f${colorToName(borderPref.color)}",
                "§7Sichtweite: §f${borderPref.renderDistance} Blöcke",
                "",
                "§eKlicken zum Öffnen",
            ),
            glow = false,
        ))

        // Scores
        val playerDia = plugin.store.playerDiamondStore.getStoredCount(player.uniqueId)
        val teamDia   = if (team != null) plugin.store.playerDiamondStore.getTotalTeamCount(team) else 0
        inv.setItem(SLOT_SCORES, item(
            material = Material.DIAMOND,
            name = "§b§lDiamanten",
            lore = listOf(
                "§7Deine: §b$playerDia",
                if (team != null) "§7${team.displayName}: §b$teamDia" else "§8Kein Team",
            ),
            glow = playerDia > 0,
        ))

        // Zone info
        inv.setItem(SLOT_ZONE_INFO, item(
            material = Material.COMPASS,
            name     = "§a§lZonen-Info",
            lore     = listOf(
                "§7Team: §f${team?.displayName ?: "Kein Team"}",
                "§7In eigener Zone: ${if (inZone) "§aJa" else "§cNein"}",
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
                    player.sendMessage("§7Team: §6${team.displayName}")
                    player.sendMessage("§7In eigener Zone: ${if (inZone) "§aJa" else "§cNein"}")
                } else {
                    player.sendMessage("§7Du bist in keinem Team.")
                }
            }
        }
    }

    private fun formatPvPToggleCancelLore(player: Player): List<String> {
        val remaining = plugin.store.pvpStatusStore.getRemainingTime(player.uniqueId)
        val minutes = remaining / 60
        val seconds = remaining % 60
        return if (minutes.toInt() == 0) {
            listOf("§7Verbleibend: §e${seconds}s")
        } else {
            listOf("§7Verbleibend: §e${minutes}m ${seconds}s")
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