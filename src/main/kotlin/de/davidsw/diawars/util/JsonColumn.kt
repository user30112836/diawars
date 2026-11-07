package de.davidsw.diawars.util

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken

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

    /** Decodes a JSON object of booleans (e.g. event game rules). */
    fun booleanMap(raw: String?): Map<String, Boolean> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            gson.fromJson<Map<String, Boolean>>(raw, BOOLEAN_MAP) ?: emptyMap()
        } catch (_: JsonParseException) {
            emptyMap()
        }
    }

    private val BOOLEAN_MAP = object : TypeToken<Map<String, Boolean>>() {}.type
}
