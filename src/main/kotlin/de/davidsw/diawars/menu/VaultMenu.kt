package de.davidsw.diawars.menu

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.Team
import de.davidsw.diawars.stores.VaultClaim
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit.getOfflinePlayer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

class VaultMenu(private val plugin: Diawars) {
    companion object {
        private const val SLOT_VAULT_LIST   = 23
        private const val SLOT_CLAIM        = 38
        private const val SLOT_INVITE       = 39
        private const val SLOT_BAN          = 40
        private const val SLOT_STATUS       = 42

        private val ALL_SLOTS = listOf(SLOT_CLAIM, SLOT_INVITE, SLOT_BAN, SLOT_STATUS)
    }

    fun populateVaultMenu(inv: Inventory, player: Player) {
        ALL_SLOTS.forEach { inv.setItem(it, null) }

        val claims = plugin.store.vaultClaimStore
        val team = plugin.teamManager.getPlayerTeam(player.uniqueId)
        val ownClaim = claims.getClaimByOwner(player.uniqueId)
        val invitedClaims = claims.getClaimsInvitedTo(player.uniqueId)

        inv.setItem(SLOT_VAULT_LIST, item(
            material = Material.CHEST,
            name     = mm("<gold><bold>Vault-Liste</bold></gold>"),
            glow = false,
        ))

        populateActions(inv, team, ownClaim)
        populateStatus(inv, ownClaim, invitedClaims)
    }

    private fun populateActions(
        inv: Inventory,
        team: Team?,
        ownClaim: VaultClaim?,
    ) {
        if (ownClaim != null) {
            inv.setItem(SLOT_CLAIM, item(
                material = Material.BARRIER,
                name = mm("<red><bold>Vault freigeben</bold></red>"),
                lore = listOf(
                    mm("<gray>Gibt dein beanspruchtes Vault frei</gray>"),
                    mm(""),
                    mm("<yellow>Klicken zum Freigeben</yellow>"),
                ),
            ))
        } else {
            val canClaim = team != null
            inv.setItem(SLOT_CLAIM, actionItem(
                enabled = canClaim,
                material = Material.DIAMOND_BLOCK,
                name = "<green><bold>Vault beanspruchen</bold></green>",
                lore = if (canClaim) listOf(
                    mm("<gray>Beansprucht das Vault in deiner Nähe</gray>"),
                    mm(""),
                    mm("<yellow>Klicken zum Beanspruchen</yellow>"),
                ) else listOf(mm("<dark_gray>Du bist in keinem Team oder hast bereits ein Vault</dark_gray>")),
            ))
        }

        val canInvite = ownClaim != null
        inv.setItem(SLOT_INVITE, actionItem(
            enabled = canInvite,
            material = Material.PLAYER_HEAD,
            name = "<aqua><bold>Spieler einladen</bold></aqua>",
            lore = if (canInvite) listOf(
                mm("<gray>Lädt ein Teammitglied zu deinem Vault ein</gray>"),
                mm(""),
                mm("<yellow>Klicken um den Befehl vorzuschlagen</yellow>"),
            ) else listOf(mm("<dark_gray>Du hast kein Vault beansprucht</dark_gray>")),
        ))

        val canBan = ownClaim != null && ownClaim.invited.isNotEmpty()
        inv.setItem(SLOT_BAN, actionItem(
            enabled = canBan,
            material = Material.IRON_BARS,
            name = "<gold><bold>Spieler entfernen</bold></gold>",
            lore = if (canBan) listOf(
                mm("<gray>Entfernt einen eingeladenen Spieler</gray>"),
                mm(""),
                mm("<yellow>Klicken um den Befehl vorzuschlagen</yellow>"),
            ) else listOf(mm("<dark_gray>Niemand ist eingeladen</dark_gray>")),
        ))
    }

