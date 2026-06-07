package de.davidsw.diawars.util

import org.bukkit.Color

object ColorParser {
    fun parse(colorStr: String): Color? {
        return when (colorStr.uppercase().replace(" ", "").replace(",", "")) {
            "RED" -> Color.RED
            "BLUE" -> Color.BLUE
            "GREEN" -> Color.GREEN
            "YELLOW" -> Color.YELLOW
            "ORANGE" -> Color.ORANGE
            "PURPLE" -> Color.PURPLE
            "WHITE" -> Color.WHITE
            "AQUA" -> Color.AQUA
            else -> parseRgb(colorStr)
        }
    }

    fun parseOrDefault(colorStr: String): Color = parse(colorStr) ?: Color.YELLOW

    private fun parseRgb(colorStr: String): Color? {
        val parts = colorStr.trim().split(",")
        if (parts.size != 3) return null
        val r = parts[0].trim().toIntOrNull() ?: return null
        val g = parts[1].trim().toIntOrNull() ?: return null
        val b = parts[2].trim().toIntOrNull() ?: return null
        if (r !in 0..255 || g !in 0..255 || b !in 0..255) return null
        return Color.fromRGB(r, g, b)
    }
}
