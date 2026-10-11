package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class YamaMeteorCaptureTest {
    @Test
    fun `meteor strike applies the documented burn value`() {
        assertEquals(8, YamaRules.METEOR_BURN_VALUE)
    }

    @Test
    fun `meteor distance bands preserve centre edge and safe outcomes`() {
        assertEquals(
            99,
            YamaRules.meteorDamage(movedTiles = 0, distanceFromCentre = 0, currentHitpoints = 99),
        )
        assertEquals(
            99,
            YamaRules.meteorDamage(movedTiles = 1, distanceFromCentre = 1, currentHitpoints = 99),
        )
        assertEquals(
            80,
            YamaRules.meteorDamage(movedTiles = 2, distanceFromCentre = 2, currentHitpoints = 99),
        )
        assertEquals(
            40,
            YamaRules.meteorDamage(movedTiles = 3, distanceFromCentre = 3, currentHitpoints = 99),
        )
        assertEquals(
            0,
            YamaRules.meteorDamage(movedTiles = 4, distanceFromCentre = 4, currentHitpoints = 99),
        )
    }

    @Test
    fun `meteor eligibility stays locked to cast-time positions while damage uses impact position`() {
        val centre = CoordGrid(100, 100, 0)
        val starts =
            mapOf(
                1L to CoordGrid(98, 98, 0),
                2L to CoordGrid(102, 102, 0),
                3L to CoordGrid(103, 100, 0),
            )

        val affected = YamaHazardRules.meteorParticipantsAtCast(centre, starts)

        assertEquals(setOf(1L, 2L), affected.keys)
        assertEquals(
            40,
            YamaRules.meteorDamage(movedTiles = 3, distanceFromCentre = 3, currentHitpoints = 99),
        )
        assertEquals(
            0,
            YamaRules.meteorDamage(movedTiles = 4, distanceFromCentre = 4, currentHitpoints = 99),
        )
    }

    @Test
    fun `meteor area is the complete snapshotted five by five square`() {
        val centre = CoordGrid(100, 100, 0)
        assertTrue(YamaHazardRules.insideSquare(centre, CoordGrid(98, 98, 0), radius = 2))
        assertTrue(YamaHazardRules.insideSquare(centre, CoordGrid(102, 102, 0), radius = 2))
        assertTrue(YamaHazardRules.insideSquare(centre, centre, radius = 2))
        assertFalse(YamaHazardRules.insideSquare(centre, CoordGrid(103, 100, 0), radius = 2))
        assertFalse(YamaHazardRules.insideSquare(centre, CoordGrid(100, 97, 0), radius = 2))
        assertFalse(YamaHazardRules.insideSquare(centre, CoordGrid(100, 100, 1), radius = 2))
    }
}
