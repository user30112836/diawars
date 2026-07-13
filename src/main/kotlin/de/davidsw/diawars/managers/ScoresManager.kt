package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.entity.Player
import net.kyori.adventure.text.format.NamedTextColor

class ScoresManager(private val plugin: Diawars) {
    fun handleInfo(sender: Player) {
        plugin.store.playerDiamondStore.snapshotIfChanged(sender)

        val limit = plugin.config.getInt("diamond-limit", 32)
        val playerDiamonds = plugin.store.playerDiamondStore.getStoredCount(sender.uniqueId)
        val playerColor = when {
            playerDiamonds > limit -> NamedTextColor.RED
            playerDiamonds == limit -> NamedTextColor.YELLOW
            else -> NamedTextColor.AQUA
        }

        val team = plugin.teamManager.getPlayerTeam(sender.uniqueId) ?: return
        val teamLabel = team.displayName
        val teamColor = teamColor(team)
        val teamVaultDiamonds = plugin.store.vaultStore.getVaultCount(team)
        val teamDiamonds = plugin.store.playerDiamondStore.getTotalTeamCount(team) + teamVaultDiamonds
        val teamOnlineDiamonds = plugin.store.playerDiamondStore.getOnlineTeamCount(team)
        val teamOfflineDiamonds = plugin.store.playerDiamondStore.getOfflineTeamCount(team)

        val opponents = team.opponent()
        val opponentsLabel = opponents.displayName
        val opponentsColor = teamColor(opponents)
        val opponentsVaultDiamonds = plugin.store.vaultStore.getVaultCount(opponents)
        val opponentsDiamonds = plugin.store.playerDiamondStore.getTotalTeamCount(opponents) + opponentsVaultDiamonds
        val opponentsOnlineDiamonds = plugin.store.playerDiamondStore.getOnlineTeamCount(opponents)
        val opponentsOfflineDiamonds = plugin.store.playerDiamondStore.getOfflineTeamCount(opponents)

        val message = mm("""
            <gold>=== Punkte ===</gold>
            <gray>Deine Diamanten: <$playerColor>$playerDiamonds / $limit</$playerColor>
            <$teamColor>=== $teamLabel ===</$teamColor>
            <gray>Team Diamanten: <aqua>$teamDiamonds</aqua>
            <gray>Online Team Diamanten: <aqua>$teamOnlineDiamonds</aqua>
            <gray>Offline Team Diamanten: <aqua>$teamOfflineDiamonds</aqua>
            <gray>Vault Diamanten: <aqua>$teamVaultDiamonds</aqua>
            <$opponentsColor>=== $opponentsLabel ===</$opponentsColor>
            <gray>Team Diamanten: <aqua>$opponentsDiamonds</aqua>
            <gray>Online Team Diamanten: <aqua>$opponentsOnlineDiamonds</aqua>
            <gray>Offline Team Diamanten: <aqua>$opponentsOfflineDiamonds</aqua>
            <gray>Vault Diamanten: <aqua>$opponentsVaultDiamonds</aqua>
        """.trimIndent())
        sender.sendMessage(message)
    }

    private fun teamColor(team: Team) = when (team) {
        Team.TEAM_A -> NamedTextColor.GREEN
        Team.TEAM_B -> NamedTextColor.BLUE
    }
}