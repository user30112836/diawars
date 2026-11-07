package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.SqlOp
import org.bukkit.Bukkit.getWorld
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID

private data class SpawnPoint(
    val worldName: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
)

class PlayerSpawnStore(plugin: Diawars) : SqlStore(plugin, "player_spawns") {
    private val mainSpawns = mutableMapOf<UUID, SpawnPoint>()
    private val worldSpawns = mutableMapOf<String, MutableMap<UUID, SpawnPoint>>()
    private val dirtyMain = DirtyKeys<UUID>()
    private val dirtyWorld = DirtyKeys<Pair<String, UUID>>()
    private val clearedWorlds = mutableSetOf<String>()

    init {
        migrateLegacyYaml("player_spawns.yml", "player_spawns") { yaml -> legacyRows(yaml) }
        load()
    }

    fun getMainSpawn(playerId: UUID): Location? = mainSpawns[playerId]?.toLocation()
    fun getWorldSpawn(worldName: String, playerId: UUID): Location? = worldSpawns[worldName]?.get(playerId)?.toLocation()

    fun setMainSpawn(playerId: UUID, location: Location) {
        mainSpawns[playerId] = location.toSpawnPoint()
        dirtyMain.touch(playerId)
        markDirty()
    }

    fun setWorldSpawn(worldName: String, playerId: UUID, location: Location) {
        worldSpawns.getOrPut(worldName) { mutableMapOf() }[playerId] = location.toSpawnPoint()
        dirtyWorld.touch(worldName to playerId)
        markDirty()
    }

    fun clearWorldSpawns(worldName: String) {
        if (worldSpawns.remove(worldName) != null) {
            clearedWorlds += worldName
            markDirty()
        }
    }

    private fun Location.toSpawnPoint() = SpawnPoint(world?.name ?: plugin.server.worlds.first().name, x, y, z, yaw, pitch)

    private fun SpawnPoint.toLocation(): Location? {
        val world = getWorld(worldName) ?: return null
        return Location(world, x, y, z, yaw, pitch)
    }

    override fun loadFrom(connection: Connection) {
        mainSpawns.clear()
        worldSpawns.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT player, world, x, y, z, yaw, pitch FROM main_spawns").use { rs ->
                while (rs.next()) {
                    try {
                        mainSpawns[UUID.fromString(rs.getString("player"))] = rs.toSpawnPoint()
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load main spawn for ${rs.getString("player")}: ${e.message}")
                    }
                }
            }
        }
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT world, player, x, y, z, yaw, pitch FROM world_spawns").use { rs ->
                while (rs.next()) {
                    try {
                        val worldName = rs.getString("world")
                        worldSpawns.getOrPut(worldName) { mutableMapOf() }[UUID.fromString(rs.getString("player"))] = rs.toSpawnPoint()
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load spawn for ${rs.getString("world")}/${rs.getString("player")}: ${e.message}")
                    }
                }
            }
        }
        plugin.logger.info("Loaded ${mainSpawns.size} main spawn(s) across ${worldSpawns.size} non-main world(s).")
    }

    private fun java.sql.ResultSet.toSpawnPoint() = SpawnPoint(
        worldName = getString("world"),
        x = getDouble("x"),
        y = getDouble("y"),
        z = getDouble("z"),
        yaw = getDouble("yaw").toFloat(),
        pitch = getDouble("pitch").toFloat(),
    )

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (worldName in clearedWorlds) {
            ops += SqlOp("DELETE FROM world_spawns WHERE world = ?", listOf(worldName))
        }
        for (playerId in dirtyMain.drain()) {
            val point = mainSpawns[playerId]
            if (point == null) {
                ops += SqlOp("DELETE FROM main_spawns WHERE player = ?", listOf(playerId))
            } else {
                ops += SqlOp(
                    "INSERT INTO main_spawns (player, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT(player) DO UPDATE SET world = excluded.world, x = excluded.x, y = excluded.y, " +
                        "z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch",
                    listOf(playerId, point.worldName, point.x, point.y, point.z, point.yaw, point.pitch),
                )
            }
        }
        val cleared = clearedWorlds.toSet()
        clearedWorlds.clear()
        for ((worldName, playerId) in dirtyWorld.drain()) {
            if (worldName in cleared) continue
            val point = worldSpawns[worldName]?.get(playerId)
            if (point == null) {
                ops += SqlOp("DELETE FROM world_spawns WHERE world = ? AND player = ?", listOf(worldName, playerId))
            } else {
                ops += SqlOp(
                    "INSERT INTO world_spawns (world, player, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT(world, player) DO UPDATE SET x = excluded.x, y = excluded.y, z = excluded.z, " +
                        "yaw = excluded.yaw, pitch = excluded.pitch",
                    listOf(worldName, playerId, point.x, point.y, point.z, point.yaw, point.pitch),
                )
            }
        }
    }

    /** Parses the pre-SQLite `player_spawns.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()

        yaml.getConfigurationSection("main")?.let { section ->
            for (key in section.getKeys(false)) {
                try {
                    val playerId = UUID.fromString(key)
                    val point = readSpawnPoint(section, key) ?: continue
                    ops += SqlOp(
                        "INSERT INTO main_spawns (player, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        listOf(playerId, point.worldName, point.x, point.y, point.z, point.yaw, point.pitch),
                    )
                } catch (e: Exception) {
                    plugin.logger.warning("Could not import main spawn for $key: ${e.message}")
                }
            }
        }

        yaml.getConfigurationSection("worlds")?.let { worldsSection ->
            for (worldName in worldsSection.getKeys(false)) {
                val playersSection = worldsSection.getConfigurationSection(worldName) ?: continue
                for (key in playersSection.getKeys(false)) {
                    try {
                        val playerId = UUID.fromString(key)
                        val point = readSpawnPoint(playersSection, key) ?: continue
                        ops += SqlOp(
                            "INSERT INTO world_spawns (world, player, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?)",
                            listOf(worldName, playerId, point.x, point.y, point.z, point.yaw, point.pitch),
                        )
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not import spawn for $worldName/$key: ${e.message}")
                    }
                }
            }
        }
        return ops
    }

    private fun readSpawnPoint(section: ConfigurationSection, key: String): SpawnPoint? {
        val pointSection = section.getConfigurationSection(key) ?: return null
        return SpawnPoint(
            worldName = pointSection.getString("world") ?: return null,
            x = pointSection.getDouble("x"),
            y = pointSection.getDouble("y"),
            z = pointSection.getDouble("z"),
            yaw = pointSection.getDouble("yaw").toFloat(),
            pitch = pointSection.getDouble("pitch").toFloat(),
        )
    }
}
