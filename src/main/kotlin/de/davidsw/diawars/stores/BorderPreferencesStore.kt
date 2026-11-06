package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.ColorParser
import de.davidsw.diawars.util.ParticleParser
import de.davidsw.diawars.util.SqlOp
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.util.UUID
import kotlin.collections.iterator

data class BorderDensity(
    val horizontal: Int = 2,
    val vertical: Int = 3,
) {
    companion object {
        const val MIN = 1
        const val MAX = 8

        /** Normalizes any externally supplied values (config, disk, callers). */
        fun clamped(horizontal: Int, vertical: Int) =
            BorderDensity(horizontal.coerceIn(MIN, MAX), vertical.coerceIn(MIN, MAX))
    }
}

data class BorderPreference(
    val enabled: Boolean = true,
    val particleType: String = "REDSTONE",
    val color: Color = Color.YELLOW,
    val renderDistance: Int = 32,
    val density: BorderDensity = BorderDensity(),
    val amount: Int = 1,
)

class BorderPreferencesStore(plugin: Diawars) : SqlStore(plugin, "border_preferences") {
    companion object {
        const val RENDER_DISTANCE_MIN = 8
        const val RENDER_DISTANCE_MAX = 64
        const val AMOUNT_MIN = 0
        const val AMOUNT_MAX = 10
    }

    private val preferences = mutableMapOf<UUID, BorderPreference>()
    private val dirty = DirtyKeys<UUID>()

    init {
        migrateLegacyYaml("border_preferences.yml", "border_preferences") { yaml -> legacyRows(yaml) }
        load()
    }

    fun getPreference(playerId: UUID): BorderPreference {
        return preferences[playerId] ?: getDefaultPreference()
    }

    fun setEnabled(playerId: UUID, enabled: Boolean) {
        update(playerId) { it.copy(enabled = enabled) }
    }

    fun setParticleType(playerId: UUID, particleType: String) {
        if (ParticleParser.parse(particleType) == null) {
            plugin.logger.warning("Ignoring invalid border particle type '$particleType' for $playerId")
            return
        }
        update(playerId) { it.copy(particleType = particleType) }
    }

    fun setColor(playerId: UUID, color: Color) {
        update(playerId) { it.copy(color = color) }
    }

    fun setRenderDistance(playerId: UUID, renderDistance: Int) {
        update(playerId) { it.copy(renderDistance = renderDistance.coerceIn(RENDER_DISTANCE_MIN, RENDER_DISTANCE_MAX)) }
    }

    fun setDensity(playerId: UUID, density: BorderDensity) {
        update(playerId) { it.copy(density = BorderDensity.clamped(density.horizontal, density.vertical)) }
    }

    fun setHorizontalDensity(playerId: UUID, horizontal: Int) {
        val clamped = horizontal.coerceIn(BorderDensity.MIN, BorderDensity.MAX)
        update(playerId) { it.copy(density = it.density.copy(horizontal = clamped)) }
    }

    fun setVerticalDensity(playerId: UUID, vertical: Int) {
        val clamped = vertical.coerceIn(BorderDensity.MIN, BorderDensity.MAX)
        update(playerId) { it.copy(density = it.density.copy(vertical = clamped)) }
    }

    fun setAmount(playerId: UUID, amount: Int) {
        update(playerId) { it.copy(amount = amount.coerceIn(AMOUNT_MIN, AMOUNT_MAX)) }
    }

    fun resetToDefault(playerId: UUID) {
        preferences.remove(playerId)
        dirty.touch(playerId)
        markDirty()
    }

    fun parseParticleType(type: String): Particle? = ParticleParser.parse(type)

    private fun update(playerId: UUID, transform: (BorderPreference) -> BorderPreference) {
        preferences[playerId] = transform(getPreference(playerId))
        dirty.touch(playerId)
        markDirty()
    }

    private fun getDefaultPreference(): BorderPreference {
        return BorderPreference(
            enabled = plugin.config.getBoolean("border.enabled", true),
            particleType = plugin.config.getString("border.particle-type", "REDSTONE") ?: "REDSTONE",
            color = ColorParser.parseOrDefault(plugin.config.getString("border.color", "YELLOW") ?: "YELLOW"),
            renderDistance = plugin.config.getInt("border.render-distance", 32)
                .coerceIn(RENDER_DISTANCE_MIN, RENDER_DISTANCE_MAX),
            density = BorderDensity.clamped(
                horizontal = plugin.config.getInt("border.density.horizontal", 2),
                vertical = plugin.config.getInt("border.density.vertical", 3)
            ),
            amount = plugin.config.getInt("border.amount", 1).coerceIn(AMOUNT_MIN, AMOUNT_MAX),
        )
    }

