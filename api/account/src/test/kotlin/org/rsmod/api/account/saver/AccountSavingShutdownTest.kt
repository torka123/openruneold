package org.rsmod.api.account.saver

import jakarta.inject.Provider
import java.lang.reflect.Proxy
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.account.saver.request.AccountSaveRequest
import org.rsmod.api.account.saver.request.AccountSaveResponse
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.jdbc.GameDatabase
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.Player

class AccountSavingShutdownTest {
    @Test
    fun `shutdown drains more than a prefetched batch`() = runBlocking {
        val fixture = Fixture()
        repeat(31) { fixture.queue(it) }
        fixture.saver.shutdown()
        assertEquals(31, fixture.commits)
        assertEquals(31, fixture.responses.size)
        assertTrue(fixture.responses.all { it is AccountSaveResponse.Success })
    }

    @Test
    fun `cancellation retains the current request and unread batch suffix`() = runBlocking {
        val fixture = Fixture()
        repeat(3) { fixture.queue(it) }
        fixture.onSave = { id -> if (id == 1) throw CancellationException("writer cancelled") }
        assertFailsWith<CancellationException> { fixture.saver.run() }
        assertEquals(1, fixture.commits)
        fixture.onSave = {}
        fixture.saver.shutdown()
        assertEquals(3, fixture.commits)
        assertEquals(3, fixture.responses.size)
        assertTrue(fixture.responses.all { it is AccountSaveResponse.Success })
    }

    @Test
    fun `four failed attempts are not a committed shutdown`() = runBlocking {
        val fixture = Fixture()
        var attempts = 0
        fixture.onSave = {
            attempts++
            error("database unavailable")
        }
        fixture.queue(1)
        assertFailsWith<IllegalStateException> { fixture.saver.shutdown() }
        assertEquals(4, attempts)
        assertEquals(0, fixture.commits)
        assertEquals(1, fixture.responses.size)
        assertTrue(fixture.responses.single() is AccountSaveResponse.ExcessiveRetries)
    }

    @Test
    fun `throwing callback never retries an already committed save`() = runBlocking {
        val fixture = Fixture()
        fixture.saver.queue(
            AccountSaveRequest(1, 1, mock(Player::class.java), { error("callback failed") })
        )
        assertFailsWith<IllegalStateException> { fixture.saver.shutdown() }
        assertEquals(1, fixture.commits)
    }

    @Test
    fun `fatal callback is recorded without repeating its committed transaction`() = runBlocking {
        val fixture = Fixture()
        var calls = 0
        fixture.saver.queue(
            AccountSaveRequest(
                1,
                1,
                mock(Player::class.java),
                {
                    calls++
                    throw AssertionError("callback")
                },
            )
        )
        repeat(2) { assertFailsWith<IllegalStateException> { fixture.saver.shutdown() } }
        assertEquals(1, calls)
        assertEquals(1, fixture.commits)
        assertEquals(1, fixture.saver.drainStatus().callbackFailures)
    }

    @Test
    fun `duplicate pending request commits once and late shutdown admission is refused`() =
        runBlocking<Unit> {
            val fixture = Fixture()
            val request =
                AccountSaveRequest(1, 1, mock(Player::class.java), { fixture.responses += it })
            fixture.saver.queue(request)
            fixture.saver.queue(request)
            fixture.saver.shutdown()
            assertEquals(1, fixture.commits)
            assertEquals(1, fixture.responses.size)
            assertFailsWith<IllegalStateException> { fixture.saver.queue(request) }
        }

    @Test
    fun `fresh full snapshot clears terminal failure only after a real commit`() = runBlocking {
        val fixture = Fixture()
        val player = mock(Player::class.java)
        val request = AccountSaveRequest(1, 1, player, { fixture.responses += it })
        fixture.onSave = { error("temporary outage") }
        fixture.saver.queue(request)
        repeat(4) { fixture.saver.run() }
        assertEquals(1, fixture.saver.drainStatus().failed)
        assertEquals(0, fixture.commits)
        fixture.onSave = {}
        fixture.saver.queue(request.copy(attempts = 0))
        assertEquals(1, fixture.saver.drainStatus().failed)
        fixture.saver.shutdown()
        assertEquals(1, fixture.commits)
        assertTrue(fixture.saver.drainStatus().isDrained)
        assertEquals(2, fixture.responses.size)
    }

    private class Fixture {
        var commits = 0
        var onSave: (Int) -> Unit = {}
        val responses = mutableListOf<AccountSaveResponse>()
        val saver: AccountSavingService

        init {
            val connection =
                Proxy.newProxyInstance(
                    Connection::class.java.classLoader,
                    arrayOf(Connection::class.java),
                ) { _, method, _ ->
                    when (method.name) {
                        "isClosed" -> false
                        "commit" -> {
                            commits++
                            null
                        }
                        "rollback",
                        "close" -> null
                        else -> error("Unexpected JDBC call: ${method.name}")
                    }
                } as Connection
            val database = GameDatabase(Provider<DatabaseConfig> { error("unused") })
            GameDatabase::class
                .java
                .getDeclaredField("connection")
                .apply { isAccessible = true }
                .set(database, connection)
            val pipeline =
                object : CharacterDataStage.Pipeline {
                    override fun append(
                        connection: DatabaseConnection,
                        metadata: CharacterMetadataList,
                    ) {}

                    override fun save(
                        connection: DatabaseConnection,
                        player: Player,
                        characterId: Int,
                    ) = onSave(characterId)
                }
            saver =
                AccountSavingService(
                    database,
                    mock(CharacterAccountRepository::class.java),
                    setOf(pipeline),
                    mock(ServerConfig::class.java),
                )
        }

        fun queue(id: Int) =
            saver.queue(AccountSaveRequest(id, id, mock(Player::class.java), { responses += it }))
    }
}
