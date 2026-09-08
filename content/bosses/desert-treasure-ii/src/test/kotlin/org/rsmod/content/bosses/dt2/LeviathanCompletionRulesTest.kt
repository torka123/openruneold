@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.bosses.dt2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.game.inv.InvObj

class LeviathanCompletionRulesTest {
    @Test
    fun `normal encounter entry is reward eligible but awakened debug entry is not`() {
        assertTrue(Dt2Rules.rewardEligibleOnEntry(Dt2Difficulty.NORMAL))
        assertFalse(Dt2Rules.rewardEligibleOnEntry(Dt2Difficulty.AWAKENED))
    }

    @Test
    fun `perfect standard-hit rule uses final damage and impact-time prayer`() {
        assertFalse(
            Dt2Rules.leviathanPerfectStandardFailure(0, bite = false, protectedAtImpact = false)
        )
        assertFalse(
            Dt2Rules.leviathanPerfectStandardFailure(7, bite = false, protectedAtImpact = true)
        )
        assertTrue(
            Dt2Rules.leviathanPerfectStandardFailure(7, bite = false, protectedAtImpact = false)
        )
        assertTrue(
            Dt2Rules.leviathanPerfectStandardFailure(7, bite = true, protectedAtImpact = true)
        )
    }

    @Test
    fun `Unconventional accepts only cache-backed mithril ammunition families`() {
        assertTrue(Dt2Rules.isLeviathanMithrilAmmunition("obj.mithril_arrow"))
        assertTrue(Dt2Rules.isLeviathanMithrilAmmunition("obj.mithril_dart_p"))
        assertTrue(Dt2Rules.isLeviathanMithrilAmmunition("obj.xbows_crossbow_bolts_mithril_p"))
        assertFalse(Dt2Rules.isLeviathanMithrilAmmunition("obj.adamant_arrow"))
        assertFalse(Dt2Rules.isLeviathanMithrilAmmunition(null))
    }

    @Test
    fun `DT2 fee reduction is 75 percent until five successful kills`() {
        assertEquals(25_000, LeviathanDeathRules.discountedGraveFee(100_000, 0))
        assertEquals(25_000, LeviathanDeathRules.discountedGraveFee(100_000, 4))
        assertEquals(100_000, LeviathanDeathRules.discountedGraveFee(100_000, 5))
        assertEquals(125_000, LeviathanDeathRules.discountedGraveFee(900_000, 4))
        assertEquals(500_000, LeviathanDeathRules.discountedGraveFee(900_000, 5))
    }

    @Test
    fun `death-storage codec rejects malformed triples`() {
        val items = listOf(InvObj(995, 250_000, 0), InvObj(385, 2, 17))
        assertEquals(items, LeviathanStorageCodec.decode(LeviathanStorageCodec.encode(items)))
        assertEquals(emptyList(), LeviathanStorageCodec.decode(listOf(995, 0, 0)))
        assertEquals(emptyList(), LeviathanStorageCodec.decode(listOf(995, 1)))
    }
}