    override fun loadFrom(connection: Connection) {
        preferences.clear()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT player, enabled, particle_type, color, render_distance, " +
                    "density_horizontal, density_vertical, amount FROM border_preferences",
            ).use { rs ->
                while (rs.next()) {
                    try {
                        preferences[UUID.fromString(rs.getString("player"))] = BorderPreference(
                            enabled = rs.getInt("enabled") != 0,
                            particleType = rs.getString("particle_type")?.takeIf { ParticleParser.parse(it) != null }
                                ?: "REDSTONE",
                            color = ColorParser.parseOrDefault(rs.getString("color") ?: "YELLOW"),
                            renderDistance = rs.getInt("render_distance")
                                .coerceIn(RENDER_DISTANCE_MIN, RENDER_DISTANCE_MAX),
                            density = BorderDensity.clamped(
                                horizontal = rs.getInt("density_horizontal"),
                                vertical = rs.getInt("density_vertical"),
                            ),
                            amount = rs.getInt("amount").coerceIn(AMOUNT_MIN, AMOUNT_MAX),
                        )
                    } catch (e: Exception) {
                        plugin.logger.warning(
                            "An error occurred while loading the border-settings for ${rs.getString("player")}: ${e.message}",
                        )
                    }
                }
            }
        }
        plugin.logger.info("Loaded border preferences for ${preferences.size} player(s).")
    }

    override fun writeTo(ops: MutableList<SqlOp>) {
        for (playerId in dirty.drain()) {
            val pref = preferences[playerId]
            if (pref == null) {
                ops += SqlOp("DELETE FROM border_preferences WHERE player = ?", listOf(playerId))
                continue
            }
            ops += SqlOp(
                "INSERT INTO border_preferences (player, enabled, particle_type, color, render_distance, " +
                    "density_horizontal, density_vertical, amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(player) DO UPDATE SET enabled = excluded.enabled, " +
                    "particle_type = excluded.particle_type, color = excluded.color, " +
                    "render_distance = excluded.render_distance, " +
                    "density_horizontal = excluded.density_horizontal, " +
                    "density_vertical = excluded.density_vertical, amount = excluded.amount",
                listOf(
                    playerId, pref.enabled, pref.particleType,
                    "${pref.color.red},${pref.color.green},${pref.color.blue}",
                    pref.renderDistance, pref.density.horizontal, pref.density.vertical, pref.amount,
                ),
            )
        }
    }

    /** Parses the pre-SQLite `border_preferences.yml` into insert statements. */
    private fun legacyRows(yaml: YamlConfiguration): List<SqlOp> {
        val ops = mutableListOf<SqlOp>()
        for (key in yaml.getKeys(false)) {
            try {
                val playerId = UUID.fromString(key)
                val section = yaml.getConfigurationSection(key) ?: continue
                val pref = BorderPreference(
                    enabled = section.getBoolean("enabled", true),
                    particleType = section.getString("particle-type")?.takeIf { ParticleParser.parse(it) != null }
                        ?: "REDSTONE",
                    color = ColorParser.parseOrDefault(section.getString("color") ?: "YELLOW"),
                    renderDistance = section.getInt("render-distance", 32)
                        .coerceIn(RENDER_DISTANCE_MIN, RENDER_DISTANCE_MAX),
                    density = BorderDensity.clamped(
                        horizontal = section.getInt("density.horizontal", 2),
                        vertical = section.getInt("density.vertical", 3),
                    ),
                    amount = section.getInt("amount", 1).coerceIn(AMOUNT_MIN, AMOUNT_MAX),
                )
                ops += SqlOp(
                    "INSERT INTO border_preferences (player, enabled, particle_type, color, render_distance, " +
                        "density_horizontal, density_vertical, amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    listOf(
                        playerId, pref.enabled, pref.particleType,
                        "${pref.color.red},${pref.color.green},${pref.color.blue}",
                        pref.renderDistance, pref.density.horizontal, pref.density.vertical, pref.amount,
                    ),
                )
            } catch (e: Exception) {
                plugin.logger.warning("An error occurred while importing the border-settings for $key: ${e.message}")
            }
        }
        return ops
    }
}
