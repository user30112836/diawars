package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.StoreFiles
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong

abstract class YamlStore(protected val plugin: Diawars, fileName: String) {
    protected val storeFile: File = StoreFiles.resolve(plugin, fileName)

    private val writeLock = Any()

    private val latestSnapshot = AtomicLong(0)

    @Volatile
    private var dirty = false

    protected var finalFlush: Boolean = false
        private set

    protected abstract fun readFrom(yaml: YamlConfiguration)

    protected abstract fun writeTo(yaml: YamlConfiguration)

    protected fun load() {
        try {
            storeFile.parentFile?.mkdirs()
            if (!storeFile.exists()) {
                storeFile.createNewFile()
            }
            readFrom(YamlConfiguration.loadConfiguration(storeFile))
        } catch (e: Exception) {
            plugin.logger.severe("Could not load ${storeFile.name}: ${e.message}")
        }
    }

    fun markDirty() {
        dirty = true
    }

    fun saveImmediately() {
        dirty = false
        write(serialize(isFinal = false), async = true)
    }

    fun isDirty(): Boolean = dirty

    fun flushIfDirty(): Boolean {
        if (!dirty) return false
        dirty = false
        write(serialize(false), async = true)
        return true
    }

    fun flushNow(isFinal: Boolean = false) {
        dirty = false
        write(serialize(isFinal), async = false)
    }

    private fun serialize(isFinal: Boolean): String {
        val yaml = YamlConfiguration()
        finalFlush = isFinal
        try {
            writeTo(yaml)
        } finally {
            finalFlush = false
        }
        return yaml.saveToString()
    }

    private fun write(data: String, async: Boolean) {
        val snapshot = latestSnapshot.incrementAndGet()

        if (!async || !plugin.isEnabled) {
            synchronized(writeLock) { writeToDisk(data, snapshot) }
            return
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            synchronized(writeLock) { writeToDisk(data, snapshot) }
        })
    }

    private fun writeToDisk(data: String, snapshot: Long) {
        if (snapshot < latestSnapshot.get()) return // superseded by a newer snapshot

        try {
            val directory = storeFile.parentFile
            directory?.mkdirs()

            val temp = File(directory, "${storeFile.name}.tmp")
            temp.writeText(data)

            try {
                Files.move(
                    temp.toPath(), storeFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), storeFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            plugin.logger.severe("Could not save ${storeFile.name}: ${e.message}")
        }
    }
}
