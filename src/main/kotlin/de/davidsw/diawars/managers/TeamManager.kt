package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import java.util.UUID

enum class Team(val configKey: String, var displayName: String) {
    TEAM_A("team-a", "Team A"),
    TEAM_B("team-b", "Team B");

    fun opponent(): Team = when (this) {
        TEAM_A -> TEAM_B
        TEAM_B -> TEAM_A
    }
}

class TeamManager(private val plugin: Diawars) {
    private val playerTeams = mutableMapOf<UUID, Team>()

    init {
        loadTeamsFromConfig()
    }

    private fun loadTeamFromConfig(team: Team) {
        team.displayName = plugin.config.getString("teams.${team.configKey}.display-name") ?: team.displayName
        plugin.config.getStringList("teams.${team.configKey}.players").forEach { uuidString ->
            try {
                val uuid = UUID.fromString(uuidString)
                if (uuid in playerTeams) {
                    plugin.logger.warning("$uuidString exists in multiple teams, current team: $team")
                }
                playerTeams[uuid] = team
            } catch (_: IllegalArgumentException) {
                plugin.logger.warning("Invalid UUID in config for ${team.configKey}: $uuidString")
            }
        }
    }

    fun loadTeamsFromConfig() {
        playerTeams.clear()

        loadTeamFromConfig(Team.TEAM_A)
        loadTeamFromConfig(Team.TEAM_B)

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