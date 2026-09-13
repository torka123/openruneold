package org.rsmod.api.net.central.logging

import dev.openrune.types.util.UncheckedType
import dev.or2.central.logs.CentralActivityLog
import dev.or2.central.logs.CentralActivityLogRepository
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

@OptIn(UncheckedType::class)
class CentralActivityLogWriterShutdownTest {
    @Test
    fun `concurrent stops commit native log snapshots in order before closing datasource once`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fixture = Fixture { record ->
            if (record.type == "login") {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
        }
        val failures = CopyOnWriteArrayList<Throwable>()
        val completed = CountDownLatch(2)
        val callers =
            List(2) {
                Thread {
                    try {
                        fixture.writer.stop()
                    } catch (failure: Throwable) {
                        failures.add(failure)
                    } finally {
                        completed.countDown()
                    }
                }
            }

        try {
            val player = player()
            fixture.writer.logPlayerLogin(player)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val arguments = mutableListOf("original")
            fixture.writer.logCommand(player, "test", arguments)
            fixture.writer.logPlayerLogout(player, 123L)
            fixture.writer.logItemDestroy(player, "item.abyssal_whip", InvObj(4151, 3))
            arguments[0] = "changed"
            player.characterId = 99
            player.accountId = 990
            callers.forEach(Thread::start)
            assertTrue(fixture.executor.awaitEntered.await(5, TimeUnit.SECONDS))
            assertEquals(2L, completed.count)
            assertEquals(0, fixture.closed.get())
            release.countDown()
            assertTrue(completed.await(10, TimeUnit.SECONDS))
            assertTrue(failures.isEmpty(), "Unexpected stop failures: $failures")
            fixture.writer.stop()
            assertEquals(
                listOf("login", "command", "logout", "destroy_item"),
                fixture.jdbc.committed.map { it.type },
            )
            assertTrue(
                fixture.jdbc.committed.all {
                    it.characterId == 7 && it.accountId == 70L && it.worldId == 1
                }
            )
            assertTrue(fixture.jdbc.committed[1].payload.contains("original"))
            assertFalse(fixture.jdbc.committed[1].payload.contains("changed"))
            assertTrue(fixture.jdbc.committed[2].payload.contains("123"))
            assertEquals(1, fixture.jdbc.itemLines.get())
            assertEquals(1, fixture.closed.get())
            assertEquals(
                listOf("login", "command", "logout", "destroy_item", "datasource"),
                fixture.events.toList(),
            )
            assertEquals(0, fixture.executor.forcedShutdowns)
        } finally {
            release.countDown()
            callers.forEach { it.join(10_000) }
            fixture.finishExecutor()
        }
    }

    @Test
    fun `failed accepted record and cause remain owned without retrying after later commits`() {
        val databaseFailure = SQLException("activity insert failed")
        val fixture = Fixture { record -> if (record.type == "login") throw databaseFailure }

        try {
            fixture.writer.logPlayerLogin(player())
            fixture.writer.logPlayerLogout(player(), 123L)
            val failure = assertFails { fixture.writer.stop() }
            assertSame(failure, assertFails { fixture.writer.stop() })
            assertEquals(listOf(databaseFailure), failure.suppressed.toList())
            assertEquals(listOf("login", "logout"), fixture.jdbc.attempted.map { it.type })
            assertEquals(listOf("logout"), fixture.jdbc.committed.map { it.type })
            assertRetainedRecord(fixture, databaseFailure)
            assertEquals(0, fixture.closed.get())
            assertSame(
                fixture.repository,
                field<CentralActivityLogRepository>(fixture.writer, "repository"),
            )
            assertSame(fixture.resourceOwner, field<AutoCloseable>(fixture.writer, "dataSource"))
        } finally {
            fixture.finishExecutor()
        }
    }

    @Test
    fun `timeout retains queue and datasource including a deferred accepted database failure`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val databaseFailure = SQLException("late activity failure")
        val fixture =
            Fixture(timeoutMillis = 1) { record ->
                if (record.type == "login") {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    throw databaseFailure
                }
            }

