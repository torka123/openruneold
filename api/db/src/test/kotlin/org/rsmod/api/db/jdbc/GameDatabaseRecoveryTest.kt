package org.rsmod.api.db.jdbc

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.inject.Provider
import java.lang.management.ManagementFactory
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.DriverPropertyInfo
import java.sql.SQLException
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.db.DatabaseConnection

/**
 * S04 native JDBC recovery mapping in docs/bosses/nex/NEX_OSRS_AUDIT.md. Successful recovery uses
 * actual GameDatabase startup, GameConnection and PostgreSQL in a separately owned logs cluster.
 * Native startup migrates before probe DDL; recovery preserves that history and the frozen startup
 * authority without reading either provider again or replaying the fixed SQL receipt callback.
 *
 * Temporary, uniquely addressed JDBC Drivers inject failures at the native connector boundary;
 * strict connection proxies observe cleanup and monitor ownership through the existing private
 * connection-installation fixture seam. No canonical configuration, account/NPC mutation,
 * database-service lifecycle or production test hook is used.
 */
class GameDatabaseRecoveryTest {
    @Test
    fun `native reconnect replaces the PostgreSQL backend and preserves committed data without replay`() {
        val logs = Files.createDirectories(Path.of("logs")).toAbsolutePath().normalize()
        val directory = Files.createTempDirectory(logs, "game-db-recovery-test-").normalize()
        check(directory.startsWith(logs))
        EmbeddedPostgres.builder()
            .setDataDirectory(directory.resolve("data"))
            .setOverrideWorkingDirectory(directory.resolve("binaries").toFile())
            .setCleanDataDirectory(false)
            .setPort(0)
            .start()
            .use { postgres ->
                val source = postgres.postgresDatabase
                val config =
                    source.connection.use {
                        DatabaseConfig(it.metaData.url, it.metaData.userName, "", false)
                    }
                var providersFail = false
                val databaseProviderReads = AtomicInteger()
                val connectorProviderReads = AtomicInteger()
                val connector =
                    GameConnection(
                        Provider {
                            connectorProviderReads.incrementAndGet()
                            check(!providersFail) { "Connector provider authority is unavailable" }
                            config
                        }
                    )
                val database =
                    GameDatabase(
                        Provider {
                            databaseProviderReads.incrementAndGet()
                            check(!providersFail) { "Database provider authority is unavailable" }
                            config
                        }
                    )
                database.connect(connector)
                var preparationCalls = 0
                try {
                    val history = database.withTransactionBlockingOnce(::migrationHistory)
                    assertTrue(history.isNotEmpty(), "Native startup must run migrations first")
                    assertEquals(1, databaseProviderReads.get())
                    assertEquals(
                        0,
                        connectorProviderReads.get(),
                        "Startup passes its resolved config",
                    )
                    // Failing providers cannot select or attempt to open any unintended database.
                    providersFail = true
                    database.withTransactionBlockingOnce { db ->
                        preparationCalls++
                        db.prepareStatement(
                                "CREATE TABLE recovery_probe (receipt_id UUID PRIMARY KEY, payload TEXT NOT NULL)"
                            )
                            .use { it.execute() }
                        db.prepareStatement(
                                "INSERT INTO recovery_probe(receipt_id, payload) VALUES (?, ?)"
                            )
                            .use {
                                it.setObject(1, RECEIPT_ID)
                                it.setString(2, "immutable committed payload")
                                assertEquals(1, it.executeUpdate())
                            }
                    }
                    var before = database.withTransactionBlockingOnce(::backend)
                    assertFalse(before.autoCommit)
                    repeat(2) {
                        database.reconnect()
                        val after = database.withTransactionBlocking(::backend)
                        assertNotEquals(
                            before.pid,
                            after.pid,
                            "A fresh physical backend is required",
                        )
                        assertEquals("immutable committed payload", after.payload)
                        assertFalse(after.autoCommit)
                        assertEquals(
                            history,
                            database.withTransactionBlockingOnce(::migrationHistory),
                        )
                        assertEquals(after, database.withTransactionBlockingOnce(::backend))
                        assertEquals(
                            1,
                            preparationCalls,
                            "Reconnect cannot replay accepted SQL work",
                        )
                        assertTrue(before.connection.isClosed)
                        assertEquals(
                            1,
                            databaseProviderReads.get(),
                            "Recovery retains startup authority",
                        )
                        assertEquals(
                            0,
                            connectorProviderReads.get(),
                            "Recovery bypasses provider fallback",
                        )
                        before = after
                    }
                } finally {
                    database.close()
                }
                source.connection.use { jdbc ->
                    jdbc
                        .prepareStatement("SELECT payload FROM recovery_probe WHERE receipt_id = ?")
                        .use {
                            it.setObject(1, RECEIPT_ID)
                            it.executeQuery().use { rows ->
                                assertTrue(rows.next())
                                assertEquals("immutable committed payload", rows.getString(1))
                                assertFalse(rows.next())
                            }
                        }
                }
            }
    }

