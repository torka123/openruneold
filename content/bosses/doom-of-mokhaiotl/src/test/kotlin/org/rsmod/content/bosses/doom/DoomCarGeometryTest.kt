package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class DoomCarGeometryTest {
    @Test
    fun `five by five footprint is centred two tiles from south west`() {
        val southWest = CoordGrid(100, 200, 0)
        val footprint = DoomCarGeometry.footprint(southWest)

        assertEquals(CoordGrid(102, 202, 0), DoomCarGeometry.centre(southWest))
        assertEquals(25, footprint.size)
        assertTrue(CoordGrid(100, 200, 0) in footprint)
        assertTrue(CoordGrid(104, 204, 0) in footprint)
        assertFalse(CoordGrid(105, 204, 0) in footprint)
        assertEquals(
            CoordGrid(108, 208, 0),
            DoomCarGeometry.southWestTarget(CoordGrid(110, 210, 0)),
        )
    }

    @Test
    fun `adjacency is measured from every edge of the five by five footprint`() {
        val southWest = CoordGrid(100, 200, 0)
        assertEquals(0, DoomCarGeometry.distanceToFootprint(southWest, CoordGrid(102, 204, 0)))
        assertEquals(1, DoomCarGeometry.distanceToFootprint(southWest, CoordGrid(105, 202, 0)))
        assertEquals(1, DoomCarGeometry.distanceToFootprint(southWest, CoordGrid(99, 202, 0)))
        assertEquals(1, DoomCarGeometry.distanceToFootprint(southWest, CoordGrid(102, 205, 0)))
        assertEquals(2, DoomCarGeometry.distanceToFootprint(southWest, CoordGrid(106, 202, 0)))
        assertEquals(
            Int.MAX_VALUE,
            DoomCarGeometry.distanceToFootprint(southWest, CoordGrid(102, 205, 1)),
        )
    }

    @Test
    fun `exact move plans use the captured four six and eight tile speed tiers`() {
        val start = CoordGrid(100, 100, 0)
        val eye = CoordGrid(111, 111, 0)

        for (delve in 5..6) {
            val plan = DoomCarGeometry.plan(start, eye, delve)
            assertEquals(4, plan.tilesPerTick, "delve $delve")
            assertEquals(CoordGrid(109, 109, 0), plan.targetSouthWest, "delve $delve")
            assertEquals(listOf(4, 4, 1), plan.segments.map { it.distance }, "delve $delve")
            assertEquals(
                listOf(CoordGrid(104, 104, 0), CoordGrid(108, 108, 0), CoordGrid(109, 109, 0)),
                plan.segments.map { it.endSouthWest },
                "delve $delve",
            )
            assertTrue(
                plan.segments.all { it.clientCycles == DoomCarGeometry.EXACT_MOVE_CLIENT_CYCLES }
            )
        }

        val delveSeven = DoomCarGeometry.plan(start, eye, delve = 7)
        assertEquals(6, delveSeven.tilesPerTick)
        assertEquals(CoordGrid(109, 109, 0), delveSeven.targetSouthWest)
        assertEquals(listOf(6, 3), delveSeven.segments.map { it.distance })
        assertEquals(
            listOf(CoordGrid(106, 106, 0), CoordGrid(109, 109, 0)),
            delveSeven.segments.map { it.endSouthWest },
        )

        for (delve in listOf(8, 9, 15)) {
            val plan = DoomCarGeometry.plan(start, eye, delve)
            assertEquals(8, plan.tilesPerTick, "delve $delve")
            assertEquals(listOf(8, 1), plan.segments.map { it.distance }, "delve $delve")
            assertEquals(
                listOf(CoordGrid(108, 108, 0), CoordGrid(109, 109, 0)),
                plan.segments.map { it.endSouthWest },
                "delve $delve",
            )
        }
    }

    @Test
    fun `plan reaches the eye and identifies every rock crossed along the full zoom`() {
        val start = CoordGrid(100, 100, 0)
        val eye = CoordGrid(120, 102, 0)
        val firstRock = CoordGrid(110, 102, 0)
        val downstreamRock = CoordGrid(114, 102, 0)
        val rocks = listOf(downstreamRock, firstRock)

        val plan = DoomCarGeometry.plan(start, eye, delve = 8)
        val crossed =
            plan.segments.flatMapTo(linkedSetOf()) {
                DoomCarGeometry.intersectedRocks(it, rocks)
            }

        assertEquals(CoordGrid(118, 100, 0), plan.targetSouthWest)
        assertEquals(plan.targetSouthWest, plan.segments.last().endSouthWest)
        assertEquals(setOf(firstRock, downstreamRock), crossed)
    }

    @Test
    fun `a rock inside the first exact move cannot stop the zoom`() {
        val start = CoordGrid(100, 100, 0)
        val eye = CoordGrid(120, 102, 0)
        val immediateRock = CoordGrid(105, 102, 0)

        val plan = DoomCarGeometry.plan(start, eye, delve = 6)

        assertTrue(plan.segments.isNotEmpty())
        assertEquals(plan.targetSouthWest, plan.segments.last().endSouthWest)
        assertTrue(DoomCarGeometry.intersectedRocks(plan.segments.first(), listOf(immediateRock)).isNotEmpty())
    }

    @Test
    fun `trampling the player cannot cancel later exact move segments`() {
        val plan =
            DoomCarGeometry.plan(
                startSouthWest = CoordGrid(100, 100, 0),
                eyeTile = CoordGrid(120, 102, 0),
                delve = 6,
            )

        assertTrue(DoomCarGeometry.tramples(plan.segments.first(), CoordGrid(106, 102, 0)))
        assertTrue(plan.segments.size > 1)
        assertEquals(plan.targetSouthWest, plan.segments.last().endSouthWest)
    }

    @Test
    fun `swept footprint catches tramples and rocks between exact move endpoints`() {
        val segment =
            DoomCarExactSegment(
                startSouthWest = CoordGrid(100, 100, 0),
                endSouthWest = CoordGrid(108, 100, 0),
                clientCycles = 30,
            )
        val hitRock = CoordGrid(111, 104, 0)
        val missedRock = CoordGrid(113, 104, 0)

        assertTrue(DoomCarGeometry.tramples(segment, CoordGrid(106, 103, 0)))
        assertTrue(DoomCarGeometry.tramples(segment, CoordGrid(112, 104, 0)))
        assertFalse(DoomCarGeometry.tramples(segment, CoordGrid(99, 103, 0)))
        assertFalse(DoomCarGeometry.tramples(segment, missedRock))
        assertEquals(
            setOf(hitRock),
            DoomCarGeometry.intersectedRocks(segment, listOf(hitRock, missedRock)),
        )
    }

    @Test
    fun `slam protection requires a rock directionally between Doom and player`() {
        val origin = CoordGrid(102, 102, 0)
        val blocker = CoordGrid(105, 102, 0)

        assertTrue(
            DoomCarGeometry.providesDirectionalCover(origin, blocker, CoordGrid(110, 102, 0))
        )
        assertFalse(
            DoomCarGeometry.providesDirectionalCover(origin, blocker, CoordGrid(104, 102, 0))
        )
        assertFalse(
            DoomCarGeometry.providesDirectionalCover(origin, blocker, CoordGrid(110, 106, 0))
        )
    }

    @Test
    fun `surviving rubble prevents car slam damage in every compass direction`() {
        val bossSouthWest = CoordGrid(100, 100, 0)
        val origin = DoomCarGeometry.centre(bossSouthWest)
        val arena =
            buildList {
                for (x in 84..120) {
                    for (z in 84..120) add(CoordGrid(x, z, 0))
                }
            }
        val directions =
            listOf(
                -1 to -1,
                -1 to 0,
                -1 to 1,
                0 to -1,
                0 to 1,
                1 to -1,
                1 to 0,
                1 to 1,
            )

        directions.forEach { (dx, dz) ->
            val blocker = origin.translate(dx * 3, dz * 3)
            val player = origin.translate(dx * 6, dz * 6)
            val resolution =
                DoomCarGeometry.resolveSlam(
                    bossSouthWest = bossSouthWest,
                    playerTile = player,
                    rocks = listOf(blocker),
                    arenaTiles = arena,
                )

            assertTrue(resolution.playerWithinWave, "wave range $dx,$dz")
            assertTrue(resolution.playerProtected, "rubble cover $dx,$dz")
            assertFalse(resolution.damagesPlayer, "covered damage $dx,$dz")
        }
    }

    @Test
    fun `car slam damages an uncovered player but not one outside the wave`() {
        val bossSouthWest = CoordGrid(100, 100, 0)
        val arena =
            buildList {
                for (x in 84..120) {
                    for (z in 84..120) add(CoordGrid(x, z, 0))
                }
            }
        val exposed =
            DoomCarGeometry.resolveSlam(
                bossSouthWest = bossSouthWest,
                playerTile = CoordGrid(108, 102, 0),
                rocks = emptyList(),
                arenaTiles = arena,
            )
        val outside =
            DoomCarGeometry.resolveSlam(
                bossSouthWest = bossSouthWest,
                playerTile = CoordGrid(120, 120, 0),
                rocks = emptyList(),
                arenaTiles = arena,
            )

        assertTrue(exposed.damagesPlayer)
        assertFalse(outside.playerWithinWave)
        assertFalse(outside.damagesPlayer)
    }

    @Test
    fun `slam destroys exposed rocks and preserves rocks covered by another rock`() {
        val bossSouthWest = CoordGrid(100, 100, 0)
        val front = CoordGrid(105, 102, 0)
        val behind = CoordGrid(109, 102, 0)
        val exposed = CoordGrid(102, 107, 0)
        val outsideRadius = CoordGrid(120, 102, 0)

        val result =
            DoomCarGeometry.resolveSlam(
                bossSouthWest,
                playerTile = CoordGrid(111, 102, 0),
                rocks = listOf(front, behind, exposed, outsideRadius),
            )

        assertNull(result.centreRock)
        assertTrue(result.playerProtected)
        assertEquals(setOf(front, exposed), result.destroyedRocks)
        assertFalse(behind in result.destroyedRocks)
        assertFalse(outsideRadius in result.destroyedRocks)
    }

    @Test
    fun `centre rock fully blocks slam and is the only rock destroyed`() {
        val bossSouthWest = CoordGrid(100, 100, 0)
        val centre = CoordGrid(102, 102, 0)
        val other = CoordGrid(105, 102, 0)

        val result =
            DoomCarGeometry.resolveSlam(
                bossSouthWest,
                playerTile = CoordGrid(110, 108, 0),
                rocks = listOf(centre, other),
            )

        assertEquals(centre, result.origin)
        assertEquals(centre, result.centreRock)
        assertTrue(result.playerProtected)
        assertEquals(setOf(centre), result.destroyedRocks)
    }

    @Test
    fun `deep nine capture emits the exact 306 tile radial slam mask`() {
        val arenaTiles = buildList {
            for (x in 3545..3563) {
                for (z in 6425..6443) add(CoordGrid(x, z, 0))
            }
        }
        val rocks =
            listOf(
                CoordGrid(3549, 6440, 0),
                CoordGrid(3546, 6446, 0),
                CoordGrid(3546, 6444, 0),
                CoordGrid(3550, 6445, 0),
                CoordGrid(3546, 6438, 0),
                CoordGrid(3548, 6439, 0),
                CoordGrid(3546, 6426, 0),
                CoordGrid(3548, 6427, 0),
                CoordGrid(3547, 6429, 0),
                CoordGrid(3550, 6427, 0),
                CoordGrid(3543, 6444, 0),
                CoordGrid(3543, 6438, 0),
            )

        val result =
            DoomCarGeometry.resolveSlam(
                bossSouthWest = CoordGrid(3558, 6426, 0),
                playerTile = CoordGrid(3554, 6431, 0),
                rocks = rocks,
                arenaTiles = arenaTiles,
            )

        assertEquals(CoordGrid(3560, 6428, 0), result.origin)
        assertEquals(306, result.exposedTiles.size)
        assertEquals(13, result.protectedTiles.size)
        assertTrue(CoordGrid(3545, 6425, 0) in result.exposedTiles)
        assertFalse(CoordGrid(3545, 6443, 0) in result.exposedTiles)
        assertTrue(CoordGrid(3548, 6426, 0) in result.protectedTiles)
        assertTrue(CoordGrid(3550, 6427, 0) in result.protectedTiles)
        assertTrue(CoordGrid(3563, 6443, 0) in result.exposedTiles)
    }

    @Test
    fun `slam visual circle excludes only the radius sixteen boundary`() {
        val origin = CoordGrid(100, 100, 0)
        val arena = buildList { for (x in 84..116) for (z in 84..116) add(CoordGrid(x, z, 0)) }
        val mask = DoomCarGeometry.slamMask(origin, emptyList(), arena)

        assertTrue(CoordGrid(115, 100, 0) in mask.candidateTiles)
        assertFalse(CoordGrid(116, 100, 0) in mask.candidateTiles)
        assertFalse(CoordGrid(100, 116, 0) in mask.candidateTiles)
    }

    @Test
    fun `slam propagation delays preserve captured signed client offsets`() {
        val origin = CoordGrid(3560, 6428, 0)

        assertEquals(-2, DoomCarGeometry.slamPropagationClientDelay(origin, origin))
        assertEquals(
            -1,
            DoomCarGeometry.slamPropagationClientDelay(origin, CoordGrid(3559, 6428, 0)),
        )
        assertEquals(
            0,
            DoomCarGeometry.slamPropagationClientDelay(origin, CoordGrid(3560, 6430, 0)),
        )
        assertEquals(
            4,
            DoomCarGeometry.slamPropagationClientDelay(origin, CoordGrid(3554, 6431, 0)),
        )
        assertEquals(
            13,
            DoomCarGeometry.slamPropagationClientDelay(origin, CoordGrid(3545, 6425, 0)),
        )
    }

    @Test
    fun trampleSamplesPlayerTileAtArrivalAndOnlyOncePerZoom() {
        val segment =
            DoomCarExactSegment(
                startSouthWest = CoordGrid(100, 100, 0),
                endSouthWest = CoordGrid(108, 100, 0),
                clientCycles = 30,
            )
        val outsideAtEmission = CoordGrid(99, 103, 0)
        val insideAtArrival = CoordGrid(106, 103, 0)

        assertFalse(
            DoomCarTrampleRules.shouldHitAtArrival(
                segment = segment,
                impactCycle = 101,
                currentCycle = 100,
                playerTile = insideAtArrival,
                alreadyTrampled = false,
            )
        )
        assertFalse(
            DoomCarTrampleRules.shouldHitAtArrival(
                segment = segment,
                impactCycle = 101,
                currentCycle = 101,
                playerTile = outsideAtEmission,
                alreadyTrampled = false,
            )
        )
        assertTrue(
            DoomCarTrampleRules.shouldHitAtArrival(
                segment = segment,
                impactCycle = 101,
                currentCycle = 101,
                playerTile = insideAtArrival,
                alreadyTrampled = false,
            )
        )
        assertFalse(
            DoomCarTrampleRules.shouldHitAtArrival(
                segment = segment,
                impactCycle = 101,
                currentCycle = 101,
                playerTile = insideAtArrival,
                alreadyTrampled = true,
            )
        )

        val nextSegment =
            DoomCarExactSegment(
                startSouthWest = CoordGrid(108, 100, 0),
                endSouthWest = CoordGrid(116, 100, 0),
                clientCycles = 30,
            )
        assertFalse(
            DoomCarTrampleRules.shouldHitAtArrival(
                segment = nextSegment,
                impactCycle = 102,
                currentCycle = 102,
                playerTile = CoordGrid(112, 103, 0),
                alreadyTrampled = true,
            )
        )
        assertTrue(
            DoomCarTrampleRules.shouldHitAtArrival(
                segment = nextSegment,
                impactCycle = 102,
                currentCycle = 102,
                playerTile = CoordGrid(112, 103, 0),
                alreadyTrampled = false,
            )
        )
    }
}
