package de.davidsw.diawars.util

import de.davidsw.diawars.Diawars
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * One prepared statement queued for the database thread.
 *
 * Statements are built on the main thread (so ItemStack/NBT serialization stays
 * there, exactly like the YAML stores used to do) and only executed off-thread.
 * [params] entries may be null, UUID, Enum, Boolean, String, Number or byte[];
 * everything else is handed to [PreparedStatement.setObject].
 */
data class SqlOp(val sql: String, val params: List<Any?>) {
    constructor(sql: String, vararg params: Any?) : this(sql, params.toList())
}

internal fun PreparedStatement.bind(params: List<Any?>) {
    params.forEachIndexed { index, value ->
        val i = index + 1
        when (value) {
            null -> setObject(i, null)
            is UUID -> setString(i, value.toString())
            is Enum<*> -> setString(i, value.name)
            is Boolean -> setInt(i, if (value) 1 else 0)
            else -> setObject(i, value)
        }
    }
}

/**
 * The plugin's single SQLite connection (`plugins/Diawars/data/diawars.db`).
 *
 * SQLite is a single-writer database, so one connection guarded by one lock is
 * both the simplest and the fastest setup here — no pool is needed. Reads only
 * happen at startup (every store keeps its data in memory), writes happen on
 * the dedicated `diawars-db` thread (periodic flush) or synchronously on the
 * caller's thread ([execute], used by `flushNow`).
 *
 * The file is opened in WAL mode so a crash cannot corrupt the last committed
 * transaction and readers never block the writer.
 */
class SqliteDatabase(private val plugin: Diawars, private val file: File) {
    companion object {
        /**
         * Cap for [retryOps]. A broken database (read-only file, full disk) would
         * otherwise grow the queue with every flush; past this point the oldest
         * statements are dropped and the loss is logged loudly.
         */
        private const val MAX_RETRY_OPS = 20_000
    }

