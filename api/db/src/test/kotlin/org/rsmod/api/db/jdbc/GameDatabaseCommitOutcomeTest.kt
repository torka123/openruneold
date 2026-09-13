package org.rsmod.api.db.jdbc

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.inject.Provider
import java.lang.management.ManagementFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.util.DatabaseCommitUncertainException
import org.rsmod.api.db.util.DatabaseRollbackException

/**
 * S04 explicit native commit-outcome mapping in docs/bosses/nex/NEX_OSRS_AUDIT.md. Real JDBC
 * delegates distinguish a commit not sent from a successful commit whose acknowledgement is lost.
 * The SQL probe is an immutable UUID/payload receipt in a separately owned logs cluster, with no
 * canonical connector, account mutation, NPC or RNG. Connection injection follows the existing
 * GameDatabaseConcurrencyTest fixture seam; production receives no test hook.
 */
class GameDatabaseCommitOutcomeTest {
    @Test
    fun `one attempt preserves actual PostgreSQL outcomes and original failure causes`() {
        val logs = Files.createDirectories(Path.of("logs")).toAbsolutePath().normalize()
        val directory = Files.createTempDirectory(logs, "game-db-commit-outcome-test-").normalize()
        check(directory.startsWith(logs))
        val scenarios =
            listOf(
                Scenario("normal", stored = true),
                Scenario("callback SQL failure", callbackFailure = SQLException("callback SQL")),
                Scenario(
                    "callback runtime failure",
                    callbackFailure = IllegalStateException("callback runtime"),
                ),
                Scenario(
                    "callback rollback failure",
                    callbackFailure = SQLException("callback"),
                    rollbackFailure = SQLException("rollback"),
                ),
                Scenario("commit not sent", commitFailure = SQLException("commit not sent")),
                Scenario(
                    "committed acknowledgement lost",
                    commitFailure = SQLException("ack lost"),
                    commitBeforeFailure = true,
                    stored = true,
                ),
                Scenario(
                    "commit non-SQL throwable",
                    commitFailure = AssertionError("commit transport failure"),
                ),
                Scenario(
                    "unsent commit rollback failure",
                    commitFailure = SQLException("commit not sent"),
                    rollbackFailure = IllegalStateException("rollback failure"),
                ),
                Scenario(
                    "committed rollback failure",
                    commitFailure = SQLException("ack lost"),
                    commitBeforeFailure = true,
                    rollbackFailure = SQLException("rollback failure"),
                    stored = true,
                ),
            )
        EmbeddedPostgres.builder()
            .setDataDirectory(directory.resolve("data"))
            .setOverrideWorkingDirectory(directory.resolve("binaries").toFile())
            .setCleanDataDirectory(false)
            .setPort(0)
            .start()
            .use { postgres ->
                val source = postgres.postgresDatabase
                source.connection.use { jdbc ->
                    jdbc.createStatement().use {
                        it.execute(
                            "CREATE TABLE commit_outcome_probe (receipt_id UUID PRIMARY KEY, payload TEXT NOT NULL)"
                        )
                    }
                }
                for ((index, scenario) in scenarios.withIndex()) {
                    val receipt =
                        Receipt(UUID(0L, index.toLong() + 1), "prepared-${scenario.label}")
                    source.connection.use { raw ->
                        raw.autoCommit = false
                        val probe = JdbcProbe(raw, scenario)
                        val database = newDatabase().apply { installConnection(probe.connection) }
                        var callbacks = 0
                        val callback: (DatabaseConnection) -> Receipt = { db ->
                            callbacks++
                            db.prepareStatement(
                                    "INSERT INTO commit_outcome_probe(receipt_id, payload) VALUES (?, ?)"
                                )
                                .use {
                                    it.setObject(1, receipt.id)
                                    it.setString(2, receipt.payload)
                                    assertEquals(1, it.executeUpdate())
                                }
                            scenario.callbackFailure?.let { throw it }
                            receipt
                        }
                        try {
                            if (
                                scenario.callbackFailure == null && scenario.commitFailure == null
                            ) {
                                assertSame(receipt, database.withTransactionBlockingOnce(callback))
                            } else {
                                val thrown =
                                    assertFails(scenario.label) {
                                        database.withTransactionBlockingOnce(callback)
                                    }
                                when {
                                    scenario.callbackFailure != null &&
                                        scenario.rollbackFailure == null ->
                                        assertSame(scenario.callbackFailure, thrown)
                                    scenario.callbackFailure != null -> {
                                        val rollback = assertIs<DatabaseRollbackException>(thrown)
                                        assertSame(scenario.callbackFailure, rollback.cause)
                                        assertSame(scenario.rollbackFailure, rollback.rollbackCause)
                                    }
                                    else -> {
                                        val uncertain =
                                            assertIs<DatabaseCommitUncertainException>(thrown)
                                        assertSame(scenario.commitFailure, uncertain.cause)
                                        assertSame(
                                            scenario.rollbackFailure,
                                            uncertain.rollbackCause,
                                        )
                                        if (scenario.rollbackFailure == null)
                                            assertTrue(uncertain.suppressed.isEmpty())
                                        else
                                            assertEquals(
                                                listOf(scenario.rollbackFailure),
                                                uncertain.suppressed.toList(),
                                            )
                                    }
                                }
                            }
                            assertEquals(1, callbacks, "Never replay ${scenario.label}")
                            assertEquals(
                                if (scenario.callbackFailure == null) 1 else 0,
                                probe.commitCalls,
                            )
                            assertEquals(if (scenario.stored) 1 else 0, probe.delegatedCommits)
                            assertEquals(
                                if (
                                    scenario.callbackFailure != null ||
                                        scenario.commitFailure != null
                                )
                                    1
                                else 0,
                                probe.rollbackCalls,
                            )
                            assertEquals(
                                if (scenario.stored) receipt.payload else null,
                                readReceipt(source, receipt.id),
                                scenario.label,
                            )
                        } finally {
                            // Bypass only the injected fault for native cleanup of this test-owned
                            // connection. A rollback after an acknowledged/lost commit cannot erase
                            // it.
                            try {
                                raw.rollback()
                            } finally {
                                database.close()
                            }
                        }
                        assertTrue(raw.isClosed)
                    }
                    assertEquals(
                        if (scenario.stored) receipt.payload else null,
                        readReceipt(source, receipt.id),
                    )
                }
            }
    }

