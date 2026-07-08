package de.davidsw.diawars.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage

object MiniMessageHelper {
    fun mm(text: String) = miniMessage().deserialize(text)
    fun pmm(text: String): Component {
        val prefix = "<dark_gray>[<gold>Plugin</gold>] </dark_gray>"
        return miniMessage().deserialize(prefix + text)
    }
}