    @Test
    fun `failed retirement or replacement disables every SQL entry until recovery succeeds`() {
        for (stage in listOf("retirement", "replacement")) {
            val failure = SQLException("injected $stage failure")
            val old = ConnectionProbe()
            if (stage == "retirement") old.closeFailure.set(failure)
            val replacementFailure =
                AtomicReference<Throwable?>(if (stage == "replacement") failure else null)
            val fresh = ConnectionProbe()
            NativeConnector {
                    assertTrue(old.closed.get(), "Old owner must close before replacement opens")
                    replacementFailure.get()?.let { throw it }
                    fresh.connection
                }
                .use { native ->
                    val database = newDatabase().apply { installConnection(old.connection, native) }
                    try {
                        assertSame(failure, assertFails { database.reconnect() })
                        assertEquals(if (stage == "retirement") 0 else 1, native.opens.get())
                        assertEquals(1, old.closeCalls.get())
                        assertUnavailable(database)
                        assertEquals(0, old.commits.get())
                        assertEquals(0, old.rollbacks.get())

                        old.closeFailure.set(null)
                        replacementFailure.set(null)
                        database.reconnect()
                        assertTrue(old.closed.get())
                        assertFalse(fresh.closed.get())
                        assertFalse(
                            fresh.autoCommit.get(),
                            "Native connector configures transactions",
                        )
                        assertEquals(if (stage == "retirement") 1 else 2, native.opens.get())
                        assertEquals(
                            "once restored",
                            database.withTransactionBlockingOnce { "once restored" },
                        )
                        assertEquals(
                            "default restored",
                            database.withTransactionBlocking { "default restored" },
                        )
                        assertEquals(2, fresh.commits.get())
                        assertEquals(0, old.commits.get())
                    } finally {
                        old.closeFailure.set(null)
                        database.close()
                    }
                    assertTrue(fresh.closed.get())
                }
        }
    }

