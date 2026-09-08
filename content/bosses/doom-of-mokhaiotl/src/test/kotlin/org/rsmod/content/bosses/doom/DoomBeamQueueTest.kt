package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoomBeamQueueTest {
    @Test
    fun `beam fire and impact remain one tick apart`() {
        val fireCycle = 100
        val impactCycle = DoomCombatTimeline.beamImpactCycle(fireCycle, projectileCycles = 1)

        assertEquals(101, impactCycle)
        assertEquals(1, impactCycle - fireCycle)
    }

    @Test
    fun `beam cancellation matches queue arguments by identity not equality`() {
        data class QueueArgument(val value: Int)

        val beamHit = QueueArgument(1)
        val equalButUnrelatedHit = QueueArgument(1)
        assertEquals(beamHit, equalButUnrelatedHit)
        assertTrue(DoomBeamQueue.matchesIdentity(beamHit, beamHit))
        assertFalse(DoomBeamQueue.matchesIdentity(equalButUnrelatedHit, beamHit))
    }
}
