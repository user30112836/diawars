package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.DiamondCounter
import de.davidsw.diawars.util.MaterialSets
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BundleMeta

class DiamondBundleListener(private val plugin: Diawars): Listener {
    @EventHandler
    fun onPlayerDrop(event: PlayerDropItemEvent) {
        val player = event.player
        val droppedStack = event.itemDrop.itemStack
        if (!MaterialSets.isBundle(droppedStack.type)) return

        val result = stripBundle(droppedStack, player) ?: return
        val (removed, fixed) = result

        event.itemDrop.itemStack = fixed

        if (removed > 0) {
            player.sendMessage(mm("<red>Du kannst keine Diamanten in ein Bündel packen! ($removed entfernt)</red>"))
            player.updateInventory()
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val currentItem = event.currentItem ?: return
        val touchesBundle = MaterialSets.isBundle(event.cursor.type) || MaterialSets.isBundle(currentItem.type)
        if (!touchesBundle) return

        plugin.server.scheduler.runTask(plugin, Runnable {
            var stripped = 0

            val cursorItem = player.itemOnCursor
            val cursorResult = stripBundle(cursorItem, player)
            if (cursorResult != null) {
                val (removed, fixed) = cursorResult
                player.setItemOnCursor(fixed)
                stripped += removed
            }

            player.inventory.contents.forEachIndexed { index, item ->
                if (item != null) {
                    val result = stripBundle(item, player)
                    if (result != null) {
                        val (removed, fixed) = result
                        player.inventory.setItem(index, fixed)
                        stripped += removed
                    }
                }
            }

            if (stripped > 0) {
                player.sendMessage(mm("<red>Du kannst keine Diamanten in ein Bündel packen! ($stripped entfernt)</red>"))
                player.updateInventory()
            }
        })
    }

    private fun stripBundle(item: ItemStack, player: Player): Pair<Int, ItemStack>? {
        if (!MaterialSets.isBundle(item.type)) return null
        val meta = item.itemMeta as? BundleMeta ?: return null
        if (meta.items.none { it.type in MaterialSets.DIAMOND_ITEMS }) return null

        var removedCount = 0
        val kept = mutableListOf<ItemStack>()
        for (bundleItem in meta.items) {
            if (bundleItem.type in MaterialSets.DIAMOND_ITEMS) {
                removedCount += DiamondCounter.countInItem(bundleItem)
                val leftover = player.inventory.addItem(bundleItem.clone())
                leftover.values.forEach { player.location.world?.dropItemNaturally(player.location, it) }
            } else {
                kept.add(bundleItem)
            }
        }

        val fixed = item.clone()
        val fixedMeta = fixed.itemMeta as BundleMeta
        fixedMeta.setItems(kept)
        fixed.itemMeta = fixedMeta

        return removedCount to fixed
    }
}