    @Test
    fun `uninitialized nested and terminal lifecycle requests cannot replace an active owner`() {
        NativeConnector { error("Rejected lifecycle operation reached the native connector") }
            .use { native ->
                val database = newDatabase()
                assertFailsWith<IllegalStateException> { database.reconnect() }
                assertFailsWith<IllegalStateException> { database.close() }
                val old = ConnectionProbe()
                database.installConnection(old.connection)
                assertFailsWith<IllegalStateException> { database.reconnect() }
                assertEquals(
                    0,
                    old.closeCalls.get(),
                    "Missing startup authority cannot retire an owner",
                )
                assertEquals(
                    "owner intact",
                    database.withTransactionBlockingOnce { "owner intact" },
                )
                database.installConnection(old.connection, native)
                try {
                    for (entry in listOf("once", "default", "suspending")) {
                        val callback: (DatabaseConnection) -> String = {
                            assertFailsWith<IllegalStateException> { database.reconnect() }
                            assertFailsWith<IllegalStateException> { database.close() }
                            assertFalse(old.closed.get())
                            "outer transaction survived"
                        }
                        val result =
                            when (entry) {
                                "once" -> database.withTransactionBlockingOnce(callback)
                                "default" -> database.withTransactionBlocking(callback)
                                else -> runBlocking { database.withTransaction(callback) }
                            }
                        assertEquals("outer transaction survived", result)
                    }
                    assertEquals(4, old.commits.get())
                    assertEquals(0, old.closeCalls.get())
                    assertEquals(0, native.opens.get())
                } finally {
                    database.close()
                }
                assertUnavailable(database)
                assertFailsWith<IllegalStateException> { database.reconnect() }
                assertEquals(0, native.opens.get())
            }

        for (alreadyRecovering in listOf(false, true)) {
            val closeFailure = SQLException("retained owner did not close")
            val old = ConnectionProbe().apply { this.closeFailure.set(closeFailure) }
            NativeConnector { error("Terminal owner must not reconnect") }
                .use { native ->
                    val database = newDatabase().apply { installConnection(old.connection, native) }
                    if (alreadyRecovering)
                        assertSame(closeFailure, assertFails { database.reconnect() })
                    assertSame(closeFailure, assertFails { database.close() })
                    assertFalse(old.closed.get())
                    assertUnavailable(database)
                    assertFailsWith<IllegalStateException> { database.reconnect() }
                    assertEquals(0, native.opens.get())
                    old.closeFailure.set(null)
                    database.close()
                    assertTrue(old.closed.get(), "Terminal cleanup may retry the retained owner")
                    assertEquals(if (alreadyRecovering) 3 else 2, old.closeCalls.get())
                    assertUnavailable(database)
                }
        }
    }

    @Test
    fun `recovery shares the transaction and terminal close monitor in both directions`() {
        val scenarios =
            listOf(
                "once" to "recovery",
                "default" to "recovery",
                "suspending" to "recovery",
                "recovery" to "once",
                "recovery" to "default",
                "recovery" to "suspending",
                "recovery" to "recovery",
                "recovery" to "close",
                "close" to "recovery",
            )
        for ((ownerEntry, waiterEntry) in scenarios) {
            val ownerEntered = CountDownLatch(1)
            val releaseOwner = CountDownLatch(1)
            val waiterAttempting = CountDownLatch(1)
            val ownerThread = AtomicReference<Thread>()
            val waiterThread = AtomicReference<Thread>()
            val waiterCallbacks = AtomicInteger()
            val old = ConnectionProbe()
            fun holdOwner() {
                ownerEntered.countDown()
                check(releaseOwner.await(10, TimeUnit.SECONDS))
            }
            if (ownerEntry == "close") old.onClose = ::holdOwner
            val opens = AtomicInteger()
            NativeConnector {
                    if (ownerEntry == "recovery" && opens.incrementAndGet() == 1) holdOwner()
                    ConnectionProbe().connection
                }
                .use { native ->
                    val database = newDatabase().apply { installConnection(old.connection, native) }
                    val workers = Executors.newFixedThreadPool(2)
                    try {
                        val owner =
                            workers.submit(
                                Callable {
                                    ownerThread.set(Thread.currentThread())
                                    invokeEntry(database, ownerEntry, ::holdOwner)
                                }
                            )
                        assertTrue(ownerEntered.await(5, TimeUnit.SECONDS))
                        val waiter =
                            workers.submit(
                                Callable {
                                    waiterThread.set(Thread.currentThread())
                                    waiterAttempting.countDown()
                                    if (ownerEntry == "close") {
                                        assertFailsWith<IllegalStateException> {
                                            database.reconnect()
                                        }
                                    } else {
                                        invokeEntry(database, waiterEntry) {
                                            waiterCallbacks.incrementAndGet()
                                        }
                                    }
                                }
                            )
                        assertTrue(waiterAttempting.await(5, TimeUnit.SECONDS))
                        assertBlockedOnOwner(waiterThread.get(), ownerThread.get())
                        assertEquals(0, waiterCallbacks.get())
                        assertEquals(if (ownerEntry == "recovery") 1 else 0, native.opens.get())
                        releaseOwner.countDown()
                        owner.get(5, TimeUnit.SECONDS)
                        waiter.get(5, TimeUnit.SECONDS)
                        assertEquals(
                            if (waiterEntry in listOf("once", "default", "suspending")) 1 else 0,
                            waiterCallbacks.get(),
                        )
                    } finally {
                        releaseOwner.countDown()
                        workers.shutdown()
                        check(workers.awaitTermination(15, TimeUnit.SECONDS))
                        if (ownerEntry != "close" && waiterEntry != "close") database.close()
                    }
                }
        }
    }

