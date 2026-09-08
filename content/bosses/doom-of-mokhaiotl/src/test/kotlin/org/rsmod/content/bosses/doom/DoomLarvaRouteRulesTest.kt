package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoomLarvaRouteRulesTest {
    @Test
    fun `larva reaches Doom only after entering the boss footprint`() {
        assertFalse(DoomLarvaRouteRules.hasReachedBoss(distanceToBoss = 2))
        assertFalse(DoomLarvaRouteRules.hasReachedBoss(distanceToBoss = 1))
        assertTrue(DoomLarvaRouteRules.hasReachedBoss(distanceToBoss = 0))
    }

    @Test
    fun `route must successfully end inside the boss footprint`() {
        assertTrue(
            DoomLarvaRouteRules.accept(success = true, waypointCount = 1, finalDistanceToBoss = 0)
        )
        assertFalse(
            DoomLarvaRouteRules.accept(success = false, waypointCount = 3, finalDistanceToBoss = 0)
        )
        assertFalse(
            DoomLarvaRouteRules.accept(success = true, waypointCount = 0, finalDistanceToBoss = 0)
        )
        assertFalse(
            DoomLarvaRouteRules.accept(success = true, waypointCount = 3, finalDistanceToBoss = 1)
        )
    }
}
