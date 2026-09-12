package de.davidsw.diawars.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage

object MiniMessageHelper {
    private val shared = miniMessage()

    fun mm(text: String): Component = shared.deserialize(text)
    fun pmm(text: String): Component {
        val prefix = "<dark_gray>[<gold>Plugin</gold>] </dark_gray>"
        return shared.deserialize(prefix + text)
    }

    fun escape(text: String): String = shared.escapeTags(text)
}