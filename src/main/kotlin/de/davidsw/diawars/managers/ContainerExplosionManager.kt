package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MaterialSets
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.HumanEntity
import org.bukkit.inventory.BlockInventoryHolder
import org.bukkit.inventory.InventoryHolder

class ContainerExplosionManager(private val plugin: Diawars) {

    fun explodeHolder(holder: InventoryHolder) {
        when (holder) {
            is BlockInventoryHolder -> explodeContainer(holder)
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

    fun explodeContainer(holder: BlockInventoryHolder) {
        if (holder.block.type in MaterialSets.SHULKER_BOXES) {
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

        inventory.forEach { item ->
            if (item?.type in MaterialSets.DIAMOND_ITEMS) {
                world.dropItemNaturally(loc, item ?: return@forEach)
                item.amount = 0
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

        holder.inventory.contents.forEach { item ->
            if (item?.type in MaterialSets.DIAMOND_ITEMS) {
                world.dropItemNaturally(loc, item ?: return@forEach)
                item.amount = 0
            }
        }

        plugin.server.scheduler.runTask(plugin, Runnable {
            world.createExplosion(loc, 4f)
        })
    }
}