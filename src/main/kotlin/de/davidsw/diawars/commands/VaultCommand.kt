package de.davidsw.diawars.commands

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import de.davidsw.diawars.util.MiniMessageHelper.pmm
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class VaultCommand(private val plugin: Diawars): CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden!")
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "claim" -> handleClaim(sender)
            "unclaim" -> handleUnclaim(sender)
            "invite" -> handleInvite(sender, args)
            "ban" -> handleBan(sender, args)
            "info" -> handleInfo(sender)
            else -> sendHelp(sender)
        }

        return true
    }

    private fun handleClaim(player: Player) {
        val claims = plugin.store.vaultClaimStore
        val team = plugin.teamManager.getPlayerTeam(player.uniqueId)
        if (team == null) {
            player.sendMessage(mm("<red>Du bist in keinem Team!</red>"))
            return
        }
        if (claims.hasClaimedAnyVault(player.uniqueId)) {
            player.sendMessage(mm("<red>Du hast bereits einen Vault beansprucht! Nutze <yellow>/vault unclaim</yellow> um es freizugeben.</red>"))
            return
        }

        val vault = plugin.vaultManager.findNearbyVault(player.location)
        if (vault == null) {
            player.sendMessage(mm("<red>Du musst in der Nähe eines Vaults stehen um es zu beanspruchen!</red>"))
            return
        }
        if (vault.team != team) {
            player.sendMessage(mm("<red>Dieses Vault gehört nicht deinem Team!</red>"))
            return
        }
        if (claims.isVaultClaimed(vault.id)) {
            player.sendMessage(mm("<red>Dieses Vault wurde bereits von einem anderen Spieler beansprucht!</red>"))
            return
        }

        claims.claim(vault.id, player.uniqueId)
        player.sendMessage(mm("<green>✓ Du hast das Vault <gold>${vault.id}</gold> beansprucht!</green>"))
    }

    private fun handleUnclaim(player: Player) {
        val claims = plugin.store.vaultClaimStore
        val claim = claims.getClaimByOwner(player.uniqueId)
        if (claim == null) {
            player.sendMessage(mm("<red>Du hast kein Vault beansprucht!</red>"))
            return
        }
        claims.unclaim(claim.vaultId)
        player.sendMessage(mm("<yellow>Du hast dein Vault <gold>${claim.vaultId}</gold> freigegeben.</yellow>"))
    }

    private fun handleInvite(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(mm("<red>Verwendung: /vault invite &lt;spieler&gt;</red>"))
            return
        }
        val claims = plugin.store.vaultClaimStore
        val claim = claims.getClaimByOwner(player.uniqueId)
        if (claim == null) {
            player.sendMessage(mm("<red>Du hast kein Vault beansprucht!</red>"))
            return
        }

        val target = Bukkit.getPlayerExact(args[1])
        if (target == null) {
            player.sendMessage(mm("<red>Dieser Spieler ist nicht online!</red>"))
            return
        }
        if (target.uniqueId == player.uniqueId) {
            player.sendMessage(mm("<red>Du kannst dich nicht selbst einladen!</red>"))
            return
        }
        if (!plugin.teamManager.arePlayersInSameTeam(player.uniqueId, target.uniqueId)) {
            player.sendMessage(mm("<red>Du kannst nur Spieler aus deinem eigenen Team einladen!</red>"))
            return
        }
        if (target.uniqueId in claim.invited) {
            player.sendMessage(mm("<red>${target.name} ist bereits eingeladen!</red>"))
            return
        }

        claims.invite(claim.vaultId, target.uniqueId)
        player.sendMessage(mm("<green>✓ <gold>${target.name}</gold> wurde zu deinem Vault eingeladen!</green>"))
        target.sendMessage(pmm("<green>Du wurdest von <gold>${player.name}</gold> zu seinem Vault eingeladen! Du kannst dort jetzt Diamantblöcke platzieren.</green>"))
    }

    private fun handleBan(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(mm("<red>Verwendung: /vault ban &lt;spieler&gt;</red>"))
            return
        }
        val claims = plugin.store.vaultClaimStore
        val claim = claims.getClaimByOwner(player.uniqueId)
        if (claim == null) {
            player.sendMessage(mm("<red>Du hast kein Vault beansprucht!</red>"))
            return
        }

        val targetId = Bukkit.getOfflinePlayer(args[1]).uniqueId
        if (targetId !in claim.invited) {
            player.sendMessage(mm("<red>Dieser Spieler ist nicht zu deinem Vault eingeladen!</red>"))
            return
        }

        claims.ban(claim.vaultId, targetId)
        player.sendMessage(mm("<yellow>✓ <gold>${args[1]}</gold> wurde aus deinem Vault entfernt!</yellow>"))
        plugin.messageManager.sendOrQueue(
            targetId,
            "<red>Du wurdest aus dem Vault von <gold>${player.name}</gold> entfernt!</red>",
            true,
        )
    }

    private fun handleInfo(player: Player) {
        val claims = plugin.store.vaultClaimStore
        val claim = claims.getClaimByOwner(player.uniqueId)
        if (claim == null) {
            player.sendMessage(mm("<gray>Du hast kein Vault beansprucht. Nutze <yellow>/vault claim</yellow> in der Nähe eines Vaults.</gray>"))
            return
        }
        val invitedNames = claim.invited.map { Bukkit.getOfflinePlayer(it).name ?: "Unbekannt" }
        val lines = mutableListOf(
            "<gold>=== Dein Vault ===</gold>",
            "<gray>Vault: <white>${claim.vaultId}</white></gray>",
        )
        lines += if (invitedNames.isEmpty()) {
            "<gray>Eingeladen: <dark_gray>Niemand</dark_gray></gray>"
        } else {
            "<gray>Eingeladen: <white>${invitedNames.joinToString(", ")}</white></gray>"
        }
        player.sendMessage(mm(lines.joinToString("\n")))
    }

    private fun sendHelp(player: Player) {
        val message = mm("""
            <yellow><bold>VAULT-BEFEHLE</bold></yellow>
            
            <yellow>/vault claim</yellow><gray> - Vault in der Nähe beanspruchen</gray>
            <yellow>/vault unclaim</yellow><gray> - Dein Vault freigeben</gray>
            <yellow>/vault invite &lt;spieler&gt;</yellow><gray> - Teammitglied einladen</gray>
            <yellow>/vault ban &lt;spieler&gt;</yellow><gray> - Einladung entfernen</gray>
            <yellow>/vault info</yellow><gray> - Deinen Vault-Status anzeigen</gray>
        """.trimIndent())
        player.sendMessage(message)
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("claim", "unclaim", "invite", "ban", "info")
                .filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && (args[0].equals("invite", true) || args[0].equals("ban", true))) {
            if (sender !is Player) return emptyList()
            val team = plugin.teamManager.getPlayerTeam(sender.uniqueId) ?: return emptyList()
            return plugin.teamManager.getTeamMembers(team)
                .mapNotNull { Bukkit.getPlayer(it)?.name }
                .filter { it.startsWith(args[1], ignoreCase = true) }
        }
        return emptyList()
    }
}