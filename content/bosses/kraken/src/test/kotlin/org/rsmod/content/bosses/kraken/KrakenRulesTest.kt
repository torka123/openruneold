package org.rsmod.content.bosses.kraken

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.api.instances.InstanceArea

class KrakenRulesTest {
    @Test
    fun `room layout uses the revision 240 Kraken map and exact dormant spawns`() {
        val area = KRAKEN_AREA as InstanceArea.CopyRegions
        assertEquals(listOf(9116), area.regionIds)
        assertEquals(KRAKEN_ENTRY_LOCAL_X, area.enterCoord.localX)
        assertEquals(KRAKEN_ENTRY_LOCAL_Z, area.enterCoord.localZ)
        assertEquals(KRAKEN_PUBLIC_EXIT, area.exitCoord)
        assertEquals(listOf(35 to 50, 35 to 54, 44 to 50, 44 to 54), KRAKEN_TENTACLE_SPAWNS)
    }

    @Test
    fun `entry is at the room front and outside Kraken and tentacle combat space`() {
        val bossDistance =
            KrakenRules.chebyshevDistance(
                KRAKEN_ENTRY_LOCAL_X,
                KRAKEN_ENTRY_LOCAL_Z,
                KRAKEN_BOSS_LOCAL_X,
                KRAKEN_BOSS_LOCAL_Z,
            )
        assertTrue(bossDistance >= 10)
        KRAKEN_TENTACLE_SPAWNS.forEach { (x, z) ->
            assertTrue(
                KrakenRules.chebyshevDistance(KRAKEN_ENTRY_LOCAL_X, KRAKEN_ENTRY_LOCAL_Z, x, z) >=
                    10
            )
        }
    }

    @Test
    fun `awakening and membership cleanup are deterministic`() {
        assertTrue(KrakenRules.canAwaken(KrakenPhase.DORMANT))
        assertFalse(KrakenRules.canAwaken(KrakenPhase.ACTIVE))
        assertFalse(KrakenRules.shouldDiscardRun(42L, 42L))
        assertTrue(KrakenRules.shouldDiscardRun(42L, 43L))
        assertTrue(KrakenRules.shouldDiscardRun(42L, null))
    }

    @Test
    fun `core combat values match the standard Kraken encounter`() {
        assertEquals(255, KrakenRules.BOSS_HITPOINTS)
        assertEquals(120, KrakenRules.TENTACLE_HITPOINTS)
        assertEquals(4, KrakenRules.ATTACK_RATE_TICKS)
        assertEquals(28, KrakenRules.BOSS_MAX_HIT)
        assertEquals(2, KrakenRules.TENTACLE_MAX_HIT)
    }
}
