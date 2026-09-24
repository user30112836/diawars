package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MaterialSets
import org.bukkit.Material
import org.bukkit.block.DoubleChest
import org.bukkit.entity.Entity
import org.bukkit.entity.HumanEntity
import org.bukkit.inventory.BlockInventoryHolder
import org.bukkit.inventory.InventoryHolder

class ContainerExplosionManager(private val plugin: Diawars) {

    fun explodeHolder(holder: InventoryHolder) {
        when (holder) {
            is DoubleChest -> explodeDoubleChest(holder)
            is BlockInventoryHolder -> explodeContainer(holder)
            // Players hold diamonds normally: exploding them on inventory close
            // would kill legit play. Only non-player entities (minecarts, mules, ...)
            // are containers that must explode.
            is HumanEntity -> return
            is Entity -> explodeEntity(holder)
            else -> {
                plugin.logger.warning("[ContainerExplosion] Unhandled InventoryHolder type: ${holder::class.simpleName}")
            }
        }
    }

    fun explodeEntity(entity: Entity) {
        val loc = entity.location.add(0.5, 0.5, 0.5)
        val world = loc.world

        plugin.server.scheduler.runTask(plugin, Runnable {
            world.createExplosion(loc, 4f)
        })
    }

    fun explodeDoubleChest(holder: DoubleChest) {
        // DoubleChest is the holder for two joined chests: neither half alone
        // represents the opened 54-slot inventory, so explode once at the
        // combined location instead of one explosion per half.
        val loc = (holder.inventory.location ?: holder.location).add(0.5, 0.5, 0.5)
        val world = loc.world

        plugin.server.scheduler.runTask(plugin, Runnable {
            world.createExplosion(loc, 4f)
        })
    }

    fun explodeContainer(holder: BlockInventoryHolder) {
        if (MaterialSets.isShulkerBox(holder.block.type)) {
            explodeShulker(holder)
            return
        }

        val block = holder.block
        val loc = block.location.add(0.5, 0.5, 0.5)
        val world = loc.world

        plugin.server.scheduler.runTask(plugin, Runnable {
            world.createExplosion(loc, 4f)
        })
    }

    fun explodeEnderChest(player: HumanEntity) {
        val block = player.openInventory.topInventory.location?.block ?: return
        val loc = block.location.add(0.5, 0.5, 0.5)
        val world = loc.world
        val inventory = player.openInventory.topInventory

        inventory.contents.forEachIndexed { index, item ->
            if (item?.type in MaterialSets.DIAMOND_ITEMS) {
                // Drop a copy and clear the slot: zeroing the live stack after
                // handing it to the drop risks voiding the diamonds depending on
                // whether the implementation clones, and leaves ghost stacks.
                world.dropItemNaturally(loc, item!!.clone())
                inventory.setItem(index, null)
            }
        }

        plugin.server.scheduler.runTask(plugin, Runnable {
            block.type = Material.AIR
            block.world.createExplosion(loc,4f)
        })
    }

    fun explodeShulker(holder: BlockInventoryHolder) {
        val block = holder.block
        val loc = block.location.add(0.5, 0.5, 0.5)
        val world = loc.world

        holder.inventory.contents.forEachIndexed { index, item ->
            if (item?.type in MaterialSets.DIAMOND_ITEMS) {
                // See explodeEnderChest: drop a copy, then clear the slot.
                world.dropItemNaturally(loc, item!!.clone())
                holder.inventory.setItem(index, null)
            }
        }

        plugin.server.scheduler.runTask(plugin, Runnable {
            world.createExplosion(loc, 4f)
        })
    }
}