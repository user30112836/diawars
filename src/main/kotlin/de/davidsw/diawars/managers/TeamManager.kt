package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import org.bukkit.Bukkit
import java.util.UUID

enum class Team(val configKey: String, val displayName: String) {
    TEAM_A("team-a", "Team A"),
    TEAM_B("team-b", "Team B");

    fun opponent(): Team = when (this) {
        TEAM_A -> TEAM_B
        TEAM_B -> TEAM_A
    }

    companion object {
        fun fromConfigKey(key: String): Team? {
            return entries.find { it.configKey == key }
        }
    }
}

class TeamManager(private val plugin: Diawars) {
    private val playerTeams = mutableMapOf<UUID, Team>()

    init {
        loadTeamsFromConfig()
    }

    fun loadTeamsFromConfig() {
        playerTeams.clear()

        val teamAPlayers = plugin.config.getStringList("teams.${Team.TEAM_A.configKey}")
        val teamBPlayers = plugin.config.getStringList("teams.${Team.TEAM_B.configKey}")

        teamAPlayers.forEach { playerName ->
            val player = Bukkit.getOfflinePlayer(playerName)
            playerTeams[player.uniqueId] = Team.TEAM_A
        }

        teamBPlayers.forEach { playerName ->
            val player = Bukkit.getOfflinePlayer(playerName)
            playerTeams[player.uniqueId] = Team.TEAM_B
        }

        plugin.logger.info("Loaded teams: ${playerTeams.values}")
    }

    fun getPlayerTeam(playerId: UUID): Team? {
        return playerTeams[playerId]
    }

    fun isPlayerInTeam(playerId: UUID): Boolean {
        return playerTeams.containsKey(playerId)
    }

    fun arePlayersInSameTeam(playerA: UUID, playerB: UUID): Boolean {
        return getPlayerTeam(playerA) == getPlayerTeam(playerB)
    }

    fun getTeamMembers(team: Team): Set<UUID> = playerTeams.filterValues { it == team }.keys
}