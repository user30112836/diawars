package de.davidsw.diawars.util

import com.google.gson.Gson
import com.google.gson.JsonParseException

/**
 * JSON columns for values that are read as a whole anyway (lists, nested
 * snapshots). The stores keep everything in memory, so these are never queried
 * from SQL — a normalized child table would only add joins and code.
 */
object JsonColumn {
    val gson = Gson()

    fun encode(value: Any?): String = gson.toJson(value)

    /** Decodes a JSON string array; malformed or missing values become an empty list. */
    fun strings(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson(raw, Array<String>::class.java)?.toList().orEmpty()
        } catch (_: JsonParseException) {
            emptyList()
        }
    }
}
