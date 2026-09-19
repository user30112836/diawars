package de.davidsw.diawars.managers

import de.davidsw.diawars.util.DiamondCounter
import de.davidsw.diawars.util.MaterialSets
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit.createInventory
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.meta.BlockStateMeta
import java.util.UUID
import kotlin.collections.mutableMapOf

class ShulkerAccessManager {
    private data class Session(val shulker: ShulkerBox, val inventory: Inventory, val slot: Int)
    private val openSessions = mutableMapOf<UUID, Session>()
    private val openEnderChest = mutableSetOf<UUID>()

    fun openHeldEnderChest(player: Player) {
        player.openInventory(player.enderChest)
        openEnderChest.add(player.uniqueId)
    }

    fun openHeldShulker(player: Player): Boolean {
        val slot = player.inventory.heldItemSlot
        val item = player.inventory.itemInMainHand
        val meta = item.itemMeta as? BlockStateMeta ?: return false
        val shulker = meta.blockState as? ShulkerBox ?: return false

        val inv = createInventory(null, shulker.inventory.size, mm("<dark_aqua>Shulker-Box</dark_aqua>"))
        inv.contents = shulker.inventory.contents

        openSessions[player.uniqueId] = Session(shulker, inv, slot)
        player.openInventory(inv)
        return true
    }

    /**
     * Vanilla-like nesting prevention for the virtual shulker view: no shulker box
     * may be placed into the opened shulker (it would be written back into its own
     * contents on close). Taking items out is unaffected.
     *
     * Additionally the source slot in the player inventory is locked: [handleClose]
     * writes the edited contents back into the item in [Session.slot], so moving
     * the opened shulker elsewhere would lose the edits or write them into the
     * wrong item.
     */
    fun handleClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = openSessions[player.uniqueId] ?: return
        if (event.view.topInventory !== session.inventory) return

        if (event.clickedInventory == session.inventory) {
            // Placing or swapping from the cursor into the shulker view.
            if (MaterialSets.isShulkerBox(event.cursor.type)) {
                event.isCancelled = true
                return
            }
            // Number-key swap pulls the hotbar item into the clicked shulker slot.
            // (If that hotbar slot is the locked source slot, it always holds the
            // opened shulker and is already cancelled by the check above.)
            if (event.action == InventoryAction.HOTBAR_SWAP || event.action == InventoryAction.HOTBAR_MOVE_AND_READD) {
                val hotbarItem = player.inventory.getItem(event.hotbarButton)
                if (hotbarItem != null && MaterialSets.isShulkerBox(hotbarItem.type)) {
                    event.isCancelled = true
                }
            }
        } else {
            // Double-click collect would gather the opened shulker off its slot into the cursor.
            if (event.action == InventoryAction.COLLECT_TO_CURSOR &&
                MaterialSets.isShulkerBox(event.cursor.type)
            ) {
                event.isCancelled = true
                return
            }
            // Any interaction with the source slot itself (pickup, place, drop,
            // shift-click, ...) would move the opened shulker away from its slot.
            if (event.slot == session.slot) {
                event.isCancelled = true
                lockHint(player)
                return
            }
            // Number-key swap while hovering the own inventory pulls the source
            // slot item out through the hotbar.
            if ((event.action == InventoryAction.HOTBAR_SWAP || event.action == InventoryAction.HOTBAR_MOVE_AND_READD) &&
                event.hotbarButton == session.slot
            ) {
                event.isCancelled = true
                lockHint(player)
                return
            }
            if (event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                // Shift-click from the player inventory would move the shulker into the view.
                val current = event.currentItem
                if (current != null && MaterialSets.isShulkerBox(current.type)) {
                    event.isCancelled = true
                }
            }
        }
    }

    private fun lockHint(player: Player) {
        player.sendMessage(mm("<red>Die geöffnete Shulker-Box kann nicht bewegt werden, solange sie geöffnet ist!</red>"))
    }

    /** Same nesting prevention for drags spanning into the shulker view. */
    fun handleDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = openSessions[player.uniqueId] ?: return
        if (event.view.topInventory !== session.inventory) return

        val topSize = session.inventory.size
        event.newItems.forEach { (slot, item) ->
            if (slot < topSize && MaterialSets.isShulkerBox(item.type)) {
                event.isCancelled = true
                return
            }
        }
    }

    fun handleClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (openEnderChest.remove(player.uniqueId) || openSessions.containsKey(player.uniqueId)) {
            val loc = player.location
            val world = loc.world
            // Use the closed inventory: player.openInventory.topInventory may already
            // point at the next/previous view during InventoryCloseEvent.
            val inventory = event.inventory

            var found  = 0
            inventory.contents.forEachIndexed { index, item ->
                if (item?.type in MaterialSets.DIAMOND_ITEMS) {
                    // Drop a copy and clear the slot (see ContainerExplosionManager).
                    world.dropItemNaturally(loc, item!!.clone())
                    found += DiamondCounter.countInItem(item)
                    inventory.setItem(index, null)
                }
            }
            when (found) {
                0 -> {}
                1 -> player.sendMessage(mm("<red>$found Diamant wurde entfernt!</red>"))
                else -> player.sendMessage(mm("<red>$found Diamanten wurden entfernt!</red>"))
            }
        }
        val session = openSessions.remove(player.uniqueId) ?: return

        if (event.inventory !== session.inventory) return
        val item = player.inventory.getItem(session.slot) ?: return
        val meta = item.itemMeta as? BlockStateMeta ?: return
        if (meta.blockState !is ShulkerBox) return

        session.shulker.inventory.contents = session.inventory.contents

        meta.blockState = session.shulker
        item.itemMeta = meta
        player.inventory.setItem(session.slot, item)
    }
}