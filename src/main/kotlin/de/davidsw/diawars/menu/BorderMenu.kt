package de.davidsw.diawars.menu

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.ColorParser
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MenuUtils.skullFromUrl
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

class BorderMenu(private val plugin: Diawars) {
    companion object {
        private const val SLOT_BORDER_PARTICLE = 21
        private const val SLOT_BORDER_COLOR = 23
        private const val SLOT_BORDER_DIST_DEC = 29
        private const val SLOT_BORDER_TOGGLE = 31
        private const val SLOT_BORDER_DIST_INC = 33
        private const val SLOT_BORDER_RESET = 40

        private val PARTICLE_TYPES = listOf("REDSTONE", "FLAME", "GLOW", "END_ROD", "SOUL", "ENCHANT", "PORTAL", "HEART")
        private val NAMED_COLORS   = listOf("YELLOW", "RED", "BLUE", "GREEN", "ORANGE", "PURPLE", "WHITE", "AQUA")
    }

    fun populateBorderMenu(inv: Inventory, player: Player) {
        val pref = plugin.store.borderPreferencesStore.getPreference(player.uniqueId)

        // On/Off toggle
        inv.setItem(SLOT_BORDER_TOGGLE, item(
            material = if (pref.enabled) Material.LIME_WOOL else Material.RED_WOOL,
            name     = if (pref.enabled) "§c§lBorder deaktivieren" else "§a§lBorder aktivieren",
            lore     = listOf("§7Aktuell: ${if (pref.enabled) "§aAn" else "§cAus"}"),
        ))

        // Particle type cycle
        val particleIdx = PARTICLE_TYPES.indexOf(pref.particleType).takeIf { it >= 0 } ?: 0
        val prevParticle = PARTICLE_TYPES[(particleIdx - 1 + PARTICLE_TYPES.size) % PARTICLE_TYPES.size]
        val nextParticle = PARTICLE_TYPES[(particleIdx + 1) % PARTICLE_TYPES.size]
        inv.setItem(SLOT_BORDER_PARTICLE, item(
            material = Material.BLAZE_POWDER,
            name     = "§e§lPartikel-Typ",
            lore     = listOf(
                "§8◄ §7$prevParticle",
                "§f▶ ${pref.particleType}",
                "§8  $nextParticle §7►",
                "",
                "§eKlicken zum Wechseln",
            ),
        ))

        // Color cycle
        val colorName  = colorToName(pref.color)
        val colorIdx   = NAMED_COLORS.indexOf(colorName).takeIf { it >= 0 } ?: 0
        val prevColor  = NAMED_COLORS[(colorIdx - 1 + NAMED_COLORS.size) % NAMED_COLORS.size]
        val nextColor  = NAMED_COLORS[(colorIdx + 1) % NAMED_COLORS.size]
        inv.setItem(SLOT_BORDER_COLOR, item(
            material = Material.FIREWORK_STAR,
            name     = "§d§lBorder-Farbe",
            lore     = listOf(
                "§8◄ §7$prevColor",
                "§f▶ $colorName",
                "§8  $nextColor §7►",
                "",
                "§dKlicken zum Wechseln",
            ),
        ))

        // Render distance controls
        inv.setItem(SLOT_BORDER_DIST_DEC, item(
            material = Material.RED_STAINED_GLASS_PANE,
            name     = "§c§l◄ -8 Blöcke",
            lore     = listOf(
                "§7Aktuell: ${pref.renderDistance} Blöcke",
                "§8Minimum: 8 Blöcke"
            ),
        ))
        inv.setItem(SLOT_BORDER_DIST_INC, item(
            material = Material.GREEN_STAINED_GLASS_PANE,
            name     = "§a§l+8 Blöcke ►",
            lore     = listOf(
                "§7Aktuell: ${pref.renderDistance} Blöcke",
                "§8Maximum: 64 Blöcke"
            ),
        ))

        // Reset
        inv.setItem(SLOT_BORDER_RESET, item(
            material = Material.ANVIL,
            name     = "§c§lAuf Standard zurücksetzen",
            lore     = listOf("§8Setzt alle Border-Einstellungen zurück"),
        ))
    }

    fun handleBorderClick(player: Player, slot: Int, inv: Inventory) {
        val prefs = plugin.store.borderPreferencesStore

        when (slot) {
            SLOT_BORDER_TOGGLE -> {
                prefs.setEnabled(player.uniqueId, !prefs.getPreference(player.uniqueId).enabled)
                populateBorderMenu(inv, player)
            }

            SLOT_BORDER_PARTICLE -> {
                val current = prefs.getPreference(player.uniqueId).particleType
                val next    = PARTICLE_TYPES[(PARTICLE_TYPES.indexOf(current) + 1) % PARTICLE_TYPES.size]
                prefs.setParticleType(player.uniqueId, next)
                populateBorderMenu(inv, player)
            }

            SLOT_BORDER_COLOR -> {
                val current     = prefs.getPreference(player.uniqueId).color
                val currentName = colorToName(current)
                val next        = NAMED_COLORS[(NAMED_COLORS.indexOf(currentName) + 1) % NAMED_COLORS.size]
                prefs.setColor(player.uniqueId, ColorParser.parseOrDefault(next))
                populateBorderMenu(inv, player)
            }

            SLOT_BORDER_DIST_DEC -> {
                val current = prefs.getPreference(player.uniqueId).renderDistance
                prefs.setRenderDistance(player.uniqueId, (current - 8).coerceAtLeast(8))
                populateBorderMenu(inv, player)
            }

            SLOT_BORDER_DIST_INC -> {
                val current = prefs.getPreference(player.uniqueId).renderDistance
                prefs.setRenderDistance(player.uniqueId, (current + 8).coerceAtMost(64))
                populateBorderMenu(inv, player)
            }

            SLOT_BORDER_RESET -> {
                prefs.resetToDefault(player.uniqueId)
                populateBorderMenu(inv, player)
            }
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