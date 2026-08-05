package de.davidsw.diawars.menu

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.ManualSegment
import de.davidsw.diawars.util.BookUtils
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import java.util.UUID

class ManualMenu(private val plugin: Diawars) {
    companion object {
        private const val SLOT_OVERVIEW = 20
        private const val SLOT_FULL     = 22
        private const val SLOT_RULES    = 24

        private val SEGMENT_SLOTS = listOf(28, 29, 30, 31, 32, 33, 34)

        private val ALL_SLOTS = listOf(SLOT_OVERVIEW, SLOT_FULL, SLOT_RULES) + SEGMENT_SLOTS
    }

    private val segmentCache = mutableMapOf<UUID, List<ManualSegment>>()

    fun populateManualMenu(inv: Inventory, player: Player) {
        ALL_SLOTS.forEach { inv.setItem(it, null) }

        inv.setItem(SLOT_OVERVIEW, item(
            material = Material.KNOWLEDGE_BOOK,
            name = mm("<aqua><bold>Kurzübersicht</bold></aqua>"),
            lore = listOf(
                mm("<gray>Kurze Zusammenfassung aller</gray>"),
                mm("<gray>Funktionen des Plugins</gray>"),
                mm(""),
                mm("<yellow>Klicken zum Lesen</yellow>"),
            ),
        ))

        inv.setItem(SLOT_FULL, item(
            material = Material.WRITTEN_BOOK,
            name = mm("<gold><bold>Vollständiges Handbuch</bold></gold>"),
            lore = listOf(
                mm("<gray>Alle Abschnitte in einem Buch</gray>"),
                mm(""),
                mm("<yellow>Klicken zum Lesen</yellow>"),
            ),
        ))

        inv.setItem(SLOT_RULES, item(
            material = Material.OAK_SIGN,
            name = mm("<red><bold>Serverregeln</bold></red>"),
            lore = listOf(
                mm("<gray>Verhaltensregeln auf dem Server</gray>"),
                mm(""),
                mm("<yellow>Klicken zum Lesen</yellow>"),
            ),
        ))

        val segments = plugin.manualManager.getSegments()
        segmentCache[player.uniqueId] = segments

        if (segments.isEmpty()) {
            inv.setItem(SEGMENT_SLOTS[0], item(
                material = Material.GRAY_DYE,
                name = mm("<gray>Keine Abschnitte konfiguriert</gray>"),
            ))
        } else {
            val shown = segments.take(SEGMENT_SLOTS.size)
            shown.forEachIndexed { index, segment ->
                inv.setItem(SEGMENT_SLOTS[index], item(
                    material = Material.BOOK,
                    name = mm("<yellow><bold>${segment.title}</bold></yellow>"),
                    lore = listOf(
                        mm("<gray>Abschnitt des Handbuchs</gray>"),
                        mm(""),
                        mm("<yellow>Klicken zum Lesen</yellow>"),
                    ),
                ))
            }
        }
    }

    fun handleManualClick(player: Player, slot: Int) {
        when (slot) {
            SLOT_OVERVIEW -> {
                player.closeInventory()
                BookUtils.openManualBook(player, plugin.manualManager.getOverview())
            }

            SLOT_FULL -> {
                player.closeInventory()
                BookUtils.openManualBook(player, plugin.manualManager.getFullManual())
            }

            SLOT_RULES -> {
                player.closeInventory()
                BookUtils.openManualBook(player, plugin.manualManager.getRules())
            }

            in SEGMENT_SLOTS -> {
                val segments = segmentCache[player.uniqueId] ?: return
                val index = SEGMENT_SLOTS.indexOf(slot)
                val segment = segments.getOrNull(index) ?: return
                player.closeInventory()
                BookUtils.openManualBook(player, segment)
            }
        }
    }
}