package de.davidsw.diawars.menu

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.EventManager
import de.davidsw.diawars.stores.EventGameRules
import de.davidsw.diawars.stores.EventPotionEffect
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

class EventConfigMenu(private val plugin: Diawars) {
    companion object {
        private const val SLOT_GAMEMODE  = 10
        private const val SLOT_TIME_MODE = 11
        private const val SLOT_TIME_DEC  = 12
        private const val SLOT_TIME_INC  = 13
        private const val SLOT_INV_SET   = 15
        private const val SLOT_INV_CLEAR = 16

        private val GAMERULE_SLOTS = listOf(19, 20, 21, 22, 23, 24)
        private val EFFECT_SLOTS   = listOf(28, 29, 30, 31, 32, 33, 34)
        private const val SLOT_STATUS = 40

        private val GAME_MODES = listOf(GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.ADVENTURE, GameMode.SPECTATOR)

        private val GAMERULE_MATERIAL = mapOf(
            "keep-inventory"  to Material.TOTEM_OF_UNDYING,
            "mob-griefing"    to Material.CREEPER_HEAD,
            "mob-drops"       to Material.ROTTEN_FLESH,
            "advance-weather" to Material.WATER_BUCKET,
            "block-drops"      to Material.COBBLESTONE,
        )

        private val EFFECT_ITEMS = listOf(
            "SPEED"           to Material.SUGAR,
            "JUMP_BOOST"      to Material.RABBIT_FOOT,
            "NIGHT_VISION"    to Material.GOLDEN_CARROT,
            "FIRE_RESISTANCE" to Material.MAGMA_CREAM,
            "RESISTANCE"      to Material.SHIELD,
            "STRENGTH"        to Material.BLAZE_POWDER,
            "SATURATION"      to Material.COOKED_BEEF,
        )

        private val ALL_SLOTS = listOf(SLOT_GAMEMODE, SLOT_TIME_MODE, SLOT_TIME_DEC, SLOT_TIME_INC,
            SLOT_INV_SET, SLOT_INV_CLEAR, SLOT_STATUS) + GAMERULE_SLOTS + EFFECT_SLOTS
    }

