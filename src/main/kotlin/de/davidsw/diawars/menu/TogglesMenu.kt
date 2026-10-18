package de.davidsw.diawars.menu

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.PvPManager
import de.davidsw.diawars.util.MaterialSets
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MiniMessageHelper.mm
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

class TogglesMenu(private val plugin: Diawars) {
    companion object {
        private const val SLOT_PVP_TOGGLE   = 19
        private const val SLOT_SELF_KILL    = 21
        private const val SLOT_NIGHT_VISION = 23
        private const val SLOT_INV          = 25
    }

    fun populateTogglesMenu(inv: Inventory, player: Player) {
        val pvpEnabled = plugin.store.pvpStatusStore.isPvPEnabled(player.uniqueId)
        val hasPending = plugin.store.pvpStatusStore.hasPendingToggle(player.uniqueId)

        // PvP toggle
        if (hasPending) {
            // hasPending implies a cached destination; default to true so a raced
            // cache miss can never abort the whole menu render mid-way (?: return).
            val destination = plugin.store.pvpStatusStore.getToggleDestination(player.uniqueId) ?: true
            inv.setItem(SLOT_PVP_TOGGLE, item(
                material = Material.BARRIER,
                name = mm(if (destination) "<yellow><bold>PvP Aktivierung abbrechen</bold></yellow>" else "<yellow><bold>PvP Aktivierung</bold></yellow>"),
                lore = formatPvPToggleCancelLore(player),
                glow = false,
            ))
        } else {
            if (!plugin.pvpManager.isInFight(player.uniqueId)) {
                val delayMinutes = plugin.config.getInt("pvp-toggle.delay-seconds", 300) / 60
                inv.setItem(SLOT_PVP_TOGGLE, item(
                    material = if (pvpEnabled) Material.RED_WOOL else Material.GREEN_WOOL,
                    name = mm(if (pvpEnabled) "<red><bold>PvP deaktivieren</bold></red>" else "<green><bold>PvP aktivieren</bold></green>"),
                    lore = listOf(
                        mm("<gray>Status: </gray>${if (pvpEnabled) "<green>Aktiviert</green>" else "<red>Deaktiviert</red>"}"),
                        mm("<gray>Klicken zum Umschalten</gray>"),
                        mm("<gray>($delayMinutes Min. Verzögerung)</gray>"),
                    ),
                    glow = false,
                ))
            } else {
                // Render a disabled slot while in combat instead of leaving the
                // previous (now misleading) toggle item in place.
                inv.setItem(SLOT_PVP_TOGGLE, item(
                    material = Material.BARRIER,
                    name = mm("<gray><bold>PvP</bold></gray>"),
                    lore = listOf(
                        mm("<gray>Im Kampf nicht verfügbar</gray>"),
                    ),
                    glow = false,
                ))
            }
        }

        // Self-kill
        val inFight = plugin.pvpManager.isInFight(player.uniqueId)
        if (inFight) {
            inv.setItem(SLOT_SELF_KILL, item(
                material = Material.BARRIER,
                name = mm("<gray><bold>Self-kill</bold></gray>"),
                lore = listOf(
                    mm("<gray>Diese Funktion ist im Kampf deaktiviert</gray>"),
                ),
                glow = false,
            ))
        } else {
            inv.setItem(SLOT_SELF_KILL, item(
                material = Material.BONE,
                name = mm("<dark_red><bold>Self-kill</bold></dark_red>"),
                lore = listOf(
                    mm("<gray>Hier kannst du dich selbst killen wenn du feststeckst</gray>"),
                ),
                glow = false,
            ))
        }

        // Night vision toggle
        val nvAvailable = plugin.nightVisionManager.isAvailable()
        val nvEnabled = plugin.nightVisionManager.hasEnabled(player.uniqueId)
        inv.setItem(SLOT_NIGHT_VISION, item(
            material = Material.ENDER_EYE,
            name = mm("<yellow><bold>Nachtsicht</bold></yellow>"),
            lore = when {
                !nvAvailable -> listOf(
                    mm("<gray>Derzeit deaktiviert</gray>"),
                )
                nvEnabled -> listOf(
                    mm("<gray>Status: </gray><green>Aktiviert</green>"),
                    mm(""),
                    mm("<yellow>Klicken zum Deaktivieren</yellow>"),
                )
                else -> listOf(
                    mm("<gray>Status: </gray><red>Deaktiviert</red>"),
                    mm(""),
                    mm("<yellow>Klicken zum Aktivieren</yellow>"),
                )
            },
            glow = nvAvailable && nvEnabled,
        ))

        // Inventory access (held shulker box / ender chest)
        val heldItem = player.inventory.itemInMainHand
        val canOpenInv = MaterialSets.isShulkerBox(heldItem.type) || heldItem.type == Material.ENDER_CHEST
        inv.setItem(SLOT_INV, item(
            material = if (canOpenInv) heldItem.type else Material.CHEST,
            name = mm(if (canOpenInv) "<yellow><bold>Inventar öffnen</bold></yellow>" else "<gray><bold>Inventar öffnen</bold></gray>"),
            lore = if (canOpenInv) listOf(
                mm("<gray>Öffnet die gehaltene Shulker-Box</gray>"),
                mm("<gray>oder Enderkiste</gray>"),
                mm(""),
                mm("<yellow>Klicken zum Öffnen</yellow>"),
            ) else listOf(
                mm("<gray>Halte eine Shulker-Box oder</gray>"),
                mm("<gray>eine Enderkiste in der Hand</gray>"),
            ),
            glow = false,
        ))
    }

