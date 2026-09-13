package org.rsmod.api.registry.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.rsmod.api.account.AccountManager
import org.rsmod.api.account.saver.request.AccountSaveCallback
import org.rsmod.api.account.saver.request.AccountSaveResponse
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.player.PlayerRegistryResult
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

class AccountLogoutSaveTest {
    @Test
    fun `native detach failure cannot falsely clear logout tracking`() {
        val fixture = Fixture()
        fixture.deleteResult = PlayerRegistryResult.Delete.UnexpectedSlot
        fixture.registry.queueLogout(fixture.player)
        fixture.callback(AccountSaveResponse.Success(fixture.player))
        assertFailsWith<IllegalStateException> { fixture.registry.handleLogouts() }
        assertFalse(fixture.registry.logoutStatus().isDrained)
    }

    @Test
    fun `explicit failed logout retry waits for a new successful acknowledgement`() {
        val fixture = Fixture()
        fixture.registry.queueLogout(fixture.player)
        fixture.callback(AccountSaveResponse.ExcessiveRetries(fixture.player))
        fixture.registry.retryFailedLogouts()
        fixture.registry.handleLogouts()
        verifyNoInteractions(fixture.players)
        fixture.callback(AccountSaveResponse.Success(fixture.player))
        fixture.registry.handleLogouts()
        assertTrue(fixture.registry.logoutStatus().isDrained)
    }

    @Test
    fun `player removal awaits successful save and runs once`() {
        val fixture = Fixture()
        fixture.registry.queueLogout(fixture.player)
        fixture.registry.queueLogout(fixture.player)
        fixture.registry.handleLogouts()
        verifyNoInteractions(fixture.players)
        assertFalse(fixture.registry.logoutStatus().isDrained)
        fixture.callback(AccountSaveResponse.Success(fixture.player))
        fixture.callback(AccountSaveResponse.Success(fixture.player))
        assertFalse(fixture.registry.logoutStatus().isDrained)
        fixture.registry.handleLogouts()
        verify(fixture.players, times(1)).del(fixture.player)
        assertTrue(fixture.registry.logoutStatus().isDrained)
    }

    @Test
    fun `failed save retains player and blocks drained status`() {
        for (shutdown in listOf(false, true)) {
            val fixture = Fixture()
            fixture.registry.queueLogout(fixture.player)
            fixture.callback(
                if (shutdown) AccountSaveResponse.InternalShutdownError(fixture.player)
                else AccountSaveResponse.ExcessiveRetries(fixture.player)
            )
            fixture.registry.handleLogouts()
            verifyNoInteractions(fixture.players)
            assertEquals(1, fixture.registry.logoutStatus().failed)
            assertFalse(fixture.registry.logoutStatus().isDrained)
        }
    }

    private class Fixture {
        lateinit var callback: AccountSaveCallback
        var deleteResult: PlayerRegistryResult.Delete = PlayerRegistryResult.Delete.Success
        val player = mock(Player::class.java)
        val players =
            mock(PlayerRegistry::class.java) { call ->
                if (call.method.name == "del") deleteResult else null
            }
        val accounts =
            mock(AccountManager::class.java) { call ->
                if (call.method.name == "save") callback = call.getArgument(1)
                null
            }
        val registry = AccountRegistry(mock(EventBus::class.java), players, accounts)
    }
}