    @Test
    fun `native connector closes a connection whose transaction setup fails and retains both causes`() {
        for (sqlFailure in listOf(false, true)) for (cleanupFails in listOf(false, true)) {
            val setupFailure =
                if (sqlFailure) SQLException("autoCommit setup failed")
                else AssertionError("autoCommit transport failed")
            val closeFailure = SQLException("setup cleanup failed")
            val raw =
                ConnectionProbe().apply {
                    autoCommitFailure = setupFailure
                    if (cleanupFails) this.closeFailure.set(closeFailure)
                }
            NativeConnector { raw.connection }
                .use { native ->
                    try {
                        assertSame(setupFailure, assertFails { native.connector.connect() })
                        assertEquals(1, native.opens.get())
                        assertEquals(1, raw.autoCommitCalls.get())
                        assertEquals(1, raw.closeCalls.get())
                        assertEquals(!cleanupFails, raw.closed.get())
                        assertEquals(
                            if (cleanupFails) listOf(closeFailure) else emptyList(),
                            setupFailure.suppressed.toList(),
                        )
                        assertEquals(0, raw.commits.get())
                        assertEquals(0, raw.rollbacks.get())
                    } finally {
                        raw.closeFailure.set(null)
                        if (!raw.closed.get()) raw.connection.close()
                    }
                }
        }
    }