    fun handleTogglesClick(player: Player, slot: Int, inv: Inventory) {
        when (slot) {
            SLOT_PVP_TOGGLE -> {
                val result = plugin.pvpManager.togglePvP(player.uniqueId)
                when (result) {
                    PvPManager.ToggleResult.ALREADY_PENDING -> plugin.pvpManager.cancelToggle(player.uniqueId)
                    PvPManager.ToggleResult.IN_FIGHT -> {}
                    else -> populateTogglesMenu(inv, player)
                }
            }

            SLOT_SELF_KILL -> if (!plugin.pvpManager.isInFight(player.uniqueId)) player.health = 0.0

            SLOT_NIGHT_VISION -> {
                val next = plugin.nightVisionManager.toggle(player)
                if (next == null) {
                    player.sendMessage(mm("<red>Nachtsicht ist derzeit deaktiviert!</red>"))
                } else {
                    player.sendMessage(
                        mm(if (next) "<green>✓ Nachtsicht aktiviert!</green>" else "<gray>Nachtsicht deaktiviert.</gray>"),
                    )
                }
                populateTogglesMenu(inv, player)
            }

            SLOT_INV -> {
                val item = player.inventory.itemInMainHand
                when (item.type) {
                    in MaterialSets.SHULKER_BOXES -> {
                        player.closeInventory()
                        if (!plugin.shulkerAccessManager.openHeldShulker(player)) {
                            player.sendMessage(mm("<red>Diese Shulker-Box konnte nicht geöffnet werden!</red>"))
                        }
                    }
                    Material.ENDER_CHEST -> {
                        player.closeInventory()
                        plugin.shulkerAccessManager.openHeldEnderChest(player)
                    }
                    else -> {
                        player.sendMessage(mm("<red>Du musst eine Shulker-Box oder eine Enderkiste in der Hand haben!</red>"))
                    }
                }
            }
        }
    }

    private fun formatPvPToggleCancelLore(player: Player): List<Component> {
        val remaining = plugin.store.pvpStatusStore.getRemainingTime(player.uniqueId)
        val minutes = remaining / 60
        val seconds = remaining % 60
        val duration = buildString {
            if (minutes.toInt() != 0) append(" ${minutes}m")
            if (seconds.toInt() != 0) append(" ${seconds}s")
        }
        return listOf(mm("<gray>Verbleibend: </gray><yellow>${duration}</yellow>"))
    }
}