        try {
            fixture.writer.logPlayerLogin(player())
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            fixture.writer.logPlayerLogout(player(), 123L)
            val failure = assertFails { fixture.writer.stop() }
            assertEquals(1, fixture.executor.queue.size)
            assertFalse(fixture.executor.isTerminated)
            assertEquals(0, fixture.executor.forcedShutdowns)
            assertEquals(0, fixture.closed.get())
            release.countDown()
            assertTrue(fixture.executor.awaitTermination(5, TimeUnit.SECONDS))
            assertSame(failure, assertFails { fixture.writer.stop() })
            assertEquals(listOf("login", "logout"), fixture.jdbc.attempted.map { it.type })
            assertEquals(listOf("logout"), fixture.jdbc.committed.map { it.type })
            assertRetainedRecord(fixture, databaseFailure)
            assertEquals(0, fixture.closed.get())
            assertSame(
                fixture.repository,
                field<CentralActivityLogRepository>(fixture.writer, "repository"),
            )
        } finally {
            release.countDown()
            fixture.finishExecutor()
        }
    }

    @Test
    fun `interrupted stop restores caller interrupt without cancelling accepted writes or closing datasource`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fixture = Fixture { record ->
            if (record.type == "login") {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
        }
        val failure = AtomicReference<Throwable?>()
        val interrupted = AtomicBoolean()
        val stopper = Thread {
            Thread.currentThread().interrupt()
            try {
                fixture.writer.stop()
            } catch (cause: Throwable) {
                failure.set(cause)
            } finally {
                interrupted.set(Thread.currentThread().isInterrupted)
            }
        }

        try {
            fixture.writer.logPlayerLogin(player())
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            fixture.writer.logPlayerLogout(player())
            stopper.start()
            stopper.join(5_000)
            assertFalse(stopper.isAlive)
            assertIs<InterruptedException>(failure.get())
            assertTrue(interrupted.get())
            assertEquals(1, fixture.executor.queue.size)
            assertEquals(0, fixture.executor.forcedShutdowns)
            assertEquals(0, fixture.closed.get())
            release.countDown()
            assertTrue(fixture.executor.awaitTermination(5, TimeUnit.SECONDS))
            assertSame(failure.get(), assertFails { fixture.writer.stop() })
            assertEquals(listOf("login", "logout"), fixture.jdbc.committed.map { it.type })
            assertEquals(0, fixture.closed.get())
        } finally {
            release.countDown()
            stopper.join(10_000)
            fixture.finishExecutor()
        }
    }

    @Test
    fun `native log methods reject after successful stop instead of silently treating writer as disabled`() {
        val fixture = Fixture()
        try {
            fixture.writer.stop()
            assertFailsWith<RejectedExecutionException> { fixture.writer.logPlayerLogin(player()) }
            assertFailsWith<RejectedExecutionException> { fixture.writer.logPlayerLogout(player()) }
            assertFailsWith<RejectedExecutionException> {
                fixture.writer.logCommand(player(), "test", emptyList())
            }
            assertFailsWith<RejectedExecutionException> {
                fixture.writer.logItemDestroy(player(), "item.abyssal_whip", InvObj(4151, 1))
            }
            assertFails { fixture.writer.start() }
            assertTrue(fixture.jdbc.attempted.isEmpty())
            assertEquals(1, fixture.closed.get())
        } finally {
            fixture.finishExecutor()
        }
    }

    @Test
    fun `datasource close failure retains original ownership and same terminal failure`() {
        val closeFailure = SQLException("datasource close failed")
        val fixture = Fixture(closeFailure = closeFailure)
        try {
            fixture.writer.logPlayerLogin(player())
            assertSame(closeFailure, assertFails { fixture.writer.stop() })
            assertSame(closeFailure, assertFails { fixture.writer.stop() })
            assertEquals(listOf("login"), fixture.jdbc.committed.map { it.type })
            assertEquals(1, fixture.closed.get())
            assertSame(
                fixture.repository,
                field<CentralActivityLogRepository>(fixture.writer, "repository"),
            )
            assertSame(fixture.resourceOwner, field<AutoCloseable>(fixture.writer, "dataSource"))
        } finally {
            fixture.finishExecutor()
        }
    }

    private fun assertRetainedRecord(fixture: Fixture, cause: Throwable) {
        val retained = field<Collection<*>>(fixture.writer, "failedWrites").single()!!
        assertSame(cause, field<Throwable>(retained, "cause"))
        assertSame(fixture.repository, field<CentralActivityLogRepository>(retained, "repository"))
        val record = assertIs<CentralActivityLog.Login>(field<CentralActivityLog>(retained, "log"))
        assertEquals(7, record.characterId)
        assertEquals(70L, record.accountId)
    }

    private fun player(): Player =
        Player().apply {
            characterId = 7
            accountId = 70
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as T

    private class Fixture(
        timeoutMillis: Long = 5_000,
        closeFailure: Throwable? = null,
        beforeCommit: (Record) -> Unit = {},
    ) {
        val executor = TrackingExecutor()
        val closed = AtomicInteger()
        val events = CopyOnWriteArrayList<String>()
        val jdbc = RecordingJdbc(events, beforeCommit)
        val repository = CentralActivityLogRepository(jdbc.dataSource)
        val resourceOwner = AutoCloseable {
            closed.incrementAndGet()
            closeFailure?.let { throw it }
            jdbc.closed.set(true)
            events.add("datasource")
        }
        val writer =
            CentralActivityLogWriter(
                    ServerConfig("shutdown-test", 0, 240, "test", 1),
                    executor,
                    { CentralActivityLogWriter.Resources(repository, resourceOwner) },
                    timeoutMillis,
                )
                .apply { start() }

        fun finishExecutor() {
            executor.shutdown()
            assertTrue(
                executor.awaitTermination(10, TimeUnit.SECONDS),
                "Test writer executor did not terminate",
            )
        }
    }

    private data class Record(
        val type: String,
        val accountId: Long,
        val characterId: Int,
        val worldId: Int,
        val payload: String,
    )

    /** Executes the real repository transaction, including generated keys and item-line batches. */
    private class RecordingJdbc(
        private val events: MutableList<String>,
        private val beforeCommit: (Record) -> Unit,
    ) {
        val closed = AtomicBoolean()
        val attempted = CopyOnWriteArrayList<Record>()
        val committed = CopyOnWriteArrayList<Record>()
        val itemLines = AtomicInteger()
        val dataSource: DataSource =
            proxy(DataSource::class.java) { name, _ ->
                check(!closed.get()) { "A write used the closed datasource" }
                when (name) {
                    "getConnection" -> connection()
                    else -> error("Unexpected DataSource.$name")
                }
            }

        private fun connection(): Connection {
            var pending: Record? = null
            var lines = 0
            return proxy(Connection::class.java) { name, _ ->
                when (name) {
                    "getAutoCommit" -> true
                    "setAutoCommit",
                    "rollback",
                    "close" -> null
                    "prepareStatement" -> {
                        val parameters = mutableMapOf<Int, Any?>()
                        proxy(PreparedStatement::class.java) { statementMethod, args ->
                            when (statementMethod) {
                                "setString",
                                "setInt",
                                "setLong",
                                "setObject" -> {
                                    parameters[args[0] as Int] = args[1]
                                    null
                                }
                                "executeQuery" -> {
                                    pending =
                                        Record(
                                            parameters[1] as String,
                                            parameters[3] as Long,
                                            parameters[4] as Int,
                                            parameters[5] as Int,
                                            parameters[6].toString(),
                                        )
                                    resultSet()
                                }
                                "addBatch" -> {
                                    lines++
                                    null
                                }
                                "executeBatch" -> IntArray(lines) { 1 }
                                "close" -> null
                                else -> error("Unexpected PreparedStatement.$statementMethod")
                            }
                        }
                    }
                    "commit" -> {
                        val record = checkNotNull(pending)
                        attempted.add(record)
                        beforeCommit(record)
                        committed.add(record)
                        itemLines.addAndGet(lines)
                        events.add(record.type)
                        null
                    }
                    else -> error("Unexpected Connection.$name")
                }
            }
        }

        private fun resultSet(): ResultSet {
            val first = AtomicBoolean(true)
            return proxy(ResultSet::class.java) { name, _ ->
                when (name) {
                    "next" -> first.getAndSet(false)
                    "getLong" -> 1L
                    "getObject" -> UUID(1L, 1L)
                    "close" -> null
                    else -> error("Unexpected ResultSet.$name")
                }
            }
        }

        private fun <T : Any> proxy(type: Class<T>, call: (String, Array<out Any?>) -> Any?): T =
            type.cast(
                Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, arguments ->
                    call(method.name, arguments ?: emptyArray())
                }
            )
    }

    private class TrackingExecutor :
        ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(),
            { task -> Thread(task, "activity-log-test").apply { isDaemon = true } },
        ) {
        val awaitEntered = CountDownLatch(1)
        var forcedShutdowns = 0
            private set

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
            awaitEntered.countDown()
            return super.awaitTermination(timeout, unit)
        }

        override fun shutdownNow(): MutableList<Runnable> {
            forcedShutdowns++
            return super.shutdownNow()
        }
    }
}
