package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import org.bukkit.Bukkit.getWorld
import org.bukkit.Location
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import java.util.UUID

class LobbyManager(private val plugin: Diawars) {
    private val playersInLobby = mutableSetOf<UUID>()
    private val states get() = plugin.store.playerStateStore

    val worldName: String
        get() = plugin.config.getString("lobby.world", "lobby") ?: "lobby"

    fun isLobbyWorld(worldName: String): Boolean = worldName == this.worldName

    fun isInLobby(playerId: UUID): Boolean = playerId in playersInLobby

    /** Creates/loads the lobby world once on plugin startup. */
    fun ensureWorldLoaded() {
        val world = getWorld(worldName) ?: WorldCreator(worldName)
            .type(org.bukkit.WorldType.FLAT)
            .generatorSettings("""{"layers":[],"biome":"minecraft:the_void"}""")
            .generateStructures(false)
            .createWorld()
        if (world == null) {
            plugin.logger.severe("Could not create/load the lobby world '$worldName'!")
        } else {
            plugin.logger.info("Lobby world '$worldName' is ready.")
        }
    }

    fun sendToLobby(player: Player): Boolean {
        if (isInLobby(player.uniqueId)) return false
        if (plugin.eventManager.getSession(player.uniqueId) != null) return false

        val world = getWorld(worldName)
            ?: WorldCreator(worldName)
                .type(org.bukkit.WorldType.FLAT)
                .generatorSettings("""{"layers":[],"biome":"minecraft:the_void"}""")
                .generateStructures(false)
                .createWorld()
            ?: return false

        states.saveState(player)
        playersInLobby.add(player.uniqueId)

        val section = plugin.config.getConfigurationSection("lobby.spawn-point") ?: return false
        val location = Location(
            world,
            section.getDouble("x"),
            section.getDouble("y"),
            section.getDouble("z"),
            section.getDouble("yaw").toFloat(),
            section.getDouble("pitch").toFloat(),
        )

        player.teleport(location)

        return true
    }

    fun leaveLobby(player: Player): Boolean {
        if (!isInLobby(player.uniqueId)) return false
        playersInLobby.remove(player.uniqueId)

        if (!states.restoreState(player, true)) {
            player.teleport(plugin.server.worlds.first().spawnLocation)
        }

        return true
    }

    fun handlePlayerJoin(player: Player) {
        if (!isInLobby(player.uniqueId) && states.hasSavedState(player.uniqueId) && player.world.name == worldName) {
            states.restoreState(player)
            player.sendMessage(de.davidsw.diawars.util.MiniMessageHelper.mm(
                "<yellow>Deine Lobby-Sitzung wurde durch einen Serverneustart unterbrochen. Du wurdest zurückgesetzt.</yellow>"
            ))
        }
    }
}