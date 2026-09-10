package de.davidsw.diawars.util

import org.bukkit.potion.PotionEffectType

object PotionEffectParser {
    fun parse(name: String): PotionEffectType? = when (name.uppercase()) {
        "SPEED" -> PotionEffectType.SPEED
        "SLOWNESS" -> PotionEffectType.SLOWNESS
        "HASTE" -> PotionEffectType.HASTE
        "MINING_FATIGUE" -> PotionEffectType.MINING_FATIGUE
        "STRENGTH" -> PotionEffectType.STRENGTH
        "JUMP_BOOST" -> PotionEffectType.JUMP_BOOST
        "REGENERATION" -> PotionEffectType.REGENERATION
        "RESISTANCE" -> PotionEffectType.RESISTANCE
        "FIRE_RESISTANCE" -> PotionEffectType.FIRE_RESISTANCE
        "WATER_BREATHING" -> PotionEffectType.WATER_BREATHING
        "INVISIBILITY" -> PotionEffectType.INVISIBILITY
        "NIGHT_VISION" -> PotionEffectType.NIGHT_VISION
        "SATURATION" -> PotionEffectType.SATURATION
        "HEALTH_BOOST" -> PotionEffectType.HEALTH_BOOST
        "ABSORPTION" -> PotionEffectType.ABSORPTION
        "GLOWING" -> PotionEffectType.GLOWING
        "LEVITATION" -> PotionEffectType.LEVITATION
        "LUCK" -> PotionEffectType.LUCK
        "SLOW_FALLING" -> PotionEffectType.SLOW_FALLING
        "HUNGER" -> PotionEffectType.HUNGER
        "WEAKNESS" -> PotionEffectType.WEAKNESS
        "POISON" -> PotionEffectType.POISON
        "BLINDNESS" -> PotionEffectType.BLINDNESS
        "NAUSEA" -> PotionEffectType.NAUSEA
        "UNLUCK" -> PotionEffectType.UNLUCK
        else -> null
    }

    fun name(type: PotionEffectType): String = type.key.value().uppercase()
}