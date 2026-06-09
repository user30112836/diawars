package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import io.papermc.paper.scoreboard.numbers.NumberFormat
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
        for (player in Bukkit.getOnlinePlayers()) {
            plugin.store.playerDiamondStore.snapshotIfChanged(player)
            updateListName(player)
            updateSidebar(player)
        }
    }

    private fun updateListName(player: Player) {
        val pvpEnabled = plugin.store.pvpStatusStore.isPvPEnabled(player.uniqueId)

        val playerDiamonds = plugin.store.playerDiamondStore.getStoredCount(player.uniqueId)
        val playerColor = when (pvpEnabled) {
            true -> "§4" // dark_red
            false -> "§2" // dark_green
        }

        val team = plugin.teamManager.getPlayerTeam(player.uniqueId) ?: return
        val teamColor = when (team) {
            Team.TEAM_A -> "§a" // green
            Team.TEAM_B -> "§9" // blue
        }
        val teamLabel = team.displayName

        player.setPlayerListName("$teamColor[$teamLabel] $playerColor${player.name} §b$playerDiamonds")
    }

    private fun updateSidebar(player: Player) {
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
        val scoreboard: Scoreboard = Bukkit.getScoreboardManager().newScoreboard
        val objective: Objective = scoreboard.registerNewObjective("diawars", "dummy", "§6§lDiawars")
        objective.displaySlot = DisplaySlot.SIDEBAR
        objective.numberFormat(NumberFormat.blank())

        val limit = plugin.config.getInt("diamond-limit", 32)

        val teamLabel = team.displayName
        val teamColor = teamColor(team)

        val playerColor = when {
            playerDiamonds > limit -> "§c" // red
            playerDiamonds == limit -> "§e" // yellow
            else -> "§b" // aqua
        }

        val opponentsLabel = opponents.displayName
        val opponentsColor = teamColor(opponents)

        val lines = listOf(
            " ",
            "§fTeam",
            "  $teamColor$teamLabel §7| §b$teamDiamonds",
            "  $opponentsColor$opponentsLabel §7| §b$opponentsDiamonds",
            " ",
            "§fDeine Diamanten",
            "  $playerColor$playerDiamonds / $limit",
            " ",
            if (ownZone) {
                "§2 Heimatzone"
            } else {
                "§4 Gegnerzone"
            },
        )

        lines.forEachIndexed { index, text ->
            objective.getScore(text).score = lines.size - index
        }

        player.scoreboard = scoreboard
    }

    private fun teamColor(team: Team) = when (team) {
        Team.TEAM_A -> "§a" // green
        Team.TEAM_B -> "§9" // blue
    }

    fun clearPlayer(player: Player) {
        lastState.remove(player.uniqueId)
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    }
}
