package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import org.bukkit.Location
import org.bukkit.entity.Player

class ZoneManager(private val plugin: Diawars) {
    companion object {
        const val ZONE_BOUNDARY = 0.0
    }

    fun isInOwnZone(player: Player): Boolean {
        val team = plugin.teamManager.getPlayerTeam(player.uniqueId) ?: return false
        val x = player.location.x

        return when (team) {
            Team.TEAM_A -> x > ZONE_BOUNDARY
            Team.TEAM_B -> x <= ZONE_BOUNDARY
        }
    }

    fun isInTeamZone(player: Player, team: Team): Boolean {
        val x = player.location.x

        return when (team) {
            Team.TEAM_A -> x > ZONE_BOUNDARY
            Team.TEAM_B -> x <= ZONE_BOUNDARY
        }
    }

    fun isInPlayerZone(player: Player, location: Location): Boolean {
        val team = plugin.teamManager.getPlayerTeam(player.uniqueId) ?: return false
        val x = location.x

        return when (team) {
            Team.TEAM_A -> x > ZONE_BOUNDARY
            Team.TEAM_B -> x <= ZONE_BOUNDARY
        }
    }

    fun getZoneName(x: Int): String {
        return if (x > ZONE_BOUNDARY) {
            Team.TEAM_A.displayName + "Zone"
        } else {
            Team.TEAM_B.displayName + "Zone"
        }
    }

    fun getZoneTeam(location: Location): Team {
        return if (location.x > ZONE_BOUNDARY) {
            Team.TEAM_A
        } else {
            Team.TEAM_B
        }
    }

    fun hasCrossedBoundary(from: Location, to: Location): Boolean {
        return (from.x > ZONE_BOUNDARY) != (to.x > ZONE_BOUNDARY)
    }
}