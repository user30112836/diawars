package de.davidsw.diawars.menu

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.EventManager
import de.davidsw.diawars.managers.TextInputManager
import de.davidsw.diawars.util.BedrockUtil
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

class MainMenu(private val plugin: Diawars) {
    companion object {
        private const val SLOT_IMAGE      = 20
        private const val SLOT_EVENTS     = 22
        private const val SLOT_LOBBY      = 24
        private const val SLOT_SETTINGS   = 29
        private const val SLOT_VAULT      = 31
        private const val SLOT_TOGGLES    = 33
        private const val SLOT_REPORT     = 38
        private const val SLOT_BUG_REPORT = 40
    }

    fun populateMainMenu(inv: Inventory, player: Player) {
        // Images (disabled on Bedrock: no client mod for uploads; disabled
        // while banned: uploads and the menu are locked)
        if (BedrockUtil.isBedrock(player)) {
            inv.setItem(SLOT_IMAGE, item(
                material = Material.BARRIER,
                name = mm("<gray><bold>Bilder</bold></gray>"),
                lore = listOf(
                    mm("<gray>Auf Bedrock nicht verfügbar</gray>"),
                ),
                glow = false,
            ))
        } else if (plugin.imageManager.isBanned(player.uniqueId)) {
            inv.setItem(SLOT_IMAGE, item(
                material = Material.BARRIER,
                name = mm("<red><bold>Bilder</bold></red>"),
                lore = listOf(
                    mm("<red>Du bist für die Bilder-Funktion gesperrt!</red>"),
                    mm("<gray>Wende dich an einen Admin.</gray>"),
                ),
                glow = false,
            ))
        } else {
            val imageCount = plugin.imageManager.getImageCount(player.uniqueId)
            val imageLimit = plugin.store.imageLimitStore.getLimit(player.uniqueId)
            inv.setItem(SLOT_IMAGE, item(
                material = Material.FILLED_MAP,
                name = mm("<white><bold>Bilder</bold></white>"),
                lore = listOf(
                    mm("<gray>Deine hochgeladenen Bilder: </gray><white>$imageCount/$imageLimit</white>"),
                    mm("<gray>Karte per Klick erhalten</gray>"),
                    mm("<gray>Upload über die Client-Mod</gray>"),
                    mm(""),
                    mm("<yellow>Klicken zum Öffnen</yellow>"),
                ),
                glow = false,
            ))
        }

        // Events
        val eventSession = plugin.eventManager.getSession(player.uniqueId)
        val sessionText = when (eventSession?.mode) {
            EventManager.SessionMode.BUILD -> "<yellow>Baue an einem Event</yellow>"
            EventManager.SessionMode.REVIEW -> "<light_purple>Prüfst ein Event</light_purple>"
            EventManager.SessionMode.PLAY -> "<green>Nimmst an einem Event teil</green>"
            null -> "<gray>Kein aktives Event</gray>"
        }
        inv.setItem(SLOT_EVENTS, item(
            material = Material.BEACON,
            name = mm("<gold><bold>Events</bold></gold>"),
            lore = listOf(
                mm("<gray>Status: </gray>$sessionText"),
                mm(""),
                mm("<yellow>Klicken zum Öffnen</yellow>"),
            ),
            glow = false,
        ))

        // Lobby toggle
        val inEventSession = plugin.eventManager.getSession(player.uniqueId) != null
        val inLobby = plugin.lobbyManager.isInLobby(player.uniqueId)
        inv.setItem(SLOT_LOBBY, item(
            material = when {
                inEventSession -> Material.BARRIER
                inLobby -> Material.RED_BED
                else -> Material.LIME_BED
            },
            name = mm(when {
                inEventSession -> "<gray><bold>Lobby</bold></gray>"
                inLobby -> "<red><bold>Lobby verlassen</bold></red>"
                else -> "<green><bold>Lobby betreten</bold></green>"
            }),
            lore = if (inEventSession) listOf(
                mm("<gray>Verlasse zuerst dein Event</gray>"),
                mm("<gray>um die Lobby zu betreten</gray>"),
            ) else listOf(
                mm("<gray>Status: </gray>${if (inLobby) "<green>In der Lobby</green>" else "<red>Nicht in der Lobby</red>"}"),
                mm(""),
                mm("<yellow>Klicken zum ${if (inLobby) "Verlassen" else "Betreten"}</yellow>"),
            ),
            glow = false,
        ))

        // Settings
        inv.setItem(SLOT_SETTINGS, item(
            material = Material.COMPARATOR,
            name = mm("<green><bold>Einstellungen</bold></green>"),
            lore = listOf(
                mm("<gray>Border und Sidebar anpassen</gray>"),
                mm(""),
                mm("<yellow>Klicken zum Öffnen</yellow>"),
            ),
            glow = false,
        ))

        // Vault management
        val ownClaim = plugin.store.vaultClaimStore.getClaimByOwner(player.uniqueId)
        val vaultLore = if (ownClaim != null) {
            val vaultName = plugin.vaultManager.getVaultById(ownClaim.vaultId)?.displayName ?: ownClaim.vaultId
            val vaultDiamonds = plugin.store.vaultDiamondStore.getVaultCount(ownClaim.vaultId)
            listOf(
                mm("<gray>Dein Vault: </gray><gold>$vaultName</gold>"),
                mm("<gray>Diamanten: </gray><aqua>$vaultDiamonds</aqua>"),
                mm(""),
                mm("<yellow>Klicken zum Öffnen</yellow>"),
            )
        } else {
            listOf(
                mm("<gray>Du hast noch kein Vault beansprucht</gray>"),
                mm(""),
                mm("<yellow>Klicken zum Öffnen</yellow>"),
            )
        }
        inv.setItem(SLOT_VAULT, item(
            material = Material.VAULT,
            name = mm("<gold><bold>Vault-Verwaltung</bold></gold>"),
            lore = vaultLore,
            glow = false,
        ))

        // Toggles
        inv.setItem(SLOT_TOGGLES, item(
            material = Material.LEVER,
            name = mm("<yellow><bold>Trigger/Toggles</bold></yellow>"),
            lore = listOf(
                mm("<gray>PvP, Self-kill, Nachtsicht</gray>"),
                mm("<gray>und Inventar</gray>"),
                mm(""),
                mm("<yellow>Klicken zum Öffnen</yellow>"),
            ),
            glow = false,
        ))

        // Report
        inv.setItem(SLOT_REPORT, item(
            material = Material.COMPASS,
            name = mm("<gold><bold>Melden</bold></gold>"),
            lore = listOf(
                mm("<gray>Melde Bild-Karten oder Spieler</gray>"),
                mm("<gray>mit Grund und Screenshot</gray>"),
                mm(""),
                mm("<yellow>Klicken öffnet den Report-Dialog</yellow>"),
            ),
            glow = false,
        ))

        // Bug report
        inv.setItem(SLOT_BUG_REPORT, item(
            material = Material.WRITABLE_BOOK,
            name = mm("<yellow><bold>Bug melden</bold></yellow>"),
            lore = listOf(
                mm("<gray>Melde einen Fehler im Plugin</gray>"),
                mm(""),
                mm("<yellow>Klicken zur Eingabe</yellow>"),
            ),
            glow = false,
        ))
    }