    @Test
    fun `invalid connections never enter the callback or transaction cleanup`() {
        var callbacks = 0
        assertFailsWith<IllegalStateException> {
            newDatabase().withTransactionBlockingOnce { callbacks++ }
        }
        val fake = FakeConnection().apply { closed.set(true) }
        val closed = newDatabase().apply { installConnection(fake.connection) }
        assertFailsWith<IllegalStateException> {
            closed.withTransactionBlockingOnce { callbacks++ }
        }
        assertEquals(0, callbacks)
        assertEquals(0, fake.commits.get())
        assertEquals(0, fake.rollbacks.get())
    }

    @Test
    fun `once transactions share the existing blocking suspending and close monitor`() {
        for (entry in listOf("blocking", "suspending", "close")) {
            val fake = FakeConnection()
            val database = newDatabase().apply { installConnection(fake.connection) }
            val ownerEntered = CountDownLatch(1)
            val releaseOwner = CountDownLatch(1)
            val waiterAttempting = CountDownLatch(1)
            val ownerThread = AtomicReference<Thread>()
            val waiterThread = AtomicReference<Thread>()
            val secondCallbacks = AtomicInteger()
            val executor = Executors.newFixedThreadPool(2)
            try {
                val owner =
                    executor.submit(
                        Callable {
                            database.withTransactionBlockingOnce {
                                ownerThread.set(Thread.currentThread())
                                ownerEntered.countDown()
                                check(releaseOwner.await(10, TimeUnit.SECONDS))
                                "once completed"
                            }
                        }
                    )
                assertTrue(ownerEntered.await(5, TimeUnit.SECONDS))
                val waiter =
                    executor.submit(
                        Callable {
                            waiterThread.set(Thread.currentThread())
                            waiterAttempting.countDown()
                            when (entry) {
                                "blocking" ->
                                    database.withTransactionBlocking {
                                        secondCallbacks.incrementAndGet()
                                    }
                                "suspending" ->
                                    runBlocking {
                                        database.withTransaction {
                                            secondCallbacks.incrementAndGet()
                                        }
                                    }
                                "close" -> database.close()
                                else -> error("Unexpected transaction entry $entry")
                            }
                        }
                    )
                assertTrue(waiterAttempting.await(5, TimeUnit.SECONDS))
                assertBlockedOnOwner(waiterThread.get(), ownerThread.get())
                assertEquals(0, secondCallbacks.get())
                assertEquals(0, fake.commits.get())
                assertFalse(fake.closed.get())
                releaseOwner.countDown()
                assertEquals("once completed", owner.get(5, TimeUnit.SECONDS))
                waiter.get(5, TimeUnit.SECONDS)
                assertEquals(if (entry == "close") 1 else 2, fake.commits.get())
                assertEquals(if (entry == "close") 0 else 1, secondCallbacks.get())
                assertEquals(entry == "close", fake.closed.get())
            } finally {
                releaseOwner.countDown()
                executor.shutdown()
                check(executor.awaitTermination(15, TimeUnit.SECONDS)) {
                    "Database monitor test workers did not finish"
                }
                if (!fake.closed.get()) database.close()
            }
        }
    }

