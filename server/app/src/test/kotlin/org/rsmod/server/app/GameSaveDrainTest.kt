package org.rsmod.server.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.mockito.Mockito.mock
import org.rsmod.api.account.saver.AccountSavingService
import org.rsmod.api.game.process.PluginScriptBootGate
import org.rsmod.api.registry.account.AccountRegistry
import org.rsmod.game.GameProcess
import org.rsmod.game.GameUpdate
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

@OptIn(ExperimentalCoroutinesApi::class)
class GameSaveDrainTest {
    @Test
    fun `preflight waits for native updating and every save callback then quiesces cycles`() =
        runTest {
            val fixture = Fixture()
            val result = fixture.service.prepareShutdown()
            fixture.service.run()
            assertFalse(result.isDone)
            fixture.update.setUpdating()
            fixture.saves = AccountSavingService.DrainStatus(1, 0, 0, 0, true)
            fixture.service.run()
            assertFalse(result.isDone)
            fixture.saves = AccountSavingService.DrainStatus(0, 0, 0, 1, true)
            fixture.logouts = AccountRegistry.LogoutStatus(1, 0)
            fixture.service.run()
            assertFalse(result.isDone)
            fixture.logouts = AccountRegistry.LogoutStatus(0, 0)
            fixture.service.run()
            assertTrue(result.isDone && !result.isCompletedExceptionally)
            val before = fixture.cycles
            fixture.service.run()
            kotlin.test.assertEquals(before, fixture.cycles)
        }

    @Test
    fun `failed save blocks preflight without stopping native game processing`() = runTest {
        val fixture = Fixture()
        fixture.saves = AccountSavingService.DrainStatus(0, 1, 0, 0, true)
        val result = fixture.service.prepareShutdown()
        fixture.service.run()
        assertTrue(result.isCompletedExceptionally)
        val before = fixture.cycles
        fixture.service.run()
        assertTrue(fixture.cycles > before)
    }

    @Test
    fun `an attached player blocks preflight even with no pending save`() = runTest {
        val fixture = Fixture()
        fixture.players[1] = mock(Player::class.java)
        fixture.update.setUpdating()
        val result = fixture.service.prepareShutdown()
        fixture.service.run()
        assertFalse(result.isDone)
        fixture.players.remove(1)
        fixture.service.run()
        assertTrue(result.isDone && !result.isCompletedExceptionally)
    }

    private class Fixture {
        val players = PlayerList()
        val update = GameUpdate()
        var cycles = 0
        var saves = AccountSavingService.DrainStatus(0, 0, 0, 0, true)
        var logouts = AccountRegistry.LogoutStatus(0, 0)
        val saver =
            mock(AccountSavingService::class.java) { call ->
                if (call.method.name == "drainStatus") saves else null
            }
        val registry =
            mock(AccountRegistry::class.java) { call ->
                if (call.method.name == "logoutStatus") logouts else null
            }
        val process =
            mock(GameProcess::class.java) { call ->
                if (call.method.name == "cycle") cycles++
                null
            }
        val service =
            GameService(
                process,
                mock(PluginScriptBootGate::class.java),
                update,
                players,
                registry,
                saver,
            )
    }
}
