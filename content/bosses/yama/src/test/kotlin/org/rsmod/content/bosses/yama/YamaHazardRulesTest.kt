package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class YamaHazardRulesTest {
    @Test
    fun `fire streak fronts telegraph then move at captured running speed`() {
        assertEquals(
            18,
            YamaHazardRules.fireFrontLocalX(17, YamaHazardRules.HorizontalDirection.EAST, 0),
        )
        assertEquals(
            20,
            YamaHazardRules.fireFrontLocalX(17, YamaHazardRules.HorizontalDirection.EAST, 1),
        )
        assertEquals(
            43..44,
            YamaHazardRules.fireFrontTiles(44, 30, YamaHazardRules.HorizontalDirection.WEST, 0),
        )
        assertEquals(
            41..42,
            YamaHazardRules.fireFrontTiles(44, 30, YamaHazardRules.HorizontalDirection.WEST, 1),
        )
    }

    @Test
    fun `distance uses the full five by five Yama footprint`() {
        val southWest = CoordGrid(1500, 10070, 0)
        assertEquals(0, YamaHazardRules.occupiedRectangleDistance(southWest, 5, southWest))
        assertEquals(
            0,
            YamaHazardRules.occupiedRectangleDistance(southWest, 5, southWest.translate(4, 4)),
        )
        assertEquals(
            1,
            YamaHazardRules.occupiedRectangleDistance(southWest, 5, southWest.translate(5, 2)),
        )
        assertEquals(
            Int.MAX_VALUE,
            YamaHazardRules.occupiedRectangleDistance(
                southWest,
                5,
                southWest.translate(2, 2).copy(level = 1),
            ),
        )
    }

    @Test
    fun `south west and south east waves use diagonal capture geometry`() {
        assertEquals(
            0,
            YamaHazardRules.shadowWaveArrivalTick(YamaHazardRules.WaveOrigin.SOUTH_WEST, 18, 22),
        )
        assertEquals(
            1,
            YamaHazardRules.shadowWaveArrivalTick(YamaHazardRules.WaveOrigin.SOUTH_WEST, 19, 23),
        )
        assertEquals(
            1,
            YamaHazardRules.shadowWaveArrivalTick(YamaHazardRules.WaveOrigin.SOUTH_EAST, 40, 23),
        )
        assertEquals(
            120,
            YamaHazardRules.shadowWaveClientDelay(YamaHazardRules.WaveOrigin.SOUTH_WEST, 18, 22),
        )
        assertEquals(
            150,
            YamaHazardRules.shadowWaveClientDelay(YamaHazardRules.WaveOrigin.SOUTH_WEST, 19, 23),
        )
    }

    @Test
    fun `only a real two-step traversal over a moving front is safe`() {
        val start = CoordGrid(1500, 10080, 0)
        val front = setOf(start.translate(1, 0))
        assertEquals(
            YamaHazardRules.MovingFrontCollision.SKIPPED,
            YamaHazardRules.movingFrontCollision(
                listOf(start, start.translate(1, 0), start.translate(2, 0)),
                movementSteps = 2,
                frontTiles = front,
            ),
        )
        assertEquals(
            YamaHazardRules.MovingFrontCollision.HIT,
            YamaHazardRules.movingFrontCollision(
                listOf(start, start.translate(1, 0)),
                movementSteps = 1,
                frontTiles = front,
            ),
        )
        assertEquals(
            YamaHazardRules.MovingFrontCollision.HIT,
            YamaHazardRules.movingFrontCollision(
                listOf(start.translate(-1, 0), start, start.translate(1, 0)),
                movementSteps = 2,
                frontTiles = setOf(start, start.translate(1, 0)),
            ),
        )
        assertEquals(
            YamaHazardRules.MovingFrontCollision.NONE,
            YamaHazardRules.movingFrontCollision(
                listOf(start, start.translate(0, 1), start.translate(0, 2)),
                movementSteps = 2,
                frontTiles = front,
            ),
        )
    }

    @Test
    fun `normal shadow crash is cardinal diagonal opposite cardinal`() {
        assertEquals(
            listOf(
                YamaHazardRules.CrashOrientation.VERTICAL,
                YamaHazardRules.CrashOrientation.DIAGONAL_DESCENDING,
                YamaHazardRules.CrashOrientation.HORIZONTAL,
            ),
            YamaHazardRules.crashSequence(firstVertical = true, sets = 3),
        )
        val centre = CoordGrid(1502, 10085, 0)
        assertEquals(
            setOf(centre, CoordGrid(1502, 10088, 0), CoordGrid(1502, 10082, 0)),
            YamaHazardRules.crashImpactMarkers(centre, YamaHazardRules.CrashOrientation.VERTICAL),
        )
        assertEquals(
            setOf(centre, CoordGrid(1504, 10083, 0), CoordGrid(1500, 10087, 0)),
            YamaHazardRules.crashImpactMarkers(
                centre,
                YamaHazardRules.CrashOrientation.DIAGONAL_DESCENDING,
            ),
        )
    }

    @Test
    fun `one tile perpendicular is the shadow crash dodge`() {
        val centre = CoordGrid(1502, 10085, 0)
        assertTrue(
            YamaHazardRules.isOneTilePerpendicularDodge(
                centre,
                centre.translate(1, 0),
                YamaHazardRules.CrashOrientation.VERTICAL,
            )
        )
        assertFalse(
            YamaHazardRules.isOneTilePerpendicularDodge(
                centre,
                centre.translate(0, 1),
                YamaHazardRules.CrashOrientation.VERTICAL,
            )
        )
        assertTrue(
            YamaHazardRules.isOneTilePerpendicularDodge(
                centre,
                centre.translate(1, 1),
                YamaHazardRules.CrashOrientation.DIAGONAL_DESCENDING,
            )
        )
    }

    @Test
    fun `cardinal bow tie matches captured seven five three widths`() {
        val centre = CoordGrid(1502, 10085, 0)
        val visuals =
            YamaHazardRules.crashShadowVisuals(centre, YamaHazardRules.CrashOrientation.VERTICAL)
        assertEquals(31, visuals.size)
        assertEquals(1, visuals.count { it.clientDelay == 30 })
        assertEquals(6, visuals.count { it.clientDelay == 23 })
        assertEquals(10, visuals.count { it.clientDelay == 15 })
        assertEquals(14, visuals.count { it.clientDelay == 8 })
    }
}
