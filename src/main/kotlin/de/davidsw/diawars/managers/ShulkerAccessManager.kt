package de.davidsw.diawars.managers

import de.davidsw.diawars.util.DiamondCounter
import de.davidsw.diawars.util.MaterialSets
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit.createInventory
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent
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

    fun handleClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (openEnderChest.remove(player.uniqueId) || openSessions.containsKey(player.uniqueId)) {
            val loc = player.location
            val world = loc.world
            val inventory = player.openInventory.topInventory

            var found  = 0
            inventory.forEach { item ->
                if (item?.type in MaterialSets.DIAMOND_ITEMS) {
                    world.dropItemNaturally(loc, item ?: return@forEach)
                    found += DiamondCounter.countInItem(item)
                    item.amount = 0
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