    fun populateEventConfigMenu(inv: Inventory, player: Player) {
        ALL_SLOTS.forEach { inv.setItem(it, null) }

        val session = plugin.eventManager.getSession(player.uniqueId)
        if (session == null || session.mode != EventManager.SessionMode.BUILD) {
            inv.setItem(SLOT_STATUS, item(
                material = Material.BARRIER,
                name = mm("<red><bold>Nicht verfügbar</bold></red>"),
                lore = listOf(mm("<gray>Du baust gerade an keinem Event</gray>")),
            ))
            return
        }

        val config = plugin.store.eventConfigStore.getConfig(session.eventId)

        // Gamemode
        inv.setItem(SLOT_GAMEMODE, item(
            material = Material.IRON_SWORD,
            name = mm("<aqua><bold>Spielmodus (Beitreten)</bold></aqua>"),
            lore = listOf(
                mm("<gray>Aktuell: </gray><white>${config.gameMode.name}</white>"),
                mm(""),
                mm("<yellow>Klicken zum Wechseln</yellow>"),
            ),
        ))

        // Time mode
        inv.setItem(SLOT_TIME_MODE, item(
            material = if (config.advanceTime) Material.CLOCK else Material.SUNFLOWER,
            name = mm("<gold><bold>Tageszeit</bold></gold>"),
            lore = listOf(
                mm("<gray>Modus: </gray><white>${if (config.advanceTime) "Läuft normal" else "Fixiert"}</white>"),
                mm(""),
                mm("<yellow>Klicken zum Umschalten</yellow>"),
            ),
        ))

        val timeDisabled = config.advanceTime
        inv.setItem(SLOT_TIME_DEC, item(
            material = if (timeDisabled) Material.GRAY_STAINED_GLASS_PANE else Material.RED_STAINED_GLASS_PANE,
            name = if (timeDisabled) mm("<dark_gray>Nur bei fixierter Zeit</dark_gray>") else mm("<red><bold>◄ -1000 Ticks</bold></red>"),
            lore = if (timeDisabled) emptyList() else listOf(mm("<gray>Aktuell: <white>${config.fixedTime}</white> Ticks</gray>")),
        ))
        inv.setItem(SLOT_TIME_INC, item(
            material = if (timeDisabled) Material.GRAY_STAINED_GLASS_PANE else Material.GREEN_STAINED_GLASS_PANE,
            name = if (timeDisabled) mm("<dark_gray>Nur bei fixierter Zeit</dark_gray>") else mm("<green><bold>+1000 Ticks ►</bold></green>"),
            lore = if (timeDisabled) emptyList() else listOf(mm("<gray>Aktuell: <white>${config.fixedTime}</white> Ticks</gray>")),
        ))

        // Starting inventory
        val hasCustomInv = config.startingInventory.any { it != null }
        inv.setItem(SLOT_INV_SET, item(
            material = Material.CHEST,
            name = mm("<yellow><bold>Aktuelles Inventar speichern</bold></yellow>"),
            lore = listOf(
                mm("<gray>Speichert dein jetziges Inventar als</gray>"),
                mm("<gray>Start-Inventar für Beitretende</gray>"),
                mm(""),
                mm("<gray>Status: </gray>${if (hasCustomInv) "<green>Konfiguriert</green>" else "<dark_gray>Standard (leer)</dark_gray>"}"),
                mm(""),
                mm("<yellow>Klicken zum Speichern</yellow>"),
            ),
        ))
        inv.setItem(SLOT_INV_CLEAR, item(
            material = Material.BARRIER,
            name = mm("<red><bold>Start-Inventar leeren</bold></red>"),
            lore = listOf(mm("<gray>Setzt das Start-Inventar zurück</gray>")),
        ))

        // Gamerules
        EventGameRules.CONFIGURABLE.keys.forEachIndexed { index, key ->
            val enabled = config.gameRules[key] ?: EventGameRules.DEFAULTS[key] ?: true
            inv.setItem(GAMERULE_SLOTS[index], item(
                material = GAMERULE_MATERIAL[key] ?: Material.PAPER,
                name = mm(if (enabled) "<green><bold>$key</bold></green>" else "<gray><bold>$key</bold></gray>"),
                lore = listOf(
                    mm("<gray>Status: </gray>${if (enabled) "<green>An</green>" else "<red>Aus</red>"}"),
                    mm(""),
                    mm("<yellow>Klicken zum Umschalten</yellow>"),
                ),
                glow = enabled,
            ))
        }

        // Effects
        EFFECT_ITEMS.forEachIndexed { index, (name, material) ->
            val level = config.effects.firstOrNull { it.type == name }?.let { it.amplifier + 1 } ?: 0
            inv.setItem(EFFECT_SLOTS[index], item(
                material = material,
                name = mm(if (level > 0) "<light_purple><bold>$name</bold></light_purple>" else "<gray><bold>$name</bold></gray>"),
                lore = listOf(
                    mm("<gray>Level: </gray>${if (level > 0) "<white>$level</white>" else "<dark_gray>Aus</dark_gray>"}"),
                    mm(""),
                    mm("<yellow>Links-Klick: </yellow><gray>Level erhöhen (max 5)</gray>"),
                    mm("<yellow>Rechts-Klick: </yellow><gray>Level verringern</gray>"),
                ),
                glow = level > 0,
            ))
        }

        // Status summary
        inv.setItem(SLOT_STATUS, item(
            material = Material.NETHER_STAR,
            name = mm("<light_purple><bold>Konfigurationsübersicht</bold></light_purple>"),
            lore = listOf(
                mm("<gray>Spielmodus: </gray><white>${config.gameMode.name}</white>"),
                mm("<gray>Tageszeit: </gray><white>${if (config.advanceTime) "Normal" else "${config.fixedTime} Ticks"}</white>"),
                mm("<gray>Effekte: </gray><white>${config.effects.size}</white>"),
                mm("<gray>Start-Inventar: </gray>${if (hasCustomInv) "<green>Konfiguriert</green>" else "<dark_gray>Standard</dark_gray>"}"),
            ),
        ))
    }

