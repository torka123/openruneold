package org.rsmod.content.bosses.wilderness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaledictusEventTest {
    @Test
    fun `combat levels accumulate and the supplied cumulative roll is deterministic`() {
        val state = MaledictusSpawnAccumulator(denominator = 1_000, lockoutTicks = 100)
        assertFalse(state.recordKill(combatLevel = 98, roll = 98, tick = 0))
        assertEquals(98, state.progress)
        assertTrue(state.recordKill(combatLevel = 82, roll = 179, tick = 1))
        assertEquals(180, state.progress)
    }

    @Test
    fun `one active event blocks duplicate spawns and progress resets once`() {
        val state = MaledictusSpawnAccumulator(denominator = 1_000, lockoutTicks = 100)
        assertTrue(state.recordKill(combatLevel = 500, roll = 0, tick = 20))
        assertTrue(state.markSpawned(20))
        assertEquals(0, state.progress)
        assertTrue(state.active)
        assertFalse(state.markSpawned(21))
        assertFalse(state.recordKill(combatLevel = 500, roll = 0, tick = 21))
    }

    @Test
    fun `death or expiry clears active state but preserves the spawn lockout`() {
        val state = MaledictusSpawnAccumulator(denominator = 1_000, lockoutTicks = 100)
        assertTrue(state.markSpawned(50))
        state.markInactive()
        assertFalse(state.active)
        assertFalse(state.canSpawn(149))
        assertTrue(state.canSpawn(150))
    }

    @Test
    fun `invalid kills do not contribute and progress cannot overflow`() {
        val state =
            MaledictusSpawnAccumulator(denominator = 1_000, initialProgress = 990, lockoutTicks = 0)
        assertFalse(state.recordKill(combatLevel = 0, roll = 0, tick = 0))
        assertEquals(990, state.progress)
        assertTrue(state.recordKill(combatLevel = 135, roll = 999, tick = 0))
        assertEquals(1_000, state.progress)
    }

    @Test
    fun `restart seed is clamped below the guaranteed roll boundary`() {
        val state =
            MaledictusSpawnAccumulator(
                denominator = 1_000,
                initialProgress = Int.MAX_VALUE,
                lockoutTicks = 0,
            )
        assertEquals(999, state.progress)
    }
}
