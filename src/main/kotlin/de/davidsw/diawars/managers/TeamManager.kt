package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.ConfigFiles
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
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
    private val teamsFile = ConfigFiles.resolve(plugin, "teams.yml")

    init {
        loadTeamsFromConfig()
    }

    private fun loadTeamFromConfig(config: YamlConfiguration, team: Team) {
        team.displayName = config.getString("${team.configKey}.display-name") ?: team.displayName
        config.getStringList("${team.configKey}.players").forEach { uuidString ->
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

        val config = YamlConfiguration.loadConfiguration(teamsFile)
        loadTeamFromConfig(config, Team.TEAM_A)
        loadTeamFromConfig(config, Team.TEAM_B)

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

    fun getSpawnLocation(team: Team): Location? {
        val config = YamlConfiguration.loadConfiguration(teamsFile)
        val section = config.getConfigurationSection("${team.configKey}.spawn-point") ?: return null

        return Location(
            plugin.server.worlds.first(),
            section.getDouble("x"),
            section.getDouble("y"),
            section.getDouble("z"),
            section.getDouble("yaw").toFloat(),
            section.getDouble("pitch").toFloat(),
        )
    }
}