    fun handleEventConfigClick(player: Player, slot: Int, inv: Inventory, rightClick: Boolean) {
        val session = plugin.eventManager.getSession(player.uniqueId)
        if (session == null || session.mode != EventManager.SessionMode.BUILD) return

        val eventId = session.eventId
        val configStore = plugin.store.eventConfigStore

        when (slot) {
            SLOT_GAMEMODE -> {
                configStore.update(eventId) { cfg ->
                    val idx = GAME_MODES.indexOf(cfg.gameMode).takeIf { it >= 0 } ?: 0
                    cfg.copy(gameMode = GAME_MODES[(idx + 1) % GAME_MODES.size])
                }
                populateEventConfigMenu(inv, player)
            }

            SLOT_TIME_MODE -> {
                configStore.update(eventId) { it.copy(advanceTime = !it.advanceTime) }
                plugin.eventManager.applyTimeConfig(eventId)
                populateEventConfigMenu(inv, player)
            }

            SLOT_TIME_DEC -> {
                if (!configStore.getConfig(eventId).advanceTime) {
                    configStore.update(eventId) { it.copy(fixedTime = ((it.fixedTime - 1000 + 24000) % 24000)) }
                    plugin.eventManager.applyTimeConfig(eventId)
                    populateEventConfigMenu(inv, player)
                }
            }

            SLOT_TIME_INC -> {
                if (!configStore.getConfig(eventId).advanceTime) {
                    configStore.update(eventId) { it.copy(fixedTime = (it.fixedTime + 1000) % 24000) }
                    plugin.eventManager.applyTimeConfig(eventId)
                    populateEventConfigMenu(inv, player)
                }
            }

            SLOT_INV_SET -> {
                configStore.update(eventId) {
                    it.copy(
                        startingInventory = player.inventory.storageContents.toList(),
                        startingArmor = player.inventory.armorContents.toList(),
                        startingOffHand = player.inventory.itemInOffHand.clone(),
                    )
                }
                player.sendMessage(mm("<green>✓ Aktuelles Inventar als Start-Inventar gespeichert!</green>"))
                populateEventConfigMenu(inv, player)
            }

            SLOT_INV_CLEAR -> {
                configStore.update(eventId) { it.copy(startingInventory = emptyList(), startingArmor = emptyList(), startingOffHand = null) }
                player.sendMessage(mm("<yellow>Start-Inventar wurde geleert.</yellow>"))
                populateEventConfigMenu(inv, player)
            }

            in GAMERULE_SLOTS -> {
                val key = EventGameRules.CONFIGURABLE.keys.toList()[GAMERULE_SLOTS.indexOf(slot)]
                configStore.update(eventId) { cfg ->
                    val current = cfg.gameRules[key] ?: EventGameRules.DEFAULTS[key] ?: true
                    cfg.copy(gameRules = cfg.gameRules + (key to !current))
                }
                populateEventConfigMenu(inv, player)
            }

            in EFFECT_SLOTS -> {
                val effectName = EFFECT_ITEMS[EFFECT_SLOTS.indexOf(slot)].first
                configStore.update(eventId) { cfg ->
                    val currentLevel = (cfg.effects.firstOrNull { it.type == effectName }?.amplifier ?: -1) + 1
                    val newLevel = if (rightClick) (currentLevel - 1).coerceAtLeast(0)
                    else if (currentLevel >= 5) 0 else currentLevel + 1
                    val filtered = cfg.effects.filterNot { it.type == effectName }
                    cfg.copy(effects = if (newLevel == 0) filtered else filtered + EventPotionEffect(effectName, newLevel - 1))
                }
                populateEventConfigMenu(inv, player)
            }
        }
    }
}