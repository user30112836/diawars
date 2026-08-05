package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.MiniMessageHelper.mm
import org.bukkit.Bukkit.getWorld
import org.bukkit.Location
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.bukkit.entity.Player
import java.util.UUID

class LobbyManager(private val plugin: Diawars) {
    private val playersInLobby = mutableSetOf<UUID>()
    private val states get() = plugin.store.playerStateStore

    val worldName: String
        get() = plugin.config.getString("lobby.world", "lobby") ?: "lobby"

    fun isLobbyWorld(worldName: String): Boolean = worldName == this.worldName

    fun isInLobby(playerId: UUID): Boolean = playerId in playersInLobby

    fun ensureWorldLoaded() {
        val world = getWorld(worldName) ?: WorldCreator(worldName)
            .type(WorldType.FLAT)
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
                .type(WorldType.FLAT)
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

        val onboarding = plugin.store.onboardingStore
        if (!onboarding.hasEnteredMainWorld(player.uniqueId)) {
            onboarding.markEnteredMainWorld(player.uniqueId)
            if (!sendPlayerToOwnZone(player)) {
                player.teleport(plugin.server.worlds.first().spawnLocation)
            }
            return true
        }

        if (!states.restoreState(player, true)) {
            if (!sendPlayerToOwnZone(player)) {
                player.teleport(plugin.server.worlds.first().spawnLocation)
            }
        }

        return true
    }

    fun handlePlayerJoin(player: Player) {
        if (!isInLobby(player.uniqueId) && states.hasSavedState(player.uniqueId) && player.world.name == worldName) {
            states.restoreState(player, true)
            player.sendMessage(mm(
                "<yellow>Deine Lobby-Sitzung wurde durch einen Serverneustart unterbrochen. Du wurdest zurückgesetzt.</yellow>"
            ))
        }
    }

    fun handleOnboardingJoin(player: Player) {
        if (plugin.store.onboardingStore.hasEnteredMainWorld(player.uniqueId)) return
        if (plugin.eventManager.getSession(player.uniqueId) != null) return

        playersInLobby.add(player.uniqueId)

        if (player.world.name != worldName) {
            val world = getWorld(worldName)
                ?: WorldCreator(worldName)
                    .type(WorldType.FLAT)
                    .generatorSettings("""{"layers":[],"biome":"minecraft:the_void"}""")
                    .generateStructures(false)
                    .createWorld()
            val section = plugin.config.getConfigurationSection("lobby.spawn-point")
            if (world != null && section != null) {
                player.teleport(Location(
                    world,
                    section.getDouble("x"),
                    section.getDouble("y"),
                    section.getDouble("z"),
                    section.getDouble("yaw").toFloat(),
                    section.getDouble("pitch").toFloat(),
                ))
            }
        }

        sendWelcomeMessage(player)
    }

    private fun sendPlayerToOwnZone(player: Player): Boolean {
        val team = plugin.teamManager.getPlayerTeam(player.uniqueId) ?: return false
        val location = plugin.teamManager.getSpawnLocation(team) ?: return false
        player.teleport(location)
        return true
    }

    private fun sendWelcomeMessage(player: Player) {
        player.sendMessage(mm("""
            <gold><bold>Willkommen auf dem Server!</bold></gold>
            
            <gray>Du befindest dich aktuell in der Lobby. Verlasse sie mit <yellow>/lobby</yellow> oder über das Menü mit <yellow>/menu</yellow> um in deine Zone zu gelangen.</gray>
            
            <gray>Damit du die Zonengrenze richtig sehen kannst, stelle deine Partikel im Spiel (Grafikeinstellungen) auf <yellow>Alle</yellow> oder <yellow>Verringert</yellow> und deaktiviere partikelreduzierende Resourcenpakete/Mods.</gray>
            
            <gray>Es gibt ein Handbuch zum Plugin welches im Menü unter <yellow>/menu</yellow> zu finden ist. Dieses beinhaltet eine kurze Einführung, ausfürliche Anleitungen zu allen Features und die Regeln dieses Servers welche du durch das Spielen akzeptierst.</gray>
            
            <aqua>Viel Spaß!</aqua>
        """.trimIndent()))
    }
}