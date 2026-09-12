package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.ColorParser
import de.davidsw.diawars.util.ParticleParser
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.configuration.file.YamlConfiguration
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

class BorderPreferencesStore(plugin: Diawars) : YamlStore(plugin, "border_preferences.yml") {
    companion object {
        const val RENDER_DISTANCE_MIN = 8
        const val RENDER_DISTANCE_MAX = 64
        const val AMOUNT_MIN = 0
        const val AMOUNT_MAX = 10
    }

    private val preferences = mutableMapOf<UUID, BorderPreference>()

    init {
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
        markDirty()
    }

    fun parseParticleType(type: String): Particle? = ParticleParser.parse(type)

    private fun update(playerId: UUID, transform: (BorderPreference) -> BorderPreference) {
        preferences[playerId] = transform(getPreference(playerId))
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

    override fun readFrom(yaml: YamlConfiguration) {
        for (key in yaml.getKeys(false)) {
            try {
                val uuid = UUID.fromString(key)
                val section = yaml.getConfigurationSection(key) ?: continue

                preferences[uuid] = BorderPreference(
                    enabled = section.getBoolean("enabled", true),
                    particleType = section.getString("particle-type")?.takeIf { ParticleParser.parse(it) != null } ?: "REDSTONE",
                    color = ColorParser.parseOrDefault(section.getString("color") ?: "YELLOW"),
                    renderDistance = section.getInt("render-distance", 32)
                        .coerceIn(RENDER_DISTANCE_MIN, RENDER_DISTANCE_MAX),
                    density = BorderDensity.clamped(
                        horizontal = section.getInt("density.horizontal", 2),
                        vertical   = section.getInt("density.vertical", 3),
                    ),
                    amount = section.getInt("amount", 1).coerceIn(AMOUNT_MIN, AMOUNT_MAX),
                )
            } catch (e: Exception) {
                plugin.logger.warning("An error occurred while loading the border-settings for $key: ${e.message}")
            }
        }
    }

    override fun writeTo(yaml: YamlConfiguration) {
        for ((uuid, pref) in preferences) {
            val key = uuid.toString()
            yaml.set("$key.enabled", pref.enabled)
            yaml.set("$key.particle-type", pref.particleType)
            yaml.set("$key.color", "${pref.color.red},${pref.color.green},${pref.color.blue}")
            yaml.set("$key.render-distance", pref.renderDistance)
            yaml.set("$key.density.horizontal", pref.density.horizontal)
            yaml.set("$key.density.vertical", pref.density.vertical)
            yaml.set("$key.amount", pref.amount)
        }
    }
}
