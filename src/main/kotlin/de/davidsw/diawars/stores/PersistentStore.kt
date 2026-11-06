package de.davidsw.diawars.stores

/**
 * Common flush contract for every persisted store, so [de.davidsw.diawars.Store]
 * can drive YAML- and SQLite-backed stores alike during the migration.
 */
interface PersistentStore {
    fun markDirty()

    /** Persists pending changes if any (called from the periodic flush task). */
    fun flushIfDirty(): Boolean

    /**
     * Persists synchronously: custody transfers that must survive a crash.
     * [isFinal] is set during plugin shutdown so stores can drop volatile state.
     */
    fun flushNow(isFinal: Boolean = false)
}
