package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

class InspectInventoryHolder : InventoryHolder {
    lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
}

class InventoryInspectManager(private val plugin: Diawars) {
    private data class Snapshot(
        val storage: List<ItemStack?>,
        val armor: List<ItemStack?>,
        val offHand: ItemStack?,
    )

    fun openMainInventory(admin: Player, target: OfflinePlayer) {
        val targetPlayer = target.player
        val inSpecialContext = targetPlayer != null && (
                plugin.eventManager.getSession(targetPlayer.uniqueId) != null ||
                        plugin.lobbyManager.isInLobby(targetPlayer.uniqueId)
                )

        val snapshot = if (targetPlayer != null && !inSpecialContext) {
            Snapshot(
                targetPlayer.inventory.storageContents.toList(),
                targetPlayer.inventory.armorContents.toList(),
                targetPlayer.inventory.itemInOffHand,
            )
        } else {
            val saved = plugin.store.playerStateStore.getState(target.uniqueId)
            if (saved == null) {
                admin.sendMessage(mm("<red>Für diesen Spieler liegen keine Normalwelt-Inventardaten vor!</red>"))
                return
            }
            Snapshot(saved.inventory, saved.armor, saved.offHand)
        }

        openSnapshot(admin, "<gold>Hauptinventar</gold> <gray>von</gray> <white>${target.name ?: "?"}</white>", snapshot)
    }

    fun openEventInventory(admin: Player, target: OfflinePlayer, eventId: String) {
        val event = plugin.store.eventStore.getEvent(eventId)
        if (event == null) {
            admin.sendMessage(mm("<red>Unbekanntes Event!</red>"))
            return
        }

        val targetPlayer = target.player
        val liveInThisEvent = targetPlayer != null &&
                plugin.eventManager.getSession(targetPlayer.uniqueId)?.eventId == eventId

        val snapshot = if (liveInThisEvent) {
            Snapshot(
                targetPlayer.inventory.storageContents.toList(),
                targetPlayer.inventory.armorContents.toList(),
                targetPlayer.inventory.itemInOffHand,
            )
        } else {
            val saved = plugin.store.eventInventoryStore.getInventory(eventId, target.uniqueId)
            if (saved == null) {
                admin.sendMessage(mm("<red>Für diesen Spieler liegen keine Inventardaten für dieses Event vor!</red>"))
                return
            }
            Snapshot(saved.inventory, saved.armor, saved.offHand)
        }

        openSnapshot(admin, "<gold>Event-Inventar</gold> <gray>(</gray><white>${event.name}</white><gray>)</gray>", snapshot)
    }

    fun openEnderChest(admin: Player, target: OfflinePlayer, eventId: String? = null) {
        if (eventId != null) {
            val event = plugin.store.eventStore.getEvent(eventId)
            if (event == null) {
                admin.sendMessage(mm("<red>Unbekanntes Event!</red>"))
                return
            }

            val targetPlayer = target.player
            val liveInThisEvent = targetPlayer != null &&
                    plugin.eventManager.getSession(targetPlayer.uniqueId)?.eventId == eventId

            val contents = if (liveInThisEvent) {
                targetPlayer.enderChest.contents.toList()
            } else {
                plugin.store.eventInventoryStore.getInventory(eventId, target.uniqueId)?.enderChest ?: run {
                    admin.sendMessage(mm("<red>Für diesen Spieler liegen keine Enderkisten-Daten für dieses Event vor!</red>"))
                    return
                }
            }

            openChestOnly(admin, "<light_purple>Enderkiste</light_purple> <gray>(</gray><white>${event.name}</white><gray>)</gray>", contents)
            return
        }

        val targetPlayer = target.player
        val inSpecialContext = targetPlayer != null && (
                plugin.eventManager.getSession(targetPlayer.uniqueId) != null ||
                        plugin.lobbyManager.isInLobby(targetPlayer.uniqueId)
                )

        val contents = if (targetPlayer != null && !inSpecialContext) {
            targetPlayer.enderChest.contents.toList()
        } else {
            plugin.store.playerStateStore.getState(target.uniqueId)?.enderChest ?: run {
                admin.sendMessage(mm("<red>Für diesen Spieler liegen keine Enderkisten-Daten vor!</red>"))
                return
            }
        }

        openChestOnly(admin, "<light_purple>Enderkiste</light_purple> <gray>von</gray> <white>${target.name ?: "?"}</white>", contents)
    }

    private fun openSnapshot(admin: Player, titleText: String, snapshot: Snapshot) {
        val holder = InspectInventoryHolder()
        val inv = Bukkit.createInventory(holder, 45, mm("<dark_gray>[Inspect]</dark_gray> $titleText"))
        holder.inv = inv

        snapshot.storage.take(36).forEachIndexed { index, stack -> inv.setItem(index, stack) }

        val pane = item(Material.GRAY_STAINED_GLASS_PANE)
        for (i in 36 until 45) inv.setItem(i, pane)

        val armorSlots = listOf(36, 37, 38, 39)
        snapshot.armor.take(4).forEachIndexed { index, stack -> if (stack != null) inv.setItem(armorSlots[index], stack) }
        if (snapshot.offHand != null && snapshot.offHand.type != Material.AIR) inv.setItem(40, snapshot.offHand)

        admin.openInventory(inv)
    }

    private fun openChestOnly(admin: Player, titleText: String, contents: List<ItemStack?>) {
        val holder = InspectInventoryHolder()
        val inv = Bukkit.createInventory(holder, 27, mm("<dark_gray>[Inspect]</dark_gray> $titleText"))
        holder.inv = inv
        contents.take(27).forEachIndexed { index, stack -> inv.setItem(index, stack) }
        admin.openInventory(inv)
    }
}