package org.rsmod.content.areas.wilderness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForinthrySurgeTimeTest {
    @Test
    fun `maledictus source accepts the active cache and legacy aliases`() {
        assertTrue(isMaledictusSurgeSource("wild_cave_superior", null))
        assertTrue(isMaledictusSurgeSource("other", "npc.wild_cave_superior"))
        assertTrue(isMaledictusSurgeSource("other", "npc.revenant_maledictus"))
        assertFalse(isMaledictusSurgeSource("wild_cave_dragon", "npc.wild_cave_dragon"))
    }

    @Test
    fun `surge starts at thirty minutes`() {
        assertEquals(11_800, ForinthrySurgeTime.startExpiry(10_000))
        assertEquals(1_800, ForinthrySurgeTime.remainingSeconds(11_800, 10_000))
        assertEquals(3_000, ForinthrySurgeTime.secondsToTicks(1_800))
    }

    @Test
    fun `only a real world change removes one minute`() {
        assertEquals(11_800, ForinthrySurgeTime.applyWorldHopPenalty(11_800, 10_000, 301, 301))
        assertEquals(11_740, ForinthrySurgeTime.applyWorldHopPenalty(11_800, 10_000, 301, 302))
    }

    @Test
    fun `world hop penalty cannot make the timer negative`() {
        assertEquals(10_000, ForinthrySurgeTime.applyWorldHopPenalty(10_030, 10_000, 301, 302))
        assertEquals(0, ForinthrySurgeTime.remainingSeconds(10_000, 10_000))
    }
}
