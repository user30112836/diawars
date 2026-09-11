package de.davidsw.diawars.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage

object MiniMessageHelper {
    fun mm(text: String): Component = miniMessage().deserialize(text)
    fun pmm(text: String): Component {
        val prefix = "<dark_gray>[<gold>Plugin</gold>] </dark_gray>"
        return miniMessage().deserialize(prefix + text)
    }

    /** Escapes user-controlled input so it renders as literal text instead of MiniMessage tags. */
    fun escape(text: String): String = miniMessage().escapeTags(text)

    /** Renders untrusted input as plain literal text (no MiniMessage tags are interpreted). */
    fun smm(text: String): Component = miniMessage().deserialize(escape(text))
}