package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.DiamondCounter
import de.tr7zw.nbtapi.NBT
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

class DiamondLimitManager(private val plugin: Diawars) {
    val trackedDiamonds = mutableMapOf<UUID, DiamondData>()

    data class DiamondData(
        val item: Item,
        val textDisplay: TextDisplay,
    )

    fun countDiamonds(player: Player): Int = DiamondCounter.countForPlayer(player)

    fun enforceLimit(player: Player, limit: Int) {
        val total = countDiamonds(player)
        if (total <= limit) return
        dropDiamondsFromInventory(player, excess = total - limit, keepAmount = limit)
    }

    fun dropAll(player: Player) {
        val total = countDiamonds(player)
        if (total == 0) return
        dropDiamondsFromInventory(player, excess = total, keepAmount = 0)
    }

    private fun dropDiamondsFromInventory(player: Player, excess: Int, keepAmount: Int) {
        var remaining = excess
        var left = keepAmount
        val inv = player.inventory
        for (i in inv.contents.indices) {
            if (remaining <= 0) break
            val item = inv.getItem(i) ?: continue

            when (item.type) {
                Material.DIAMOND -> {
                    val keep = minOf(item.amount, left)
                    val drop = item.amount - keep
                    inv.setItem(i, if (keep == 0) ItemStack(Material.AIR) else ItemStack(Material.DIAMOND, keep))
                    if (drop > 0) {
                        player.location.world?.dropItemNaturally(player.location, ItemStack(Material.DIAMOND, drop))
                            ?.also { it.pickupDelay = 40 }
                    }
                    remaining -= drop
                    left -= keep
                }
                Material.DIAMOND_BLOCK -> {
                    val keep = minOf(item.amount, left / 9)
                    val drop = item.amount - keep
                    inv.setItem(i, if (keep == 0) ItemStack(Material.AIR) else ItemStack(Material.DIAMOND_BLOCK, keep))
                    if (drop > 0) {
                        player.location.world?.dropItemNaturally(player.location, ItemStack(Material.DIAMOND_BLOCK, drop))
                            ?.also { it.pickupDelay = 40 }
                    }
                    remaining -= drop * 9
                    left -= keep * 9
                }
                else -> continue
            }
        }
    }

    fun trackDiamond(item: Item) {
        if (!item.isValid || item.isDead) return
        if (item.uniqueId in trackedDiamonds) return

        item.isGlowing = true

        val textDisplay = item.location.world.spawn(item.location.clone().add(0.0, 0.6, 0.0), TextDisplay::class.java) { display ->
            display.text(formatTimer(getTicksLived(item)))
            display.isShadowed = true
            display.isVisibleByDefault = true
            display.billboard = display.billboard
            display.isPersistent = false
            display.backgroundColor = Color.fromARGB(160, 0, 0, 0)
            display.textOpacity = 230.toByte()
        }

        textDisplay.billboard = Display.Billboard.CENTER
        trackedDiamonds[item.uniqueId] = DiamondData(item, textDisplay)
    }

    private fun formatTimer(age: Int): Component {
        val ticks = 6000 - age
        val secondsLeft = (ticks / 20).coerceAtLeast(0)
        val minutes = secondsLeft / 60
        val seconds = secondsLeft % 60
        val color = when {
            ticks > plugin.config.getInt("dia-timer.yellow", 3000) -> NamedTextColor.GREEN // green
            ticks > plugin.config.getInt("dia-timer.red", 1200) -> NamedTextColor.YELLOW // yellow
            else -> NamedTextColor.RED // red
        }
        return Component.text("⏱ ${"%d:%02d".format(minutes, seconds)}", color)
    }

    fun startTrackingTask() {
        object: BukkitRunnable() {
            override fun run() {
                val toRemove = mutableListOf<UUID>()

                trackedDiamonds.forEach { (uuid, data) ->
                    val item = data.item

                    if (!item.isValid || item.isDead) {
                        data.textDisplay.remove()
                        toRemove.add(uuid)
                        return@forEach
                    }

                    val ticksLived = getTicksLived(item)
                    val ticksLeft = plugin.config.getInt("dia-timer.despawn-time", 6000) - ticksLived

                    if (ticksLeft <= 0) {
                        data.textDisplay.remove()
                        toRemove.add(uuid)
                        return@forEach
                    }

                    val itemLoc = item.location.clone().add(0.0, 0.6, 0.0)
                    data.textDisplay.teleport(itemLoc)
                    data.textDisplay.text(formatTimer(ticksLived))
                }
                toRemove.forEach { trackedDiamonds.remove(it) }
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }

    fun removeTracking(item: Item) {
        val data  = trackedDiamonds.remove(item.uniqueId) ?: return
        data.textDisplay.remove()
    }

    fun reTrackDiamond(item: Item) {
        removeTracking(item)
        resetTicksLived(item)
        trackDiamond(item)
    }

    fun resetTicksLived(item: Item) {
        try {
            NBT.modify(item) { nbt ->
                nbt.setInteger("Age", 6000 - plugin.config.getInt("dia-timer.despawn-time", 6000))
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to reset item ticks: ${e.message}")
        }
    }

    fun getTicksLived(item: Item): Int {
        var age = 0
        NBT.get(item) { nbt ->
            age = nbt.getInteger("Age")
        }
        return age
    }

    fun trackExistingDiamonds() {
        plugin.server.worlds.forEach { world ->
            world.entities
                .filterIsInstance<Item>()
                .filter { it.itemStack.type == Material.DIAMOND }
                .forEach { trackDiamond(it) }
        }
    }

    fun convertDiamondBlocks(item: Item) {
        val stack = item.itemStack
        val diamondCount = stack.amount * 9
        if (diamondCount <= 64) {
            val newStack = ItemStack(Material.DIAMOND, diamondCount)
            item.itemStack = newStack
        } else {
            val stacks = diamondCount / 64
            val left = diamondCount % 64
            repeat(stacks) {
                val drop = item.location.world.dropItem(item.location, ItemStack(Material.DIAMOND, 64))
                drop.velocity = item.velocity
                drop.pickupDelay = item.pickupDelay
                drop.thrower = item.thrower
            }
            if (left > 0) {
                item.itemStack = ItemStack(Material.DIAMOND, left)
            } else {
                item.itemStack = ItemStack(Material.AIR)
            }
        }
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            resetTicksLived(item)
            trackDiamond(item)
        }, 2L)
    }
}