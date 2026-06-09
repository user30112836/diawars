package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import org.bukkit.entity.Player

class ScoresManager(private val plugin: Diawars) {
    fun handleInfo(sender: Player) {
        plugin.store.playerDiamondStore.snapshotIfChanged(sender)

        val limit = plugin.config.getInt("diamond-limit", 32)
        val playerDiamonds = plugin.store.playerDiamondStore.getStoredCount(sender.uniqueId)
        val playerColor = when {
            playerDiamonds > limit -> "§c" // red
            playerDiamonds == limit -> "§e" // yellow
            else -> "§b" // aqua
        }

        val team = plugin.teamManager.getPlayerTeam(sender.uniqueId) ?: return
        val teamLabel = team.displayName
        val teamColor = teamColor(team)
        val teamDiamonds = plugin.store.playerDiamondStore.getTotalTeamCount(team)
        val teamOnlineDiamonds = plugin.store.playerDiamondStore.getOnlineTeamCount(team)
        val teamOfflineDiamonds = plugin.store.playerDiamondStore.getOfflineTeamCount(team)

        val opponents = team.opponent()
        val opponentsLabel = opponents.displayName
        val opponentsColor = teamColor(opponents)
        val opponentsDiamonds = plugin.store.playerDiamondStore.getTotalTeamCount(opponents)
        val opponentsOnlineDiamonds = plugin.store.playerDiamondStore.getOnlineTeamCount(opponents)
        val opponentsOfflineDiamonds = plugin.store.playerDiamondStore.getOfflineTeamCount(opponents)

        sender.sendMessage("§6=== Punkte ===")
        sender.sendMessage("§7Deine Diamanten: $playerColor$playerDiamonds / $limit")
        sender.sendMessage("$teamColor=== $teamLabel ===")
        sender.sendMessage("§7Team Diamanten: §b$teamDiamonds")
        sender.sendMessage("§7Online Team Diamanten: §b$teamOnlineDiamonds")
        sender.sendMessage("§7Offline Team Diamanten: §b$teamOfflineDiamonds")
        sender.sendMessage("$opponentsColor=== $opponentsLabel ===")
        sender.sendMessage("§7Team Diamanten: §b$opponentsDiamonds")
        sender.sendMessage("§7Online Team Diamanten: §b$opponentsOnlineDiamonds")
        sender.sendMessage("§7Offline Team Diamanten: §b$opponentsOfflineDiamonds")
    }

    private fun teamColor(team: Team) = when (team) {
        Team.TEAM_A -> "§a" // green
        Team.TEAM_B -> "§9" // blue
    }
}