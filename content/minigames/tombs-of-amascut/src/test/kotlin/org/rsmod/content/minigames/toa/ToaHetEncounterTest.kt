package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToaHetEncounterTest {
    @Test
    fun mirrorDirectionsRotateAndReflectFromOnlyTheirExposedFaces() {
        assertEquals(HetMirrorDirection.SOUTH_EAST, HetMirrorDirection.NORTH_EAST.clockwise())
        assertEquals(
            HetMirrorDirection.NORTH_WEST,
            HetMirrorDirection.NORTH_EAST.counterClockwise(),
        )
        assertEquals(HetMirrorDirection.NORTH_EAST, HetMirrorDirection.NORTH_WEST.clockwise())

        assertEquals(HetDirection.NORTH, HetMirrorDirection.NORTH_EAST.reflect(HetDirection.WEST))
        assertEquals(HetDirection.EAST, HetMirrorDirection.NORTH_EAST.reflect(HetDirection.SOUTH))
        assertEquals(HetDirection.SOUTH, HetMirrorDirection.SOUTH_EAST.reflect(HetDirection.WEST))
        assertEquals(HetDirection.EAST, HetMirrorDirection.SOUTH_EAST.reflect(HetDirection.NORTH))
        assertEquals(HetDirection.SOUTH, HetMirrorDirection.SOUTH_WEST.reflect(HetDirection.EAST))
        assertEquals(HetDirection.WEST, HetMirrorDirection.SOUTH_WEST.reflect(HetDirection.NORTH))
        assertEquals(HetDirection.NORTH, HetMirrorDirection.NORTH_WEST.reflect(HetDirection.EAST))
        assertEquals(HetDirection.WEST, HetMirrorDirection.NORTH_WEST.reflect(HetDirection.SOUTH))
        assertNull(HetMirrorDirection.NORTH_EAST.reflect(HetDirection.EAST))
        assertNull(HetMirrorDirection.SOUTH_WEST.reflect(HetDirection.WEST))
    }

    @Test
    fun placementAndPushAreCollisionSafeAndFailedPlacementDoesNotConsumeMirror() {
        val layout =
            openLayout(
                solidTiles = setOf(HetTile(2, 2)),
                barriers = listOf(HetBarrier(HetTile(3, 3), 2)),
                fixedMirrors =
                    listOf(
                        HetMirror(
                            id = 100,
                            tile = HetTile(1, 1),
                            direction = HetMirrorDirection.NORTH_EAST,
                            movable = false,
                        )
                    ),
                movableMirrorIds = setOf(1, 2),
            )
        val encounter = HetEncounter(layout)

        assertFalse(encounter.placeMirror(1, layout.source, HetMirrorDirection.NORTH_EAST))
        assertFalse(encounter.placeMirror(1, HetTile(2, 2), HetMirrorDirection.NORTH_EAST))
        assertFalse(encounter.placeMirror(1, HetTile(3, 3), HetMirrorDirection.NORTH_EAST))
        assertFalse(encounter.placeMirror(1, HetTile(1, 1), HetMirrorDirection.NORTH_EAST))
        assertFalse(encounter.placeMirror(1, HetTile(-1, 1), HetMirrorDirection.NORTH_EAST))
        assertEquals(setOf(1, 2), encounter.availableMirrors())

        assertTrue(encounter.placeMirror(1, HetTile(2, 1), HetMirrorDirection.NORTH_EAST))
        assertTrue(encounter.placeMirror(2, HetTile(3, 1), HetMirrorDirection.SOUTH_EAST))
        assertFalse(encounter.pushMirror(HetTile(2, 1), HetDirection.EAST))
        assertFalse(encounter.pushMirror(HetTile(2, 1), HetDirection.NORTH))
        assertTrue(encounter.pushMirror(HetTile(2, 1), HetDirection.SOUTH))
        assertEquals(1, encounter.mirrorAt(HetTile(2, 0))?.id)
        assertFalse(encounter.pickUpMirror(HetTile(1, 1)))
        assertTrue(encounter.pickUpMirror(HetTile(2, 0)))
        assertEquals(setOf(1), encounter.availableMirrors())
    }

    @Test
    fun beamReflectsButDirtyMirrorsAndUnbrokenBarriersBlockIt() {
        val turnOne = HetTile(2, 1)
        val turnTwo = HetTile(2, 4)
        val barrier = HetTile(3, 4)
        val layout =
            HetLayout(
                bounds = HetBounds(0, 5, 0, 5),
                source = HetTile(0, 1),
                sourceDirection = HetDirection.EAST,
                shield = HetTile(4, 4),
                barriers = listOf(HetBarrier(barrier, 2)),
                movableMirrorIds = setOf(1, 2),
            )
        val encounter = HetEncounter(layout)
        assertTrue(encounter.placeMirror(1, turnOne, HetMirrorDirection.NORTH_WEST))
        assertTrue(encounter.placeMirror(2, turnTwo, HetMirrorDirection.SOUTH_EAST))

        assertEquals(HetBeamEnd.BLOCKED, encounter.traceBeam().end)
        assertEquals(1, encounter.workBarrier(barrier, HetPickaxeTier.BRONZE))
        assertEquals(1, encounter.workBarrier(barrier, HetPickaxeTier.BRONZE))
        assertEquals(HetBeamEnd.HIT_SHIELD, encounter.traceBeam().end)

        assertTrue(encounter.dirtyMirror(turnTwo))
        assertEquals(HetBeamEnd.BLOCKED, encounter.traceBeam().end)
        assertFalse(encounter.dirtyMirror(turnTwo))
        assertTrue(encounter.cleanMirror(turnTwo))
        assertEquals(HetBeamEnd.HIT_SHIELD, encounter.traceBeam().end)
    }

    @Test
    fun shieldHitOpensExactlyTwentyFourTicksAndSealWorkUsesSeededTicket() {
        val encounter = HetEncounter(openLayout())
        assertEquals(130, encounter.sealHitpoints)
        assertEquals(HetBeamEnd.HIT_SHIELD, encounter.fireBeam(100).end)

        assertTrue(encounter.isSealVulnerable(100))
        assertTrue(encounter.isSealVulnerable(123))
        assertFalse(encounter.isSealVulnerable(124))
        assertEquals(0, encounter.workSeal(124, HetPickaxeTier.DRAGON, 99, 0))
        assertEquals(5, encounter.workSeal(100, HetPickaxeTier.BRONZE, 1, 0))
        assertEquals(125, encounter.sealHitpoints)
        assertEquals(7, encounter.workSeal(101, HetPickaxeTier.BRONZE, 1, 2))
    }

    @Test
    fun soloSealHealthIsFixedAndGroupScalingIsAnExplicitHook() {
        var calls = 0
        val scaling = HetGroupSealScaling { partySize ->
            calls++
            130 + (partySize - 1) * 97
        }

        assertEquals(
            130,
            HetEncounter(openLayout(), partySize = 1, groupSealScaling = scaling).sealMaxHitpoints,
        )
        assertEquals(0, calls)
        assertEquals(
            809,
            HetEncounter(openLayout(), partySize = 8, groupSealScaling = scaling).sealMaxHitpoints,
        )
        assertEquals(1, calls)
    }

    @Test
    fun pickaxeAndMiningBandsProduceCurrentSealWorkRanges() {
        assertEquals(HetWorkRange(5, 7), HetSealWork.range(HetPickaxeTier.ADAMANT, 84))
        assertEquals(HetWorkRange(8, 10), HetSealWork.range(HetPickaxeTier.RUNE, 84))
        assertEquals(HetWorkRange(10, 12), HetSealWork.range(HetPickaxeTier.DRAGON, 84))
        assertEquals(HetWorkRange(12, 15), HetSealWork.range(HetPickaxeTier.BRONZE, 85))
        assertEquals(HetWorkRange(15, 17), HetSealWork.range(HetPickaxeTier.GILDED, 98))
        assertEquals(HetWorkRange(17, 19), HetSealWork.range(HetPickaxeTier.CRYSTAL, 98))
        assertEquals(HetWorkRange(14, 16), HetSealWork.range(HetPickaxeTier.STEEL, 99))
        assertEquals(HetWorkRange(17, 19), HetSealWork.range(HetPickaxeTier.RUNE, 120))
        assertEquals(HetWorkRange(19, 21), HetSealWork.range(HetPickaxeTier.THIRD_AGE, 99))
    }

    @Test
    fun higherPickaxeTiersCompleteBarrierWorkInFewerActions() {
        val barrier = HetTile(2, 1)
        val bronze = HetEncounter(openLayout(barriers = listOf(HetBarrier(barrier, 7))))
        val dragon = HetEncounter(openLayout(barriers = listOf(HetBarrier(barrier, 7))))

        assertEquals(1, bronze.workBarrier(barrier, HetPickaxeTier.BRONZE))
        assertEquals(6, bronze.remainingBarrierWork(barrier))
        assertEquals(7, dragon.workBarrier(barrier, HetPickaxeTier.DRAGON))
        assertTrue(dragon.allBarriersBroken())
    }

    @Test
    fun seededLayoutsAreDeterministicAndCarrySolvableOneToThreeMirrorSolutions() {
        for (seed in -100L..100L) {
            val first = HetLayoutGenerator.generate(seed)
            val second = HetLayoutGenerator.generate(seed)
            assertEquals(first, second)
            assertTrue(first.solution.size in 1..3)
            assertEquals(first.solution.size, first.solution.map { it.tile }.distinct().size)
            assertTrue(first.isSolvable(), "seed " + seed + " should have a valid beam solution")
        }
        assertNotEquals(HetLayoutGenerator.generate(0), HetLayoutGenerator.generate(1))
    }

    private fun openLayout(
        solidTiles: Set<HetTile> = emptySet(),
        barriers: List<HetBarrier> = emptyList(),
        fixedMirrors: List<HetMirror> = emptyList(),
        movableMirrorIds: Set<Int> = emptySet(),
    ): HetLayout =
        HetLayout(
            bounds = HetBounds(0, 4, 0, 4),
            source = HetTile(0, 2),
            sourceDirection = HetDirection.EAST,
            shield = HetTile(4, 2),
            solidTiles = solidTiles,
            barriers = barriers,
            fixedMirrors = fixedMirrors,
            movableMirrorIds = movableMirrorIds,
        )
}
