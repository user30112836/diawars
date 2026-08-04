package de.davidsw.diawars.util

import de.davidsw.diawars.Diawars
import java.io.File

object LogFiles {
    fun resolve(plugin: Diawars, fileName: String): File {
        return File(plugin.dataFolder, "logs/$fileName")
    }
}