package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit.getOnlinePlayers
import org.bukkit.Bukkit.getScoreboardManager
import org.bukkit.scoreboard.Criteria
import java.util.UUID

class DiamondScoreboardManager(private val plugin: Diawars) {
    private var taskId: Int = -1

    private data class BoardState(
        val playerDiamonds: Int,
        val teamDiamonds: Int,
        val opponentsDiamonds: Int,
        val ownZone: Boolean,
    )
    private val lastState = mutableMapOf<UUID, BoardState>()

    fun start() {
        stop()
        taskId = plugin.server.scheduler.runTaskTimer(plugin, Runnable { update() }, 0L, 40L).taskId // 40 Ticks = 2 Second
    }

    fun stop() {
        if (taskId != -1) {
            plugin.server.scheduler.cancelTask(taskId)
            taskId = -1
        }
    }

    fun update() {
        for (player in getOnlinePlayers()) {
            plugin.store.playerDiamondStore.snapshotIfChanged(player)
            updateListName(player)
            updateSidebar(player)
        }
    }

    private fun updateListName(player: Player) {
        val pvpEnabled = plugin.store.pvpStatusStore.isPvPEnabled(player.uniqueId)
        val isAfk = plugin.afkManager.isAfk(player.uniqueId)

        val playerDiamonds = plugin.store.playerDiamondStore.getStoredCount(player.uniqueId)
        val playerColor = when {
            isAfk -> NamedTextColor.GRAY
            pvpEnabled -> NamedTextColor.DARK_RED
            else -> NamedTextColor.DARK_GREEN
        }

        val team = plugin.teamManager.getPlayerTeam(player.uniqueId) ?: return
        val teamColor = when (team) {
            Team.TEAM_A -> NamedTextColor.GREEN
            Team.TEAM_B -> NamedTextColor.BLUE
        }
        val teamLabel = team.displayName

        player.playerListName(mm("<$teamColor>[$teamLabel]</$teamColor> <$playerColor>${player.name}</$playerColor> <aqua>$playerDiamonds</aqua>"))
    }

    private fun updateSidebar(player: Player) {
        if (!plugin.store.scoreboardPreferencesStore.isSidebarEnabled(player.uniqueId)) {
            if (lastState.containsKey(player.uniqueId)) {
                player.scoreboard = getScoreboardManager().mainScoreboard
                lastState.remove(player.uniqueId)
            }
            return
        }

        val playerDiamonds = plugin.store.playerDiamondStore.getStoredCount(player.uniqueId)
        val team = plugin.teamManager.getPlayerTeam(player.uniqueId) ?: return
        val teamDiamonds = plugin.store.playerDiamondStore.getTotalTeamCount(team)
        val opponents = team.opponent()
        val opponentsDiamonds = plugin.store.playerDiamondStore.getTotalTeamCount(opponents)
        val ownZone = plugin.zoneManager.isInOwnZone(player)

        val newState = BoardState(playerDiamonds, teamDiamonds, opponentsDiamonds, ownZone)
        if (lastState[player.uniqueId] != newState) {
            renderSidebar(
                player, playerDiamonds,
                team, teamDiamonds,
                opponents, opponentsDiamonds,
                ownZone
            )
            lastState[player.uniqueId] = newState
        }
    }

    private fun renderSidebar(
        player: Player,
        playerDiamonds: Int,
        team: Team,
        teamDiamonds: Int,
        opponents: Team,
        opponentsDiamonds: Int,
        ownZone: Boolean,
    ) {
        val scoreboard: Scoreboard = getScoreboardManager().newScoreboard
        val objective: Objective = scoreboard.registerNewObjective("diawars", Criteria.DUMMY, mm("<gold><bold>Diawars</bold></gold>"))
        objective.displaySlot = DisplaySlot.SIDEBAR
        objective.numberFormat(NumberFormat.blank())

        val limit = plugin.config.getInt("diamond-limit", 32)

        val teamLabel = team.displayName
        val teamColor = teamColor(team)

        val playerColor = when {
            playerDiamonds > limit -> NamedTextColor.RED
            playerDiamonds == limit -> NamedTextColor.YELLOW
            else -> NamedTextColor.AQUA
        }

        val opponentsLabel = opponents.displayName
        val opponentsColor = teamColor(opponents)

        val mm = miniMessage()
        val lines = listOf(
            mm.deserialize(" "),
            mm.deserialize("<white>Team</white>"),
            mm.deserialize("  <$teamColor>$teamLabel</$teamColor> <gray>|</gray> <aqua>$teamDiamonds</aqua>"),
            mm.deserialize("  <$opponentsColor>$opponentsLabel</$opponentsColor> <gray>|</gray> <aqua>$opponentsDiamonds</aqua>"),
            mm.deserialize(" "),
            mm.deserialize("<white>Deine Diamanten</white>"),
            mm.deserialize("  <$playerColor>$playerDiamonds / $limit</$playerColor>"),
            mm.deserialize(" "),
            if (plugin.zoneManager.isZoneWorld(player.world)) {
                if (ownZone) {
                    mm.deserialize("<dark_green> Heimatzone</dark_green>")
                } else {
                    mm.deserialize("<dark_red> Gegnerzone</dark_red>")
                }
            } else {
                mm.deserialize("<gold> Neutrale Zone</gold>")
            },
        )

        lines.forEachIndexed { index, component ->
            val text = LegacyComponentSerializer.legacySection().serialize(component)
            objective.getScore(text).score = lines.size - index
        }

        player.scoreboard = scoreboard
    }

    private fun teamColor(team: Team) = when (team) {
        Team.TEAM_A -> NamedTextColor.GREEN
        Team.TEAM_B -> NamedTextColor.BLUE
    }

    fun clearPlayer(player: Player) {
        lastState.remove(player.uniqueId)
        player.scoreboard = getScoreboardManager().mainScoreboard
    }
}
