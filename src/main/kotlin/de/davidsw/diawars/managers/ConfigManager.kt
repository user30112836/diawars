package de.davidsw.diawars.managers

import de.davidsw.diawars.Diawars

class ConfigManager(private val plugin: Diawars) {
    enum class Type { BOOL, INT, DOUBLE, STRING }

    data class Option(
        val key: String,
        val type: Type,
        val default: Any,
        val description: String,
    )

    val options: List<Option> = listOf(
        Option("lobby.world", Type.STRING, "lobby", "Lobby-Weltname"),
        Option("lobby.spawn-point.x", Type.DOUBLE, 0.0, "Lobby-Spawn X"),
        Option("lobby.spawn-point.y", Type.DOUBLE, 0.0, "Lobby-Spawn Y"),
        Option("lobby.spawn-point.z", Type.DOUBLE, 0.0, "Lobby-Spawn Z"),
        Option("lobby.spawn-point.yaw", Type.DOUBLE, 0.0, "Lobby-Spawn Yaw"),
        Option("lobby.spawn-point.pitch", Type.DOUBLE, 0.0, "Lobby-Spawn Pitch"),
        Option("border.enabled", Type.BOOL, true, "Border anzeigen"),
        Option("border.render-distance", Type.INT, 32, "Border Render-Distanz"),
        Option("border.particle-type", Type.STRING, "REDSTONE", "Border Partikeltyp"),
        Option("border.color", Type.STRING, "YELLOW", "Border Farbe"),
        Option("border.amount", Type.INT, 1, "Border Partikelmenge"),
        Option("border.vertical-below", Type.INT, 10, "Border Tiefe nach unten"),
        Option("border.vertical-above", Type.INT, 24, "Border Hoehe nach oben"),
        Option("border.density.horizontal", Type.INT, 2, "Border horizontale Dichte"),
        Option("border.density.vertical", Type.INT, 3, "Border vertikale Dichte"),
        Option("pvp-toggle.default-enabled", Type.BOOL, true, "PvP standardmaessig an"),
        Option("pvp-toggle.delay-seconds", Type.INT, 300, "PvP-Umschalt-Verzoegerung (s)"),
        Option("pvp-toggle.lava-protection-radius", Type.DOUBLE, 5.0, "Lava-Schutzradius"),
        Option("dia-timer.yellow", Type.INT, 3000, "Diamant-Timer gelb (ticks)"),
        Option("dia-timer.red", Type.INT, 1200, "Diamant-Timer rot (ticks)"),
        Option("dia-timer.despawn-time", Type.INT, 12000, "Diamant-Despawn-Zeit (ticks)"),
        Option("diamond-limit", Type.INT, 32, "Diamant-Limit"),
        Option("diamonds-per-kill", Type.INT, 2, "Diamanten pro Kill"),
        Option("starter-diamonds", Type.INT, 32, "Start-Diamanten"),
        Option("night-vision-toggle", Type.BOOL, true, "Nachtsicht erlauben"),
        Option("event.accept-reward-diamonds", Type.INT, 5, "Event-Akzept-Belohnung Diamanten"),
        Option("playtime-reward.enabled", Type.BOOL, false, "Spielzeit-Belohnung aktiv"),
        Option("playtime-reward.interval-minutes", Type.INT, 30, "Belohnungs-Intervall (min)"),
        Option("playtime-reward.amount", Type.INT, 1, "Belohnungs-Anzahl"),
        Option("afk.threshold-seconds", Type.INT, 300, "AFK-Schwelle (s)"),
        Option("vault.claim-distance", Type.DOUBLE, 5.0, "Vault-Beanspruchungsdistanz"),
        Option("storage.flush-interval-seconds", Type.INT, 30, "Storage-Flush-Intervall (s)"),
    )

    private val byKey = options.associateBy { it.key }

    fun all(): List<Option> = options

    fun option(key: String): Option? = byKey[key]

    fun currentValue(key: String): Any {
        val opt = option(key)
        return plugin.config.get(key) ?: opt?.default ?: ""
    }

    fun displayValue(key: String): String = currentValue(key).toString()

    fun set(key: String, raw: String): String {
        val opt = option(key) ?: return "Unbekannte Einstellung: $key"
        val parsed = parse(opt.type, raw.trim()) ?: return "Ungueltiger Wert fuer $key (Typ ${opt.type})."
        plugin.config.set(key, parsed)
        plugin.saveConfig()
        plugin.reloadPluginConfigs()
        return "OK $key = $parsed"
    }

    private fun parse(type: Type, raw: String): Any? = when (type) {
        Type.BOOL -> when (raw.lowercase()) {
            "true", "on", "enable", "enabled", "1", "ja", "yes" -> true
            "false", "off", "disable", "disabled", "0", "nein", "no" -> false
            else -> null
        }
        Type.INT -> raw.toIntOrNull()
        Type.DOUBLE -> raw.toDoubleOrNull()
        Type.STRING -> raw
    }
}