    private val lock = ReentrantLock()
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "diawars-db").apply { isDaemon = true }
        }

    private var connection: Connection? = null

    /**
     * Batches that failed to commit, replayed before the next write. The in-memory
     * caches are unaffected by a failed flush, so without this the affected rows
     * would only reach disk again on their next mutation.
     */
    private val retryOps = mutableListOf<SqlOp>()
    private var retryDropped = 0

    /**
     * Incremental schema migrations; entry index + 1 is the schema version it
     * produces. Each entry runs inside a single transaction together with the
     * `schema_version` bump, so a failed migration never leaves half a schema.
     */
    private val migrations: List<List<String>> = listOf(
        // v1: the three admin-facing record stores (bugs, reports, mod requests).
        listOf(
            """
            CREATE TABLE bugs (
                id          TEXT PRIMARY KEY,
                reporter    TEXT NOT NULL,
                description TEXT NOT NULL,
                reported_at INTEGER NOT NULL,
                resolved    INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE reports (
                id            TEXT PRIMARY KEY,
                reporter      TEXT NOT NULL,
                reported_at   INTEGER NOT NULL,
                source        TEXT NOT NULL,
                target_player TEXT,
                target_name   TEXT,
                target_owner  TEXT,
                target_file   TEXT,
                reason        TEXT,
                shot          INTEGER NOT NULL DEFAULT 0,
                resolved      INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE mod_requests (
                id             TEXT PRIMARY KEY,
                requester      TEXT NOT NULL,
                requester_name TEXT NOT NULL,
                display_name   TEXT NOT NULL,
                requested_at   INTEGER NOT NULL,
                resolved       INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE admin_reads (
                store     TEXT NOT NULL,
                admin     TEXT NOT NULL,
                last_read INTEGER NOT NULL,
                PRIMARY KEY (store, admin)
            )
            """.trimIndent(),
        ),
        // v2: per-player scores, states, spawns and preferences.
        listOf(
            """
            CREATE TABLE diamond_scores (
                player   TEXT PRIMARY KEY,
                diamonds INTEGER NOT NULL,
                name     TEXT NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE pvp_status (
                player             TEXT PRIMARY KEY,
                enabled            INTEGER NOT NULL,
                toggle_active      INTEGER NOT NULL,
                toggle_start       INTEGER NOT NULL,
                toggle_destination INTEGER NOT NULL,
                old_time           INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE night_vision (
                player TEXT PRIMARY KEY
            )
            """.trimIndent(),
            """
            CREATE TABLE onboarding (
                player             TEXT PRIMARY KEY,
                entered_main_world INTEGER NOT NULL DEFAULT 0,
                starter_diamonds   INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE main_spawns (
                player TEXT PRIMARY KEY,
                world  TEXT NOT NULL,
                x      REAL NOT NULL,
                y      REAL NOT NULL,
                z      REAL NOT NULL,
                yaw    REAL NOT NULL,
                pitch  REAL NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE world_spawns (
                world  TEXT NOT NULL,
                player TEXT NOT NULL,
                x      REAL NOT NULL,
                y      REAL NOT NULL,
                z      REAL NOT NULL,
                yaw    REAL NOT NULL,
                pitch  REAL NOT NULL,
                PRIMARY KEY (world, player)
            )
            """.trimIndent(),
            """
            CREATE TABLE player_states (
                store      TEXT NOT NULL,
                player     TEXT NOT NULL,
                inventory  BLOB,
                armor      BLOB,
                offhand    BLOB,
                enderchest BLOB,
                gamemode   TEXT NOT NULL,
                world      TEXT NOT NULL,
                x          REAL NOT NULL,
                y          REAL NOT NULL,
                z          REAL NOT NULL,
                yaw        REAL NOT NULL,
                pitch      REAL NOT NULL,
                health     REAL NOT NULL,
                food       INTEGER NOT NULL,
                saturation REAL NOT NULL,
                exp        REAL NOT NULL,
                level      INTEGER NOT NULL,
                PRIMARY KEY (store, player)
            )
            """.trimIndent(),
        ),
        // v3: preferences, queued messages, rewards, image limits/bans, vault data.
        listOf(
            """
            CREATE TABLE border_preferences (
                player            TEXT PRIMARY KEY,
                enabled           INTEGER NOT NULL,
                particle_type     TEXT NOT NULL,
                color             TEXT NOT NULL,
                render_distance   INTEGER NOT NULL,
                density_horizontal INTEGER NOT NULL,
                density_vertical  INTEGER NOT NULL,
                amount            INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE scoreboard_preferences (
                player          TEXT PRIMARY KEY,
                sidebar_enabled INTEGER NOT NULL,
                components      TEXT NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE pending_messages (
                player   TEXT PRIMARY KEY,
                messages TEXT NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE rewards (
                player TEXT PRIMARY KEY,
                amount INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE image_limits (
                player      TEXT PRIMARY KEY,
                limit_value INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE image_bans (
                player TEXT PRIMARY KEY
            )
            """.trimIndent(),
            """
            CREATE TABLE vault_diamonds (
                vault    TEXT PRIMARY KEY,
                diamonds INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE vault_claims (
                vault   TEXT PRIMARY KEY,
                owner   TEXT NOT NULL,
                invited TEXT NOT NULL
            )
            """.trimIndent(),
        ),
        // v4: reported client info (nested snapshot, kept as one JSON document).
        listOf(
            """
            CREATE TABLE client_info (
                player      TEXT PRIMARY KEY,
                data        TEXT NOT NULL,
                reported_at INTEGER NOT NULL
            )
            """.trimIndent(),
        ),
        // v5: events (incl. configs and saved player inventories) and image maps.
        listOf(
            """
            CREATE TABLE events (
                id         TEXT PRIMARY KEY,
                name       TEXT NOT NULL,
                creator    TEXT NOT NULL,
                state      TEXT NOT NULL,
                world      TEXT NOT NULL,
                start_time INTEGER NOT NULL DEFAULT 0,
                end_time   INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE used_event_ids (
                id TEXT PRIMARY KEY
            )
            """.trimIndent(),
            """
            CREATE TABLE event_configs (
                event             TEXT PRIMARY KEY,
                gamemode          TEXT NOT NULL,
                advance_time      INTEGER NOT NULL,
                fixed_time        INTEGER NOT NULL,
                gamerules         TEXT NOT NULL,
                effects           TEXT NOT NULL,
                starting_inventory BLOB,
                starting_armor    BLOB,
                starting_offhand  BLOB
            )
            """.trimIndent(),
            """
            CREATE TABLE event_inventories (
                event      TEXT NOT NULL,
                player     TEXT NOT NULL,
                inventory  BLOB,
                armor      BLOB,
                offhand    BLOB,
                enderchest BLOB,
                PRIMARY KEY (event, player)
            )
            """.trimIndent(),
            """
            CREATE TABLE image_maps (
                map_id  INTEGER PRIMARY KEY,
                owner   TEXT NOT NULL,
                file    TEXT NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        ),
    )

    fun open() {
        file.parentFile?.mkdirs()
        // Explicit load: the driver is shaded into the plugin jar and found through
        // its service file, but this makes a missing/blocked driver fail loudly here.
        Class.forName("org.sqlite.JDBC")

        val conn = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
        conn.createStatement().use { statement ->
            statement.execute("PRAGMA journal_mode=WAL")
            statement.execute("PRAGMA synchronous=NORMAL")
            statement.execute("PRAGMA foreign_keys=ON")
            statement.execute("PRAGMA busy_timeout=5000")
        }
        lock.withLock { connection = conn }
        migrate(conn)
        plugin.logger.info("Opened database ${file.name} (schema v${schemaVersion(conn)}).")
    }

    private fun migrate(conn: Connection) {
        conn.createStatement().use {
            it.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)")
        }
        if (conn.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM schema_version").use { rs -> rs.next() && rs.getInt(1) == 0 }
            }
        ) {
            conn.createStatement().use { it.execute("INSERT INTO schema_version (version) VALUES (0)") }
        }

        var current = schemaVersion(conn)
        for (index in current until migrations.size) {
            val target = index + 1
            conn.autoCommit = false
            try {
                conn.createStatement().use { statement ->
                    migrations[index].forEach { statement.execute(it) }
                    statement.execute("UPDATE schema_version SET version = $target")
                }
                conn.commit()
                plugin.logger.info("Applied database migration v$target.")
            } catch (e: Exception) {
                conn.rollback()
                throw IllegalStateException("Database migration v$target failed: ${e.message}", e)
            } finally {
                conn.autoCommit = true
            }
            current = target
        }
    }

    private fun schemaVersion(conn: Connection): Int =
        conn.createStatement().use { statement ->
            statement.executeQuery("SELECT version FROM schema_version").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }

    private fun connection(): Connection =
        connection ?: throw IllegalStateException("Database is not open")

    /** Runs [block] on the single connection (used for startup reads). */
    fun <T> read(block: (Connection) -> T): T = lock.withLock { block(connection()) }

    /**
     * Executes [ops] in one transaction on the calling thread. Used for
     * `flushNow` (custody transfers that must survive a crash) and shutdown.
     *
     * A failed batch is kept and replayed before the next write, so a transient
     * failure (locked file, full disk) does not silently lose already-drained
     * changes from the stores' dirty tracking.
     */
    fun execute(ops: List<SqlOp>) {
        if (ops.isEmpty()) return
        lock.withLock {
            // Queued writes may still arrive while the plugin is shutting down, after
            // the connection was closed; dropping them is correct then.
            val conn = connection ?: return@withLock
            val batch = if (retryOps.isEmpty()) ops else retryOps.toList() + ops
            retryOps.clear()
            retryDropped = 0
            val previousAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                batch.forEach { op -> conn.prepareStatement(op.sql).use { it.bind(op.params); it.executeUpdate() } }
                conn.commit()
            } catch (e: Exception) {
                try {
                    conn.rollback()
                } catch (_: Exception) {
                }
                plugin.logger.severe("Could not write to ${file.name}: ${e.message}")
                retryOps.addAll(batch)
                if (retryOps.size > MAX_RETRY_OPS) {
                    val dropped = retryOps.size - MAX_RETRY_OPS
                    retryOps.subList(0, dropped).clear()
                    retryDropped += dropped
                    plugin.logger.severe(
                        "Database write queue is full: dropped $dropped pending statement(s) " +
                            "($retryDropped in total).",
                    )
                }
            } finally {
                try {
                    conn.autoCommit = previousAutoCommit
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Queues [ops] for the database thread. Before the plugin is enabled (or
     * after the executor shut down) the batch runs inline instead of being lost.
     */
    fun submit(ops: List<SqlOp>) {
        if (ops.isEmpty()) return
        if (!plugin.isEnabled) {
            execute(ops)
            return
        }
        try {
            executor.execute { execute(ops) }
        } catch (_: RejectedExecutionException) {
            execute(ops)
        }
    }

    /** Drains queued writes, checkpoints the WAL and closes the connection. */
    fun close() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.logger.warning("Database writes did not finish within 10s, closing anyway.")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        lock.withLock {
            val conn = connection ?: return@withLock
            if (retryOps.isNotEmpty()) {
                plugin.logger.warning("Retrying ${retryOps.size} pending database statement(s) before shutdown.")
                val batch = retryOps.toList()
                retryOps.clear()
                try {
                    conn.autoCommit = false
                    batch.forEach { op -> conn.prepareStatement(op.sql).use { it.bind(op.params); it.executeUpdate() } }
                    conn.commit()
                } catch (e: Exception) {
                    try {
                        conn.rollback()
                    } catch (_: Exception) {
                    }
                    plugin.logger.severe("Final database write failed, ${batch.size} statement(s) lost: ${e.message}")
                } finally {
                    try {
                        conn.autoCommit = true
                    } catch (_: Exception) {
                    }
                }
            }
            try {
                conn.createStatement().use { it.execute("PRAGMA wal_checkpoint(TRUNCATE)") }
            } catch (_: Exception) {
            }
            try {
                conn.close()
            } catch (e: Exception) {
                plugin.logger.warning("Could not close the database: ${e.message}")
            }
            connection = null
        }
    }
}
