package de.davidsw.diawars.menu

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.VaultRegion
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit.getOfflinePlayer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import java.util.UUID

class VaultListMenu(private val plugin: Diawars) {
    companion object {
        private val LIST_SLOTS = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
        )
        private const val SLOT_PREV_PAGE = 37
        private const val SLOT_PAGE_INFO = 40
        private const val SLOT_NEXT_PAGE = 43

        private val ALL_SLOTS = LIST_SLOTS + listOf(SLOT_PREV_PAGE, SLOT_PAGE_INFO, SLOT_NEXT_PAGE)
        private val PAGE_SIZE = LIST_SLOTS.size
    }

    private val currentPage = mutableMapOf<UUID, Int>()

    private val pageCache = mutableMapOf<UUID, List<VaultRegion>>()

    fun resetPage(player: Player) {
        currentPage[player.uniqueId] = 0
    }

    fun populateVaultListMenu(inv: Inventory, player: Player) {
        ALL_SLOTS.forEach { inv.setItem(it, null) }

        val vaults = plugin.vaultManager.getAllVaults()
            .sortedWith(compareBy({ it.team.configKey }, { it.id }))
        val totalPages = if (vaults.isEmpty()) 1 else ((vaults.size - 1) / PAGE_SIZE) + 1
        val page = (currentPage[player.uniqueId] ?: 0).coerceIn(0, totalPages - 1)
        currentPage[player.uniqueId] = page

        if (vaults.isEmpty()) {
            pageCache[player.uniqueId] = emptyList()
            inv.setItem(LIST_SLOTS[0], item(
                material = Material.GRAY_DYE,
                name = mm("<gray>Es sind keine Vaults konfiguriert</gray>"),
            ))
        } else {
            val claims = plugin.store.vaultClaimStore
            val diamonds = plugin.store.vaultDiamondStore
            val pageVaults = vaults.drop(page * PAGE_SIZE).take(PAGE_SIZE)
            pageCache[player.uniqueId] = pageVaults

            pageVaults.forEachIndexed { index, vault ->
                val claim = claims.getClaim(vault.id)
                val vaultDiamonds = diamonds.getVaultCount(vault.id)
                val teamColor = teamColor(vault.team)

                val statusLore = if (claim != null) {
                    val ownerName = getOfflinePlayer(claim.owner).name ?: "Unbekannt"
                    mm("<gray>Status: </gray><green>Beansprucht</green> <gray>von</gray> <white>$ownerName</white>")
                } else {
                    mm("<gray>Status: </gray><yellow>Frei</yellow>")
                }

                inv.setItem(LIST_SLOTS[index], item(
                    material = Material.CHEST,
                    name = mm("<$teamColor><bold>${vault.displayName}</bold></$teamColor>"),
                    lore = listOf(
                        mm("<gray>Team: </gray><$teamColor>${vault.team.displayName}</$teamColor>"),
                        statusLore,
                        mm("<gray>Diamanten: </gray><aqua>$vaultDiamonds</aqua>"),
                        mm(""),
                        mm("<gray>Welt: </gray><white>${vault.world}</white>"),
                        mm("<gray>Position: </gray><white>${formatLocation(vault)}</white>"),
                    ),
                    glow = claim != null,
                ))
            }
        }

        inv.setItem(SLOT_PREV_PAGE, item(
            material = if (page > 0) Material.ARROW else Material.GRAY_DYE,
            name = if (page > 0) mm("<yellow><bold>◄ Vorherige Seite</bold></yellow>") else mm("<dark_gray>Erste Seite</dark_gray>"),
        ))
        inv.setItem(SLOT_PAGE_INFO, item(
            material = Material.MAP,
            name = mm("<white><bold>Seite ${page + 1} / $totalPages</bold></white>"),
            lore = listOf(mm("<gray>${vaults.size} Vault(s) insgesamt</gray>")),
        ))
        inv.setItem(SLOT_NEXT_PAGE, item(
            material = if (page < totalPages - 1) Material.ARROW else Material.GRAY_DYE,
            name = if (page < totalPages - 1) mm("<yellow><bold>Nächste Seite ►</bold></yellow>") else mm("<dark_gray>Letzte Seite</dark_gray>"),
        ))
    }

    fun handleVaultListClick(player: Player, slot: Int, inv: Inventory) {
        when (slot) {
            SLOT_PAGE_INFO -> resetPage(player)

            SLOT_PREV_PAGE -> {
                val page = currentPage[player.uniqueId] ?: 0
                if (page > 0) {
                    currentPage[player.uniqueId] = page - 1
                    populateVaultListMenu(inv, player)
                }
            }

            SLOT_NEXT_PAGE -> {
                val vaults = plugin.vaultManager.getAllVaults()
                val totalPages = if (vaults.isEmpty()) 1 else ((vaults.size - 1) / PAGE_SIZE) + 1
                val page = currentPage[player.uniqueId] ?: 0
                if (page < totalPages - 1) {
                    currentPage[player.uniqueId] = page + 1
                    populateVaultListMenu(inv, player)
                }
            }

            in LIST_SLOTS -> {
                val vaults = pageCache[player.uniqueId] ?: return
                val index = LIST_SLOTS.indexOf(slot)
                val vault = vaults.getOrNull(index) ?: return

                val claims = plugin.store.vaultClaimStore
                val diamonds = plugin.store.vaultDiamondStore
                val claim = claims.getClaim(vault.id)
                val vaultDiamonds = diamonds.getVaultCount(vault.id)
                val teamColor = teamColor(vault.team)

                val statusText = if (claim != null) {
                    val ownerName = getOfflinePlayer(claim.owner).name ?: "Unbekannt"
                    "<green>Beansprucht</green> <gray>von</gray> <white>$ownerName</white>"
                } else {
                    "<yellow>Frei</yellow>"
                }

                player.sendMessage(mm(
                    "<$teamColor>${vault.displayName}</$teamColor> <gray>-</gray> $statusText " +
                            "<gray>|</gray> <aqua>$vaultDiamonds</aqua><gray> Diamanten</gray> " +
                            "<gray>|</gray> <white>${vault.world} ${formatLocation(vault)}</white>"
                ))
            }
        }
    }

    private fun formatLocation(vault: VaultRegion): String {
        val centerX = (vault.minX + vault.maxX) / 2
        val centerZ = (vault.minZ + vault.maxZ) / 2
        return "$centerX, ${vault.minY}, $centerZ"
    }

    private fun teamColor(team: de.davidsw.diawars.managers.Team): String = when (team) {
        de.davidsw.diawars.managers.Team.TEAM_A -> "green"
        de.davidsw.diawars.managers.Team.TEAM_B -> "blue"
    }
}