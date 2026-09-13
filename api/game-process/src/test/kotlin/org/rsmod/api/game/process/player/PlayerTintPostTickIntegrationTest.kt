package org.rsmod.api.game.process.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.rsmod.api.account.autosave.PlayerAutosaveOrchestrator
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.events.EventBus
import org.rsmod.game.client.ClientCycle
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerTint
import org.rsmod.game.entity.player.PlayerTintToken
import org.rsmod.game.entity.util.ShuffledPlayerList

/**
 * Runs the native PlayerPostTickProcess and EventBus with a recording ClientCycle transport edge.
 * Peripheral update processors and autosave are mocked; the native Player's no-op client handles
 * unrelated output. The tests verify queue consumption and token ownership, not client rendering or
 * elapsed tint duration. MARK uses the retained Nex C-GM Sacrifice tint fields indexed in
 * docs/bosses/nex/NEX_OSRS_AUDIT.md; no server timer is inferred from its client-cycle interval.
 */
class PlayerTintPostTickIntegrationTest {
    @Test
    fun `successful synchronization consumes the update but keeps its conditional clear owner`() {
        val f = Fixture()
        val token = f.player.tintOwned(MARK)

        f.process.process()

        assertEquals(listOf(MARK), f.cycle.updates)
        assertNull(f.player.pendingTint)
        assertTrue(f.player.clearTint(token))
        assertEquals(PlayerTint.CLEAR, f.player.pendingTint)

        f.process.process()
        assertEquals(listOf(MARK, PlayerTint.CLEAR), f.cycle.updates)
        assertNull(f.player.pendingTint)
        assertFalse(f.player.clearTint(token))
        f.process.process()
        assertEquals(listOf(MARK, PlayerTint.CLEAR, null), f.cycle.updates)
    }

    @Test
    fun `conditional clear from the real post-tick hook survives to the next synchronization once`() {
        val f = Fixture()
        val token = f.player.tintOwned(MARK)
        var clearInHook = true
        f.postTick = { player ->
            if (clearInHook) {
                clearInHook = false
                assertEquals(listOf(MARK), f.cycle.updates)
                assertNull(player.pendingTint, "The synchronized update is consumed before hooks")
                assertTrue(player.clearTint(token))
            }
        }

        f.process.process()

        assertEquals(
            PlayerTint.CLEAR,
            f.player.pendingTint,
            "Late cleanup must retain the new update",
        )
        assertEquals(listOf(MARK), f.cycle.updates)
        f.process.process()
        assertEquals(listOf(MARK, PlayerTint.CLEAR), f.cycle.updates)
        assertNull(f.player.pendingTint)
        f.process.process()
        assertEquals(listOf(MARK, PlayerTint.CLEAR, null), f.cycle.updates)
    }

    @Test
    fun `UpdateInfo replacement survives late cleanup and defeats an old post-tick clear`() {
        val f = Fixture()
        val token = f.player.tintOwned(MARK)
        var replace = true
        f.events.subscribeUnbound(GameLifecycle.UpdateInfo::class.java) {
            if (replace) {
                replace = false
                assertEquals(listOf(MARK), f.cycle.updates)
                assertNull(f.player.pendingTint)
                f.player.tint(UNRELATED)
            }
        }
        f.postTick = { player -> assertFalse(player.clearTint(token)) }

        f.process.process()

        assertEquals(UNRELATED, f.player.pendingTint)
        assertEquals(listOf(MARK), f.cycle.updates)
        f.process.process()
        assertEquals(listOf(MARK, UNRELATED), f.cycle.updates)
        assertNull(f.player.pendingTint)
        f.process.process()
        assertEquals(listOf(MARK, UNRELATED, null), f.cycle.updates)
    }

    @Test
    fun `same-object owned replacement after synchronization gets its own future clear`() {
        val f = Fixture()
        val old = f.player.tintOwned(MARK)
        var replacement: PlayerTintToken? = null
        f.events.subscribeUnbound(GameLifecycle.UpdateInfo::class.java) {
            if (replacement == null) replacement = f.player.tintOwned(MARK)
        }
        f.postTick = { player -> assertFalse(player.clearTint(old)) }

        f.process.process()
        assertEquals(MARK, f.player.pendingTint)
        f.process.process()
        assertEquals(listOf(MARK, MARK), f.cycle.updates)
        assertNull(f.player.pendingTint)
        assertTrue(f.player.clearTint(checkNotNull(replacement)))

        f.process.process()
        assertEquals(listOf(MARK, MARK, PlayerTint.CLEAR), f.cycle.updates)
        assertNull(f.player.pendingTint)
        f.process.process()
        assertEquals(listOf(MARK, MARK, PlayerTint.CLEAR, null), f.cycle.updates)
    }

    private class Fixture {
        val events = EventBus()
        val cycle = RecordingClientCycle()
        val player =
            Player(clientCycle = cycle).apply {
                slotId = 1
                uuid = 1L
                assignUid()
            }
        var postTick: (Player) -> Unit = {}
        private val players = PlayerList().apply { this[1] = player }
        val process =
            PlayerPostTickProcess(
                eventBus = events,
                playerList = ShuffledPlayerList(players),
                zoneUpdates = mock(PlayerZoneUpdateProcessor::class.java),
                buildAreas = mock(PlayerBuildAreaProcessor::class.java),
                regions = mock(PlayerRegionProcessor::class.java),
                facing = mock(PlayerFaceSquareProcessor::class.java),
                mapUpdates = mock(PlayerMapUpdateProcessor::class.java),
                runUpdates = mock(PlayerRunUpdateProcessor::class.java),
                invUpdates = mock(PlayerInvUpdateProcessor::class.java),
                statUpdates = mock(PlayerStatUpdateProcessor::class.java),
                exceptionHandler =
                    GameExceptionHandler { failure, message ->
                        throw AssertionError(message().toString(), failure)
                    },
                playerAutosave = mock(PlayerAutosaveOrchestrator::class.java),
                postTickHooks = setOf(PlayerPostTickHook { postTick(it) }),
            )
    }

    private class RecordingClientCycle : ClientCycle {
        val updates = mutableListOf<PlayerTint?>()

        override fun update(player: Player) {
            updates += player.pendingTint
        }

        override fun flush(player: Player) {}

        override fun release() {
            throw AssertionError("Tint synchronization must not release this online player's cycle")
        }
    }

    private companion object {
        val MARK = PlayerTint(0, 210, 0, 6, 28, 112)
        val UNRELATED = PlayerTint(0, 605, 0, 0, 0, 112)
    }
}