    private fun populateStatus(inv: Inventory, ownClaim: VaultClaim?, invitedClaims: List<VaultClaim>) {
        val diamonds = plugin.store.vaultDiamondStore
        val lore = mutableListOf<net.kyori.adventure.text.Component>()

        if (ownClaim != null) {
            val vaultName = plugin.vaultManager.getVaultById(ownClaim.vaultId)?.displayName ?: ownClaim.vaultId
            val vaultDiamonds = diamonds.getVaultCount(ownClaim.vaultId)
            val invitedNames = ownClaim.invited.map { getOfflinePlayer(it).name ?: "Unbekannt" }

            lore += mm("<gray>Dein Vault: </gray><gold>$vaultName</gold>")
            lore += mm("<gray>Diamanten: </gray><aqua>$vaultDiamonds</aqua>")
            lore += mm(
                if (invitedNames.isEmpty()) "<gray>Eingeladen: </gray><dark_gray>Niemand</dark_gray>"
                else "<gray>Eingeladen: </gray><white>${invitedNames.joinToString(", ")}</white>"
            )
        } else {
            lore += mm("<gray>Du hast kein Vault beansprucht</gray>")
        }

        if (invitedClaims.isNotEmpty()) {
            lore += mm("")
            lore += mm("<gray>Eingeladen zu:</gray>")
            invitedClaims.forEach { claim ->
                val vaultName = plugin.vaultManager.getVaultById(claim.vaultId)?.displayName ?: claim.vaultId
                val ownerName = getOfflinePlayer(claim.owner).name ?: "Unbekannt"
                lore += mm("<gray>- </gray><gold>$vaultName</gold> <gray>(von <white>$ownerName</white>)</gray>")
            }
        }

        inv.setItem(SLOT_STATUS, item(
            material = Material.NETHER_STAR,
            name = mm("<light_purple><bold>Dein Vault-Status</bold></light_purple>"),
            lore = lore,
        ))
    }

    fun handleVaultClick(player: Player, slot: Int, inv: Inventory) {
        when (slot) {
            SLOT_VAULT_LIST -> plugin.menuManager.openVaultListMenu(player)

            SLOT_CLAIM -> handleClaim(player, inv)

            SLOT_INVITE -> {
                val claims = plugin.store.vaultClaimStore
                if (claims.getClaimByOwner(player.uniqueId) == null) return
                player.closeInventory()
                player.sendMessage(
                    mm(
                        "<yellow>Bitte gib den Namen des Spielers ein:</yellow> " +
                                "<click:suggest_command:'/vault invite '><gold>[Spieler einladen]</gold></click>"
                    )
                )
            }

            SLOT_BAN -> {
                val claims = plugin.store.vaultClaimStore
                val claim = claims.getClaimByOwner(player.uniqueId) ?: return
                if (claim.invited.isEmpty()) return
                player.closeInventory()
                player.sendMessage(
                    mm(
                        "<yellow>Bitte gib den Namen des Spielers ein:</yellow> " +
                                "<click:suggest_command:'/vault ban '><gold>[Spieler entfernen]</gold></click>"
                    )
                )
            }
        }
    }

    private fun handleClaim(player: Player, inv: Inventory) {
        val claims = plugin.store.vaultClaimStore
        val team = plugin.teamManager.getPlayerTeam(player.uniqueId)
        if (team == null) {
            player.sendMessage(mm("<red>Du bist in keinem Team!</red>"))
            return
        }
        if (claims.hasClaimedAnyVault(player.uniqueId)) {
            val claim = claims.getClaimByOwner(player.uniqueId)
            if (claim == null) {
                player.sendMessage(mm("<red>Du hast kein Vault beansprucht!</red>"))
                return
            }
            val vaultName = plugin.vaultManager.getVaultById(claim.vaultId)?.displayName ?: claim.vaultId
            claims.unclaim(claim.vaultId)
            player.sendMessage(mm("<yellow>Du hast dein Vault <gold>$vaultName</gold> freigegeben.</yellow>"))
            populateVaultMenu(inv, player)
            return
        }

        val vault = plugin.vaultManager.findNearbyVault(player.location)
        if (vault == null) {
            player.sendMessage(mm("<red>Du musst in der Nähe eines Vaults stehen um es zu beanspruchen!</red>"))
            return
        }
        if (vault.team != team) {
            player.sendMessage(mm("<red>Dieses Vault gehört nicht deinem Team!</red>"))
            return
        }
        val existingClaim = claims.getClaim(vault.id)
        if (existingClaim != null) {
            val owner = getOfflinePlayer(existingClaim.owner).name ?: "Unbekannt"
            player.sendMessage(mm("<red>Dieses Vault wurde bereits von <gold>$owner</gold> beansprucht!</red>"))
            return
        }

        claims.claim(vault.id, player.uniqueId)
        player.sendMessage(mm("<green>✓ Du hast das Vault <gold>${vault.displayName}</gold> beansprucht!</green>"))
        populateVaultMenu(inv, player)
    }

    private fun actionItem(
        enabled: Boolean,
        material: Material,
        name: String,
        lore: List<net.kyori.adventure.text.Component>,
    ) = item(
        material = if (enabled) material else Material.GRAY_DYE,
        name = if (enabled) mm(name) else mm("<dark_gray><bold>Nicht verfügbar</bold></dark_gray>"),
        lore = lore,
        glow = false,
    )
}