package de.davidsw.diawars.util

import org.bukkit.Particle

object ParticleParser {
    fun parse(type: String): Particle? {
        return when (type.uppercase()) {
            "REDSTONE", "DUST" -> Particle.DUST
            "FLAME" -> Particle.FLAME
            "GLOW" -> Particle.GLOW
            "END_ROD" -> Particle.END_ROD
            "SOUL" -> Particle.SOUL
            "ENCHANT" -> Particle.ENCHANT
            "PORTAL" -> Particle.PORTAL
            "HEART" -> Particle.HEART
            "VILLAGER_HAPPY" -> Particle.HAPPY_VILLAGER
            else -> null
        }
    }
}
