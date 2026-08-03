package de.davidsw.diawars.util

import de.davidsw.diawars.Diawars
import java.io.File

object ConfigFiles {
    fun resolve(plugin: Diawars, fileName: String): File {
        val file = File(plugin.dataFolder, fileName)
        if (!file.exists()) plugin.saveResource(fileName, false)
        return file
    }
}