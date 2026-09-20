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
    private val states get() = plugin.store.lobbyStateStore

    val worldName: String
        get() = plugin.config.getString("lobby.world", "lobby") ?: "lobby"

    fun isLobbyWorld(worldName: String): Boolean = worldName == this.worldName

    fun isInLobby(playerId: UUID): Boolean = playerId in playersInLobby

    /** When true, non-admin players cannot leave the lobby (admin lock). */
    var locked = false

    fun lobbyPlayerCount(): Int = playersInLobby.size

    /** Lobby spawn from config, or null when world/section are missing. */
    fun getSpawnLocation(): Location? {
        val world = getWorld(worldName)
            ?: WorldCreator(worldName)
                .type(WorldType.FLAT)
                .generatorSettings("""{"layers":[],"biome":"minecraft:the_void"}""")
                .generateStructures(false)
                .createWorld()
            ?: return null
        val section = plugin.config.getConfigurationSection("lobby.spawn-point") ?: return null
        return Location(
            world,
            section.getDouble("x"),
            section.getDouble("y"),
            section.getDouble("z"),
            section.getDouble("yaw").toFloat(),
            section.getDouble("pitch").toFloat(),
        )
    }

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
        // No escape to the safe lobby while tagged in a fight.
        if (plugin.pvpManager.isInFight(player.uniqueId)) return false

        val location = getSpawnLocation() ?: return false

        states.saveState(player)
        player.teleport(location)
        playersInLobby.add(player.uniqueId)

        return true
    }

    fun leaveLobby(player: Player): Boolean {
        if (!isInLobby(player.uniqueId)) return false
        // Locked lobby: regular players stay inside, admins always pass.
        if (locked && !player.hasPermission("diawars.admin")) return false
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
        if (isInLobby(player.uniqueId)) return
        if (!states.hasSavedState(player.uniqueId)) return

        playersInLobby.add(player.uniqueId)
        player.sendMessage(mm(
            "<yellow>Deine Lobby-Sitzung wurde durch einen Serverneustart unterbrochen. Du wurdest wieder in die Lobby gesetzt.</yellow>"
        ))
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
        grantStarterDiamonds(player)
    }

    /** One-time starter grant on first join. Marked before granting so a rejoin
     * before leaving the lobby cannot pay out twice; a full inventory or event
     * world is covered by RewardManager's pending balance. */
    private fun grantStarterDiamonds(player: Player) {
        val onboarding = plugin.store.onboardingStore
        if (onboarding.hasReceivedStarterDiamonds(player.uniqueId)) return
        // Players without a team are kicked on join: don't consume their
        // one-time reward, they receive it on the join after being added.
        if (!plugin.teamManager.isPlayerInTeam(player.uniqueId) && !player.isOp) return
        onboarding.markStarterDiamondsReceived(player.uniqueId)
        val amount = plugin.config.getInt("starter-diamonds", 32)
        if (amount > 0) {
            plugin.rewardManager.grantDiamondReward(player, amount)
        }
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