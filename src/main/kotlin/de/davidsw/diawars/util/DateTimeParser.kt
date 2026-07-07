package de.davidsw.diawars.util

import java.time.Instant.ofEpochSecond
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object DateTimeParser {
    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy-HH:mm")
    const val FORMAT_HINT = "TT.MM.JJJJ-HH.mm"

    fun parseToEpochSeconds(input: String): Long? {
        return try {
            val parsed = LocalDateTime.parse(input, FORMATTER)
            parsed.atZone(ZoneId.systemDefault()).toEpochSecond()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun parseToString(epochSeconds: Long): String? {
        val instant = ofEpochSecond(epochSeconds)
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}