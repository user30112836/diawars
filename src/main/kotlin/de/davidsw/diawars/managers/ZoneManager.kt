package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player

class ZoneManager(private val plugin: Diawars) {
    companion object {
        const val ZONE_BOUNDARY = 0.0
    }

    fun isZoneWorld(world: World): Boolean {
        if (world.environment == World.Environment.THE_END) return false
        if (plugin.lobbyManager.isLobbyWorld(world.name)) return false
        return !plugin.eventManager.isEventWorld(world.name)
    }

    fun isInOwnZone(player: Player): Boolean {
        val team = plugin.teamManager.getPlayerTeam(player.uniqueId) ?: return false
        return isInTeamZone(player, team)
    }

    fun isInTeamZone(player: Player, team: Team): Boolean {
        if (!isZoneWorld(player.world)) return true
        val x = player.location.x

        return when (team) {
            Team.TEAM_A -> x > ZONE_BOUNDARY
            Team.TEAM_B -> x <= ZONE_BOUNDARY
        }
    }

    fun isInPlayerZone(player: Player, location: Location): Boolean {
        val world = location.world ?: player.world
        if (!isZoneWorld(world)) return true
        val team = plugin.teamManager.getPlayerTeam(player.uniqueId) ?: return false
        val x = location.x

        return when (team) {
            Team.TEAM_A -> x > ZONE_BOUNDARY
            Team.TEAM_B -> x <= ZONE_BOUNDARY
        }
    }

    fun hasCrossedBoundary(from: Location, to: Location): Boolean {
        if (!isZoneWorld(to.world)) return false
        return (from.x > ZONE_BOUNDARY) != (to.x > ZONE_BOUNDARY)
    }
}