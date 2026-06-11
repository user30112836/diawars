package de.davidsw.diawars.util

import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage

object MiniMessageHelper {
    fun mm(text: String) = miniMessage().deserialize(text)
}