    private fun backend(db: DatabaseConnection): Backend =
        db.prepareStatement(
                "SELECT pg_backend_pid(), payload FROM recovery_probe WHERE receipt_id = ?"
            )
            .use { statement ->
                statement.setObject(1, RECEIPT_ID)
                val autoCommit = statement.connection.autoCommit
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    Backend(rows.getInt(1), rows.getString(2), autoCommit, statement.connection)
                        .also { assertFalse(rows.next()) }
                }
            }

    private fun migrationHistory(db: DatabaseConnection): List<String> =
        db.prepareStatement(
                "SELECT row_to_json(history)::text FROM flyway_schema_history history ORDER BY installed_rank"
            )
            .use { statement ->
                statement.executeQuery().use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }

    private fun assertUnavailable(database: GameDatabase) {
        var callbacks = 0
        assertFailsWith<IllegalStateException> {
            database.withTransactionBlockingOnce { callbacks++ }
        }
        assertFailsWith<IllegalStateException> { database.withTransactionBlocking { callbacks++ } }
        assertFailsWith<IllegalStateException> {
            runBlocking { database.withTransaction { callbacks++ } }
        }
        assertEquals(0, callbacks, "Unavailable owners cannot execute any SQL callback")
    }

    private fun invokeEntry(database: GameDatabase, entry: String, callback: () -> Unit) {
        when (entry) {
            "once" -> database.withTransactionBlockingOnce { callback() }
            "default" -> database.withTransactionBlocking { callback() }
            "suspending" -> runBlocking { database.withTransaction { callback() } }
            "recovery" -> database.reconnect()
            "close" -> database.close()
            else -> error("Unknown database entry: $entry")
        }
    }

    private fun assertBlockedOnOwner(waiter: Thread, owner: Thread) {
        val threads = ManagementFactory.getThreadMXBean()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val info = threads.getThreadInfo(waiter.threadId())
            if (info?.threadState == Thread.State.BLOCKED && info.lockOwnerId == owner.threadId())
                return
            Thread.sleep(10)
        }
        fail("Database contender did not block on the lifecycle/transaction owner's monitor")
    }

    private fun newDatabase() =
        GameDatabase(
            Provider<DatabaseConfig> { error("Canonical/startup config must not be read") }
        )

    private fun GameDatabase.installConnection(
        connection: Connection,
        native: NativeConnector? = null,
    ) {
        GameDatabase::class
            .java
            .getDeclaredField("connection")
            .apply { isAccessible = true }
            .set(this, connection)
        if (native != null) {
            GameDatabase::class
                .java
                .getDeclaredField("connector")
                .apply { isAccessible = true }
                .set(this, native.connector)
            GameDatabase::class
                .java
                .getDeclaredField("connectionConfig")
                .apply { isAccessible = true }
                .set(this, native.config)
        }
    }

    private data class Backend(
        val pid: Int,
        val payload: String,
        val autoCommit: Boolean,
        val connection: Connection,
    )

    private class NativeConnector(private val open: () -> Connection) : AutoCloseable {
        private val url = "jdbc:openrune-recovery-test:${UUID.randomUUID()}"
        val opens = AtomicInteger()
        private val driver =
            object : Driver {
                override fun acceptsURL(candidate: String?): Boolean = candidate == url

                override fun connect(candidate: String?, info: Properties?): Connection? {
                    if (!acceptsURL(candidate)) return null
                    opens.incrementAndGet()
                    return open()
                }

                override fun getPropertyInfo(url: String?, info: Properties?) =
                    emptyArray<DriverPropertyInfo>()

                override fun getMajorVersion() = 1

                override fun getMinorVersion() = 0

                override fun jdbcCompliant() = false

                override fun getParentLogger(): Logger =
                    Logger.getLogger("GameDatabaseRecoveryTest")
            }
        val config = DatabaseConfig(url, "probe", "", false)
        val connector = GameConnection(Provider { config })

        init {
            DriverManager.registerDriver(driver)
        }

        override fun close() = DriverManager.deregisterDriver(driver)
    }

    private class ConnectionProbe {
        val closed = AtomicBoolean(false)
        val autoCommit = AtomicBoolean(true)
        val autoCommitCalls = AtomicInteger()
        val closeCalls = AtomicInteger()
        val commits = AtomicInteger()
        val rollbacks = AtomicInteger()
        val closeFailure = AtomicReference<Throwable?>()
        var autoCommitFailure: Throwable? = null
        var onClose: () -> Unit = {}
        val connection =
            Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "isClosed" -> closed.get()
                    "getAutoCommit" -> autoCommit.get()
                    "setAutoCommit" -> {
                        autoCommitCalls.incrementAndGet()
                        assertEquals(false, args?.single())
                        autoCommitFailure?.let { throw it }
                        autoCommit.set(false)
                        null
                    }
                    "close" -> {
                        closeCalls.incrementAndGet()
                        onClose()
                        closeFailure.get()?.let { throw it }
                        closed.set(true)
                        null
                    }
                    "commit" -> {
                        check(!closed.get())
                        commits.incrementAndGet()
                        null
                    }
                    "rollback" -> {
                        check(!closed.get())
                        rollbacks.incrementAndGet()
                        null
                    }
                    "toString" -> "RecoveryConnectionProbe"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> error("Unexpected JDBC probe call: ${method.name}")
                }
            } as Connection
    }

    private companion object {
        private val RECEIPT_ID = UUID(0L, 43L)
    }
}
