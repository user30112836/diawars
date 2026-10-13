package de.davidsw.diawars.menu

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.ImageManager
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MiniMessageHelper.escape
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import java.util.UUID

class ImageMenu(private val plugin: Diawars) {
    companion object {
        private val LIST_SLOTS = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
        )
        private const val SLOT_PREV_PAGE = 37
        private const val SLOT_PAGE_INFO = 40
        private const val SLOT_NEXT_PAGE = 43

        private val ALL_SLOTS = LIST_SLOTS + listOf(SLOT_PREV_PAGE, SLOT_PAGE_INFO, SLOT_NEXT_PAGE)
        private val PAGE_SIZE = LIST_SLOTS.size
    }

    private val currentPage = mutableMapOf<UUID, Int>()
    private val pageCache = mutableMapOf<UUID, List<String>>()

    fun clearCache(playerId: UUID) {
        currentPage.remove(playerId)
        pageCache.remove(playerId)
    }

    fun resetPage(player: Player) {
        currentPage[player.uniqueId] = 0
    }

    fun populateImageMenu(inv: Inventory, player: Player) {
        ALL_SLOTS.forEach { inv.setItem(it, null) }

        val images = plugin.imageManager.listImages(player.uniqueId)
        val limit = plugin.store.imageLimitStore.getLimit(player.uniqueId)
        val totalPages = if (images.isEmpty()) 1 else ((images.size - 1) / PAGE_SIZE) + 1
        val page = (currentPage[player.uniqueId] ?: 0).coerceIn(0, totalPages - 1)
        currentPage[player.uniqueId] = page

        if (images.isEmpty()) {
            pageCache[player.uniqueId] = emptyList()
            inv.setItem(LIST_SLOTS[0], item(
                material = Material.GRAY_DYE,
                name = mm("<gray>Noch keine Bilder hochgeladen</gray>"),
                lore = listOf(
                    mm("<gray>Lade Bilder über die Diawars-Client-Mod hoch</gray>"),
                    mm("<gray>(Datei wählen, wird auf 128x128 skaliert)</gray>"),
                    mm(""),
                    mm("<gray>Oder nutze <yellow>/image &lt;datei&gt;</yellow><gray> für vorhandene Bilder</gray>"),
                ),
            ))
        } else {
            val pageImages = images.drop(page * PAGE_SIZE).take(PAGE_SIZE)
            pageCache[player.uniqueId] = pageImages
            pageImages.forEachIndexed { index, file ->
                inv.setItem(LIST_SLOTS[index], item(
                    material = Material.FILLED_MAP,
                    name = mm("<white><bold>${escape(file)}</bold></white>"),
                    lore = listOf(
                        mm("<gray>Klicken für Karte mit diesem Bild</gray>"),
                    ),
                    glow = false,
                ))
            }
        }

        inv.setItem(SLOT_PREV_PAGE, item(
            material = if (page > 0) Material.ARROW else Material.GRAY_DYE,
            name = if (page > 0) mm("<yellow><bold>◄ Vorherige Seite</bold></yellow>") else mm("<dark_gray>Erste Seite</dark_gray>"),
        ))
        inv.setItem(SLOT_PAGE_INFO, item(
            material = Material.MAP,
            name = mm("<white><bold>Seite ${page + 1} / $totalPages</bold></white>"),
            lore = listOf(
                mm("<gray>${images.size}/$limit Bilder</gray>"),
                mm("<dark_gray>Upload über die Client-Mod (diawars:image)</dark_gray>"),
            ),
        ))
        inv.setItem(SLOT_NEXT_PAGE, item(
            material = if (page < totalPages - 1) Material.ARROW else Material.GRAY_DYE,
            name = if (page < totalPages - 1) mm("<yellow><bold>Nächste Seite ►</bold></yellow>") else mm("<dark_gray>Letzte Seite</dark_gray>"),
        ))
    }

    fun handleImageClick(player: Player, slot: Int, inv: Inventory) {
        when (slot) {
            SLOT_PAGE_INFO -> resetPage(player)

            SLOT_PREV_PAGE -> {
                val page = currentPage[player.uniqueId] ?: 0
                if (page > 0) {
                    currentPage[player.uniqueId] = page - 1
                    populateImageMenu(inv, player)
                }
            }

            SLOT_NEXT_PAGE -> {
                val images = plugin.imageManager.listImages(player.uniqueId)
                val totalPages = if (images.isEmpty()) 1 else ((images.size - 1) / PAGE_SIZE) + 1
                val page = currentPage[player.uniqueId] ?: 0
                if (page < totalPages - 1) {
                    currentPage[player.uniqueId] = page + 1
                    populateImageMenu(inv, player)
                }
            }

            in LIST_SLOTS -> {
                val files = pageCache[player.uniqueId] ?: return
                val index = LIST_SLOTS.indexOf(slot)
                val file = files.getOrNull(index) ?: return
                val result = plugin.imageManager.giveImage(player, player.uniqueId, file)
                when (result) {
                    is ImageManager.Result.Success -> player.sendMessage(mm(result.message))
                    is ImageManager.Result.Error -> player.sendMessage(mm(result.message))
                }
            }
        }
    }
}
