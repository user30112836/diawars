package de.davidsw.diawars.util

import de.davidsw.diawars.managers.ManualSegment
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta

object BookUtils {
    fun openManualBook(player: Player, segment: ManualSegment) {
        val book = ItemStack(Material.WRITTEN_BOOK)
        val meta = book.itemMeta as BookMeta

        meta.title(mm(segment.title))
        meta.author(mm("Diawars"))

        val pages = segment.pages.ifEmpty {
            listOf("<gray>Für diesen Abschnitt ist noch kein Inhalt hinterlegt.</gray>")
        }
        pages.forEach { page -> meta.addPages(mm(page)) }

        book.itemMeta = meta
        player.openBook(book)
    }
}