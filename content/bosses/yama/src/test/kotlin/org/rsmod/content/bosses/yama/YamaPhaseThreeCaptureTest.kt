package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals

class YamaPhaseThreeCaptureTest {
    @Test
    fun `phase three schedules exactly three personal shadow crashes`() {
        assertEquals(3, YamaRules.PHASE_THREE_SHADOW_CRASH_SETS)
    }

    @Test
    fun `each shadow crash locks its tile three ticks before impact`() {
        val locks = YamaCombatTimeline.shadowCrashLockCycles(300, 3)
        assertEquals(listOf(302, 305, 308), locks)
        assertEquals(
            listOf(305, 308, 311),
            locks.map { YamaCombatTimeline.shadowCrashImpactCycle(it) },
        )
    }

    @Test
    fun `contract fire streaks use nine distinct lanes`() {
        assertEquals(YamaRules.FIRE_STREAK_CONTRACT_COUNT, YamaRules.FIRE_STREAK_LANES.size)
        assertEquals(YamaRules.FIRE_STREAK_LANES.size, YamaRules.FIRE_STREAK_LANES.distinct().size)
    }
}
