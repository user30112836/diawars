package de.davidsw.diawars.listeners

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.managers.Team
import de.davidsw.diawars.util.MiniMessageHelper.mm
import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class MessageListener(private val plugin: Diawars): Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.messageManager.deliverPending(event.player)
    }

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        // Same tags and colors as the tab list (DiamondScoreboardManager.updateListName).
        val playerColor = when {
            plugin.afkManager.isAfk(player.uniqueId) -> NamedTextColor.GRAY
            plugin.store.pvpStatusStore.isPvPEnabled(player.uniqueId) -> NamedTextColor.DARK_RED
            else -> NamedTextColor.DARK_GREEN
        }

        val team = plugin.teamManager.getPlayerTeam(player.uniqueId)
        val prefix = if (team != null) {
            val teamColor = when (team) {
                Team.TEAM_A -> NamedTextColor.GREEN
                Team.TEAM_B -> NamedTextColor.BLUE
            }
            mm("<$teamColor>[${team.displayName}]</$teamColor> <$playerColor>${player.name}</$playerColor>")
        } else {
            mm("<$playerColor>${player.name}</$playerColor>")
        }

        event.renderer(ChatRenderer.viewerUnaware { _, message, _ ->
            prefix.append(Component.text(": ")).append(message)
        })
    }
}