    @Test
    fun `the existing blocking and suspending entrypoints retain their SQL callback retries`() {
        for (suspending in listOf(false, true)) {
            val fake = FakeConnection()
            val database = newDatabase().apply { installConnection(fake.connection) }
            var callbacks = 0
            val callback: (DatabaseConnection) -> String = {
                callbacks++
                if (callbacks < 3) throw SQLException("Existing retry attempt $callbacks")
                "existing success"
            }
            try {
                val result =
                    if (suspending) runBlocking { database.withTransaction(callback) }
                    else database.withTransactionBlocking(callback)
                assertEquals("existing success", result)
                assertEquals(3, callbacks)
                assertEquals(1, fake.commits.get())
                assertEquals(2, fake.rollbacks.get())
            } finally {
                database.close()
            }
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
        fail("Transaction/close contender did not block on the active once transaction's monitor")
    }

    private fun readReceipt(source: DataSource, id: UUID): String? =
        source.connection.use { jdbc ->
            jdbc
                .prepareStatement("SELECT payload FROM commit_outcome_probe WHERE receipt_id = ?")
                .use {
                    it.setObject(1, id)
                    it.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
                }
        }

    private fun newDatabase() =
        GameDatabase(Provider<DatabaseConfig> { error("Canonical config must not be requested") })

    private fun GameDatabase.installConnection(connection: Connection) {
        GameDatabase::class
            .java
            .getDeclaredField("connection")
            .apply { isAccessible = true }
            .set(this, connection)
    }

    private data class Receipt(val id: UUID, val payload: String)

    private data class Scenario(
        val label: String,
        val callbackFailure: Throwable? = null,
        val commitFailure: Throwable? = null,
        val commitBeforeFailure: Boolean = false,
        val rollbackFailure: Throwable? = null,
        val stored: Boolean = false,
    )

    private class JdbcProbe(private val delegate: Connection, private val scenario: Scenario) {
        var commitCalls = 0
        var delegatedCommits = 0
        var rollbackCalls = 0
        val connection =
            Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "commit" -> {
                        commitCalls++
                        if (scenario.commitFailure == null || scenario.commitBeforeFailure) {
                            delegate.commit()
                            delegatedCommits++
                        }
                        scenario.commitFailure?.let { throw it }
                        null
                    }
                    "rollback" -> {
                        rollbackCalls++
                        scenario.rollbackFailure?.let { throw it }
                        delegate.rollback()
                        null
                    }
                    else ->
                        try {
                            method.invoke(delegate, *(args ?: emptyArray()))
                        } catch (failure: InvocationTargetException) {
                            throw failure.targetException
                        }
                }
            } as Connection
    }

    private class FakeConnection {
        val closed = AtomicBoolean(false)
        val commits = AtomicInteger()
        val rollbacks = AtomicInteger()
        val connection =
            Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "isClosed" -> closed.get()
                    "commit" -> {
                        commits.incrementAndGet()
                        null
                    }
                    "rollback" -> {
                        rollbacks.incrementAndGet()
                        null
                    }
                    "close" -> {
                        closed.set(true)
                        null
                    }
                    "toString" -> "CommitOutcomeFakeConnection"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> error("Unexpected connection call ${method.name}")
                }
            } as Connection
    }
}
