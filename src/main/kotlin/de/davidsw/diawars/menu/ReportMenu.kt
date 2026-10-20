package de.davidsw.diawars.menu

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.ReportManager
import de.davidsw.diawars.util.MenuUtils.item
import de.davidsw.diawars.util.MiniMessageHelper.escape
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import net.kyori.adventure.text.Component
import java.util.UUID

/**
 * Report entry menu: stage 1 picks the report kind (image in main hand or
 * online player), stage 2 lists the online players as a picker. The reason
 * input then goes through the client-mod dialog (TextInputManager); commands
 * are an alternative entry point (see ReportCommand).
 */
class ReportMenu(private val plugin: Diawars) {

    companion object {
        // Stage 1: category menu
        private const val SLOT_IMAGE_REPORT = 20
        private const val SLOT_PLAYER_REPORT = 24

        // Stage 2: online player picker
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

    private val pickerPage = mutableMapOf<UUID, Int>()

    /** Targets of the list as they were rendered; clicks map back via the slot. */
    private val targetCache = mutableMapOf<UUID, List<Player>>()

    /** Drops per-player view state. Call on quit: logout may skip the close event. */
    fun clearCache(playerId: UUID) {
        pickerPage.remove(playerId)
        targetCache.remove(playerId)
    }

    // ------------------------------------------------------------------
    // stage 1: category
    // ------------------------------------------------------------------

    fun populateReportMenu(inv: Inventory, player: Player) {
        inv.setItem(SLOT_IMAGE_REPORT, item(
            material = Material.FILLED_MAP,
            name = mm("<red><bold>Bild melden</bold></red>"),
            lore = listOf(
                mm("<gray>Meldet das Bild der Bild-Karte,</gray>"),
                mm("<gray>die du in der Haupthand hältst</gray>"),
                mm("<gray>(z. B. auf einem Item-Rahmen)</gray>"),
                mm(""),
                mm("<yellow>Klicken zur Eingabe</yellow>"),
            ),
            glow = false,
        ))
        inv.setItem(SLOT_PLAYER_REPORT, item(
            material = Material.PLAYER_HEAD,
            name = mm("<aqua><bold>Spieler melden</bold></aqua>"),
            lore = listOf(
                mm("<gray>Meldet einen anderen Spieler</gray>"),
                mm(""),
                mm("<yellow>Klicken zum Auswählen</yellow>"),
            ),
            glow = false,
        ))
    }

    fun handleReportClick(player: Player, slot: Int) {
        when (slot) {
            SLOT_IMAGE_REPORT -> respond(player, plugin.reportManager.startImageReport(player))

            SLOT_PLAYER_REPORT -> plugin.menuManager.openReportPlayersMenu(player)
        }
    }

    // ------------------------------------------------------------------
    // stage 2: online player picker
    // ------------------------------------------------------------------

    fun populateReportPlayersMenu(inv: Inventory, player: Player) {
        ALL_SLOTS.forEach { inv.setItem(it, null) }

        val targets = onlineTargets(player)
        val totalPages = if (targets.isEmpty()) 1 else ((targets.size - 1) / PAGE_SIZE) + 1
        val page = (pickerPage[player.uniqueId] ?: 0).coerceIn(0, totalPages - 1)
        pickerPage[player.uniqueId] = page

        if (targets.isEmpty()) {
            targetCache[player.uniqueId] = emptyList()
            inv.setItem(LIST_SLOTS[0], item(
                material = Material.GRAY_DYE,
                name = mm("<gray>Es sind keine anderen Spieler online</gray>"),
            ))
        } else {
            val pageTargets = targets.drop(page * PAGE_SIZE).take(PAGE_SIZE)
            targetCache[player.uniqueId] = pageTargets
            pageTargets.forEachIndexed { index, target ->
                inv.setItem(LIST_SLOTS[index], playerHead(
                    target = target,
                    lore = listOf(mm("<gray>Klicken zum Melden</gray>")),
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
            lore = listOf(mm("<gray>${targets.size} Spieler online</gray>")),
        ))
        inv.setItem(SLOT_NEXT_PAGE, item(
            material = if (page < totalPages - 1) Material.ARROW else Material.GRAY_DYE,
            name = if (page < totalPages - 1) mm("<yellow><bold>Nächste Seite ►</bold></yellow>") else mm("<dark_gray>Letzte Seite</dark_gray>"),
        ))
    }

    fun handleReportPlayersClick(player: Player, slot: Int, inv: Inventory) {
        when (slot) {
            SLOT_PAGE_INFO -> pickerPage[player.uniqueId] = 0

            SLOT_PREV_PAGE -> {
                val page = pickerPage[player.uniqueId] ?: 0
                if (page > 0) {
                    pickerPage[player.uniqueId] = page - 1
                    populateReportPlayersMenu(inv, player)
                }
            }

            SLOT_NEXT_PAGE -> {
                val targets = onlineTargets(player)
                val totalPages = if (targets.isEmpty()) 1 else ((targets.size - 1) / PAGE_SIZE) + 1
                val page = pickerPage[player.uniqueId] ?: 0
                if (page < totalPages - 1) {
                    pickerPage[player.uniqueId] = page + 1
                    populateReportPlayersMenu(inv, player)
                }
            }

            in LIST_SLOTS -> {
                val targets = targetCache[player.uniqueId] ?: return
                val target = targets.getOrNull(LIST_SLOTS.indexOf(slot)) ?: return
                respond(player, plugin.reportManager.startPlayerReport(player, target.name))
            }
        }
    }

    /** Fresh online state per render/click round; deterministic pagination order. */
    private fun onlineTargets(player: Player): List<Player> = Bukkit.getOnlinePlayers()
        .filter { it.uniqueId != player.uniqueId }
        .sortedBy { it.name.lowercase() }

    private fun respond(player: Player, result: ReportManager.Result) {
        when (result) {
            is ReportManager.Result.Success -> player.sendMessage(mm(result.message))
            is ReportManager.Result.Error -> player.sendMessage(mm(result.message))
        }
    }

    /** Head item with the live player's skin (Steve head on Bedrock viewers). */
    private fun playerHead(target: Player, lore: List<Component>): ItemStack {
        val stack = ItemStack(Material.PLAYER_HEAD)
        val headMeta = stack.itemMeta as SkullMeta
        headMeta.owningPlayer = target
        headMeta.displayName(mm("<white><bold>${escape(target.name)}</bold></white>"))
        headMeta.lore(lore)
        stack.itemMeta = headMeta
        return stack
    }
}
