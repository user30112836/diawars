package de.davidsw.diawars.stores

import de.davidsw.diawars.Diawars
import de.davidsw.diawars.util.SqlOp
import de.davidsw.diawars.util.SqliteDatabase
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SQLite-backed counterpart of [YamlStore]. Stores keep their whole state in
 * memory exactly like before; only the persistence sink changes.
 *
 * Same threading contract as [YamlStore]: the statements (including any
 * ItemStack serialization) are built on the main thread in [writeTo], while
 * executing them happens on the `diawars-db` thread ([flushIfDirty]) or inline
 * on the caller's thread ([flushNow], custody transfers).
 */
abstract class SqlStore(protected val plugin: Diawars, protected val storeName: String) : PersistentStore {
    protected val db: SqliteDatabase get() = plugin.database

    private val dirty = AtomicBoolean(false)

    /** True while [flushNow] runs with `isFinal = true` (shutdown). */
    protected var finalFlush: Boolean = false
        private set

    /** Loads all rows into the in-memory cache. Runs once at startup on the main thread. */
    protected abstract fun loadFrom(connection: Connection)

    /** Appends the statements that persist the current cache state; leave it empty when nothing changed. */
    protected abstract fun writeTo(ops: MutableList<SqlOp>)

    protected fun load() {
        try {
            db.read { connection -> loadFrom(connection) }
        } catch (e: Exception) {
            plugin.logger.severe("Could not load $storeName from the database: ${e.message}")
        }
    }

    override fun markDirty() {
        dirty.set(true)
    }

    override fun flushIfDirty(): Boolean {
        if (!dirty.getAndSet(false)) return false
        write(isFinal = false)
        return true
    }

    override fun flushNow(isFinal: Boolean) {
        dirty.set(false)
        write(isFinal)
    }

    private fun write(isFinal: Boolean) {
        val ops = mutableListOf<SqlOp>()
        finalFlush = isFinal
        try {
            writeTo(ops)
        } finally {
            finalFlush = false
        }
        if (ops.isEmpty()) return
        if (isFinal || !plugin.isEnabled) db.execute(ops) else db.submit(ops)
    }

    /** Deletes and re-inserts every row of [storeName]'s admin read marks ("markRead" state). */
    protected fun writeAdminReads(ops: MutableList<SqlOp>, reads: Map<UUID, Long>) {
        ops += SqlOp("DELETE FROM admin_reads WHERE store = ?", storeName)
        for ((admin, lastRead) in reads) {
            ops += SqlOp(
                "INSERT INTO admin_reads (store, admin, last_read) VALUES (?, ?, ?)",
                storeName, admin, lastRead,
            )
        }
    }

    protected fun loadAdminReads(connection: Connection, into: MutableMap<UUID, Long>) {
        connection.prepareStatement("SELECT admin, last_read FROM admin_reads WHERE store = ?").use { statement ->
            statement.setString(1, storeName)
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    try {
                        into[UUID.fromString(rs.getString("admin"))] = rs.getLong("last_read")
                    } catch (e: Exception) {
                        plugin.logger.warning("Could not load admin read state for ${storeName}: ${e.message}")
                    }
                }
            }
        }
    }

    /** Highest numeric id already present, so sequential ids never restart at 1. */
    protected fun highestId(connection: Connection, table: String): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT MAX(CAST(id AS INTEGER)) FROM $table").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }
}
