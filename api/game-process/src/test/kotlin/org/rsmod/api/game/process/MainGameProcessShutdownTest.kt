package org.rsmod.api.game.process

import java.util.ArrayDeque
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.rsmod.api.db.gateway.GameDbSynchronizer
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.db.gateway.service.ResponseDbGatewayService
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

class MainGameProcessShutdownTest {
    @Test
    fun `drained world dispatches all gateway callbacks without advancing world time`() {
        val fixture = Fixture()
        val thread = Thread.currentThread()
        var callbacks = 0
        repeat(151) {
            fixture.enqueue {
                assertSame(thread, Thread.currentThread())
                callbacks++
            }
        }

        fixture.process.preShutdown()

        assertEquals(151, callbacks)
        assertTrue(fixture.responses.isEmpty())
        verifyNoInteractions(fixture.cycle)
    }

    @Test
    fun `native logout cycles stop immediately after the last player is removed`() {
        val fixture = Fixture()
        val player = Player()
        fixture.players[1] = player
        var callbacksDispatched = false
        fixture.enqueue { callbacksDispatched = true }
        var cycles = 0
        doAnswer {
                assertTrue(player.pendingShutdown)
                assertTrue(callbacksDispatched)
                if (++cycles == 3) fixture.players.remove(1)
                null
            }
            .`when`(fixture.cycle)
            .tick()

        fixture.process.preShutdown()

        assertEquals(3, cycles)
        assertTrue(fixture.players.none())
    }

    @Test
    fun `a logging out player still blocks successful shutdown at the cycle limit`() {
        val fixture = Fixture()
        val player = Player().apply { loggingOut = true }
        fixture.players[1] = player

        val failure =
            assertThrows(IllegalStateException::class.java) { fixture.process.preShutdown() }

        assertTrue(failure.message.orEmpty().contains("unable to logout"))
        verify(fixture.cycle, times(1024)).tick()
        assertSame(player, fixture.players[1])
    }

    @Test
    fun `gateway callback failure prevents successful finalization`() {
        val fixture = Fixture()
        val failure = IllegalStateException("callback failed")
        fixture.enqueue { throw failure }

        val thrown =
            assertThrows(IllegalStateException::class.java) { fixture.process.preShutdown() }

        assertSame(failure, thrown)
        verifyNoInteractions(fixture.cycle)
    }

    @Test
    fun `shutdown still publishes native lifecycle finalization`() {
        val fixture = Fixture()
        var finalized = false
        fixture.events.subscribeUnbound(GameLifecycle.Shutdown::class.java) { finalized = true }

        fixture.process.preShutdown()
        fixture.process.shutdown()

        assertTrue(finalized)
    }

    private class Fixture {
        val cycle = mock(GameCycle::class.java)
        val events = EventBus()
        val players = PlayerList()
        val responses = ArrayDeque<ResponseDbGatewayService.PendingCallback<*>>()
        private val gateway = mock(ResponseDbGatewayService::class.java)
        val process = MainGameProcess(cycle, events, players, GameDbSynchronizer(gateway))

        init {
            doAnswer { invocation ->
                    val target =
                        invocation.getArgument<
                            MutableCollection<ResponseDbGatewayService.PendingCallback<*>>
                        >(
                            0
                        )
                    val count = invocation.getArgument<Int>(1)
                    repeat(minOf(count, responses.size)) { target += responses.removeFirst() }
                    null
                }
                .`when`(gateway)
                .take(org.mockito.ArgumentMatchers.anyList(), anyInt())
        }

        fun enqueue(action: () -> Unit) {
            responses +=
                ResponseDbGatewayService.PendingCallback({ action() }, GameDbResult.Ok(Unit))
        }
    }
}
