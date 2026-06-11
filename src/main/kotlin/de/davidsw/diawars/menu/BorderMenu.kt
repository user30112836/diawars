package de.davidsw.diawars.menu

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.ColorParser
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MiniMessageHelper.mm
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
            name     = mm(if (pref.enabled) "<red><bold>Border deaktivieren</bold></red>" else "<green><bold>Border aktivieren</bold></green>"),
            lore     = listOf(
                mm("<gray>Aktuell: </gray>${if (pref.enabled) "<green>An</green>" else "<red>Aus</red>"}")
            ),
        ))

        // Particle type cycle
        val particleIdx = PARTICLE_TYPES.indexOf(pref.particleType).takeIf { it >= 0 } ?: 0
        val prevParticle = PARTICLE_TYPES[(particleIdx - 1 + PARTICLE_TYPES.size) % PARTICLE_TYPES.size]
        val nextParticle = PARTICLE_TYPES[(particleIdx + 1) % PARTICLE_TYPES.size]
        inv.setItem(SLOT_BORDER_PARTICLE, item(
            material = Material.BLAZE_POWDER,
            name     = mm("<yellow><bold>Partikel-Typ</bold></yellow>"),
            lore     = listOf(
                mm("<dark_gray>◄</dark_gray> <gray>$prevParticle</gray>"),
                mm("<white>▶ ${pref.particleType}</white>"),
                mm("<dark_gray>  $nextParticle </dark_gray><gray>►</gray>"),
                mm(""),
                mm("<yellow>Klicken zum Wechseln</yellow>"),
            ),
        ))

        // Color cycle
        val colorName  = colorToName(pref.color)
        val colorIdx   = NAMED_COLORS.indexOf(colorName).takeIf { it >= 0 } ?: 0
        val prevColor  = NAMED_COLORS[(colorIdx - 1 + NAMED_COLORS.size) % NAMED_COLORS.size]
        val nextColor  = NAMED_COLORS[(colorIdx + 1) % NAMED_COLORS.size]
        inv.setItem(SLOT_BORDER_COLOR, item(
            material = Material.FIREWORK_STAR,
            name     = mm("<light_purple><bold>Border-Farbe</bold></light_purple>"),
            lore     = listOf(
                mm("<dark_gray>◄</dark_gray> <gray>$prevColor</gray>"),
                mm("<white>▶ $colorName<white>"),
                mm("<dark_gray>  $nextColor </dark_gray><gray>►</gray>"),
                mm(""),
                mm("<light_purple>Klicken zum Wechseln</light_purple>"),
            ),
        ))

        // Render distance controls
        inv.setItem(SLOT_BORDER_DIST_DEC, item(
            material = Material.RED_STAINED_GLASS_PANE,
            name     = mm("<red><bold>◄ -8 Blöcke</bold></red>"),
            lore     = listOf(
                mm("<gray>Aktuell: ${pref.renderDistance} Blöcke</gray>"),
                mm("<dark_gray>Minimum: 8 Blöcke</dark_gray>"),
            ),
        ))
        inv.setItem(SLOT_BORDER_DIST_INC, item(
            material = Material.GREEN_STAINED_GLASS_PANE,
            name     = mm("<green><bold>+8 Blöcke ►</bold></green>"),
            lore     = listOf(
                mm("<gray>Aktuell: ${pref.renderDistance} Blöcke</gray>"),
                mm("<dark_gray>Maximum: 64 Blöcke</dark_gray>"),
            ),
        ))

        // Reset
        inv.setItem(SLOT_BORDER_RESET, item(
            material = Material.ANVIL,
            name     = mm("<red><bold>Auf Standard zurücksetzen</bold></red>"),
            lore     = listOf(mm("<dark_gray>Setzt alle Border-Einstellungen zurück</dark_gray>")),
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