    fun handleMainClick(player: Player, slot: Int) {
        when (slot) {
            SLOT_IMAGE -> {
                // Disabled button (Bedrock or banned): the lore explains why,
                // just close instead of opening the image menu.
                if (BedrockUtil.isBedrock(player) || plugin.imageManager.isBanned(player.uniqueId)) {
                    player.closeInventory()
                } else {
                    plugin.menuManager.openImageMenu(player)
                }
            }

            SLOT_EVENTS -> plugin.menuManager.openEventMenu(player)

            SLOT_LOBBY -> {
                if (plugin.eventManager.getSession(player.uniqueId) != null) return

                if (plugin.lobbyManager.isInLobby(player.uniqueId)) {
                    if (plugin.lobbyManager.locked && !player.hasPermission("diawars.admin")) {
                        player.sendMessage(mm("<red>Die Lobby ist derzeit gesperrt! Du kannst sie nicht verlassen.</red>"))
                    } else if (plugin.lobbyManager.leaveLobby(player)) {
                        player.closeInventory()
                        player.sendMessage(mm("<green>Du hast die Lobby verlassen.</green>"))
                    }
                } else {
                    if (plugin.pvpManager.isInFight(player.uniqueId)) {
                        val remaining = plugin.pvpManager.fightTimeRemainingText(player.uniqueId)
                        player.sendMessage(mm("""
                            <red>Du kannst die Lobby nicht während eines Kampfes betreten!</red>

                            <gray>Verbleibende Zeit:</gray><yellow>$remaining</yellow>
                        """.trimIndent()))
                    } else if (plugin.lobbyManager.sendToLobby(player)) {
                        player.closeInventory()
                        player.sendMessage(mm("<green>Willkommen in der Lobby!</green>"))
                    } else {
                        player.sendMessage(mm("<red>Die Lobby konnte nicht geladen werden!</red>"))
                    }
                }
            }

            SLOT_SETTINGS -> plugin.menuManager.openSettingsMenu(player)

            SLOT_VAULT -> plugin.menuManager.openVaultMenu(player)

            SLOT_TOGGLES -> plugin.menuManager.openTogglesMenu(player)

            SLOT_REPORT -> plugin.reportManager.openDialog(player)

            SLOT_BUG_REPORT -> plugin.textInputManager.openPrompt(player, TextInputManager.Prompt.BUG_REPORT)
        }
    }
}
