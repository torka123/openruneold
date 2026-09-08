package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoomAuditRegressionTest {
    @Test
    fun `captured boulder split and followup cycles are exact for every delve`() {
        val launch = 100
        val expectedExplosion = listOf(107, 107, 107, 107, 107, 106, 106, 106)
        for (delve in 1..8) {
            assertEquals(
                expectedExplosion[delve - 1],
                DoomCombatTimeline.rockExplosionCycle(launch, delve, volleyIndex = 0),
                "first split cycle for delve $delve",
            )
        }

        val firstExplosion = DoomCombatTimeline.rockExplosionCycle(launch, 8, volleyIndex = 0)
        val secondExplosion = DoomCombatTimeline.rockExplosionCycle(launch, 8, volleyIndex = 1)
        val firstDebris = DoomCombatTimeline.rockShadowImpactCycle(firstExplosion)
        val secondDebris = DoomCombatTimeline.rockShadowImpactCycle(secondExplosion)
        assertEquals(106, firstExplosion)
        assertEquals(109, secondExplosion)
        assertEquals(108, firstDebris)
        assertEquals(111, secondDebris)

        val impacts =
            listOf(firstDebris to 0, secondDebris to 1).flatMap { (debrisCycle, volley) ->
                DoomRockFollowupRules.timing(8, volley).map {
                    debrisCycle + it.impactOffsetFromDebris
                }
            }
        assertEquals(listOf(110, 111, 112, 113, 114, 115), impacts)
        assertEquals(6, impacts.distinct().size)
        assertEquals(8, DoomRules.ROCK_DEBRIS_COUNT)
    }

    @Test
    fun `a lethally hit larva never resumes pathing before its death callback`() {
        assertTrue(DoomEncounterRuntimeRules.mayAdvanceLarva(hitpoints = 2))
        assertTrue(DoomEncounterRuntimeRules.mayAdvanceLarva(hitpoints = 1))
        assertFalse(DoomEncounterRuntimeRules.mayAdvanceLarva(hitpoints = 0))
    }
}
