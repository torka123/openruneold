package org.rsmod.server.app

import jakarta.inject.Provider
import java.lang.reflect.Proxy
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.rsmod.api.account.AccountManager
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.account.loader.AccountLoaderService
import org.rsmod.api.account.saver.AccountSavingService
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.jdbc.GameDatabase
import org.rsmod.api.game.process.player.PlayerLogoutProcess
import org.rsmod.api.game.process.player.PlayerLogoutProcessor
import org.rsmod.api.registry.account.AccountRegistry
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.player.PlayerRegistryResult
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.entity.util.ShuffledPlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/** Real native logout/save/removal owners; the JDBC boundary is a deterministic proxy, not PG. */
class NativeLogoutDrainIntegrationTest {
    @Test
    fun `native logout saves final hook snapshot before actual removal exactly once`() = runTest {
        val fixture = Fixture()
        fixture.player.pendingShutdown = true
        fixture.logout.process()
        assertTrue(fixture.player.loggingOut)
        fixture.assertAttached()
        assertEquals(listOf("logout"), fixture.events)
        fixture.saver.run()
        assertEquals(listOf("logout", "save:321", "commit"), fixture.events)
        fixture.assertAttached()
        assertFalse(fixture.accounts.logoutStatus().isDrained)
        fixture.logout.process()
        fixture.assertRemoved()
        repeat(2) {
            fixture.logout.process()
            fixture.saver.run()
        }
        assertEquals(listOf("logout", "save:321", "commit", "delete"), fixture.events)
        assertEquals(1L, fixture.saver.drainStatus().committed)
        verifyNoInteractions(fixture.errors)
    }

    @Test
    fun `failed native save retains actual player until explicit repaired retry commits`() =
        runTest {
            val fixture = Fixture()
            fixture.failSave = true
            fixture.player.pendingShutdown = true
            fixture.logout.process()
            repeat(4) { fixture.saver.run() }
            fixture.logout.process()
            fixture.assertAttached()
            assertEquals(1, fixture.accounts.logoutStatus().failed)
            assertEquals(1, fixture.saver.drainStatus().failed)
            assertEquals(0L, fixture.saver.drainStatus().committed)
            fixture.failSave = false
            fixture.accounts.retryFailedLogouts()
            fixture.logout.process()
            fixture.assertAttached()
            fixture.saver.run()
            fixture.assertAttached()
            fixture.logout.process()
            fixture.assertRemoved()
            assertEquals(1, fixture.events.count { it == "commit" })
            assertEquals(1, fixture.events.count { it == "delete" })
        }

    @Test
    fun `throwing native deletion hook remains a blocked drain not a false success`() = runTest {
        val fixture = Fixture()
        fixture.bus.subscribeUnbound(SessionStateEvent.Delete::class.java) { error("delete hook") }
        fixture.player.pendingShutdown = true
        fixture.logout.process()
        fixture.saver.run()
        assertFailsWith<IllegalStateException> { fixture.logout.process() }
        assertFalse(fixture.accounts.logoutStatus().isDrained)
        fixture.accounts.retryFailedLogouts()
        assertFalse(fixture.accounts.logoutStatus().isDrained)
        assertEquals(1L, fixture.saver.drainStatus().committed)
        // Existing PlayerRegistry failure behavior is characterized, not silently redesigned
        // here.
        assertTrue(fixture.players.none())
        assertTrue(fixture.registry.findAll(fixture.zone).any { it === fixture.player })
    }

    private class Fixture {
        val events = mutableListOf<String>()
        var failSave = false
        val bus = EventBus()
        val clock = MapClock()
        val players = PlayerList()
        val collision = CollisionFlagMap()
        val zones = ZonePlayerActivityBitSet()
        val errors = mock(GameExceptionHandler::class.java)
        val registry = PlayerRegistry(players, collision, zones, bus, clock)
        val player =
            Player().apply {
                uuid = 1L
                slotId = 1
                accountId = 1
                characterId = 1
                userId = 1L
                accountHash = 1L
                coords = CoordGrid(3200, 3200)
            }
        val zone = ZoneKey.from(player.coords)
        val saver: AccountSavingService
        val accounts: AccountRegistry
        val logout: PlayerLogoutProcess

        init {
            assertEquals(PlayerRegistryResult.Add.Success, registry.add(player))
            registry.change(player, ZoneKey.NULL, zone)
            player.addBlockWalkCollision(collision, player.coords)
            bus.subscribeUnbound(SessionStateEvent.Logout::class.java) {
                events += "logout"
                player.runEnergy = 321
            }
            bus.subscribeUnbound(SessionStateEvent.Delete::class.java) { events += "delete" }
            val connection =
                Proxy.newProxyInstance(
                    Connection::class.java.classLoader,
                    arrayOf(Connection::class.java),
                ) { _, method, _ ->
                    when (method.name) {
                        "isClosed" -> false
                        "commit" -> {
                            events += "commit"
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
                    ) {
                        check(!failSave) { "fixture transaction failure" }
                        events += "save:${player.runEnergy}"
                    }
                }
            saver =
                AccountSavingService(
                    database,
                    mock(CharacterAccountRepository::class.java),
                    setOf(pipeline),
                    mock(ServerConfig::class.java),
                )
            accounts =
                AccountRegistry(
                    bus,
                    registry,
                    AccountManager(mock(AccountLoaderService::class.java), saver),
                )
            logout =
                PlayerLogoutProcess(
                    bus,
                    clock,
                    ShuffledPlayerList(players),
                    accounts,
                    errors,
                    PlayerLogoutProcessor(bus, accounts, errors),
                )
        }

        fun assertAttached() {
            assertSame(player, players[1])
            assertSame(player, player.uid.resolve(players))
            assertTrue(registry.findAll(zone).any { it === player })
            assertTrue(
                collision[player.coords.x, player.coords.z, player.coords.level] and
                    CollisionFlag.BLOCK_NPCS != 0
            )
        }

        fun assertRemoved() {
            assertTrue(players.none())
            assertEquals(PathingEntity.INVALID_SLOT, player.slotId)
            assertEquals(PlayerUid.NULL, player.uid)
            assertTrue(registry.findAll(zone).none())
            assertFalse(zones.isAnyLevelFlagged(zone, zone))
            assertEquals(
                0,
                collision[player.coords.x, player.coords.z, player.coords.level] and
                    CollisionFlag.BLOCK_NPCS,
            )
            assertTrue(accounts.logoutStatus().isDrained)
            assertTrue(saver.drainStatus().isDrained)
        }
    }
}
