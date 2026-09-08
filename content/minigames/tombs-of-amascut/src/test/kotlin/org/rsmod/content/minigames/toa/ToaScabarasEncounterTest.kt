package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToaScabarasEncounterTest {
    @Test
    fun seededSolutionsAreStableCompleteAndPartyAware() {
        val first = ScabarasEncounter(seed = 0x1234L, partySize = 1)
        val copy = ScabarasEncounter(seed = 0x1234L, partySize = 1)
        val other = ScabarasEncounter(seed = 0x5678L, partySize = 1)
        val group = ScabarasEncounter(seed = 0x1234L, partySize = 2)

        assertEquals(first.solutions, copy.solutions)
        assertNotEquals(first.solutions, other.solutions)
        assertEquals(
            setOf(
                ScabarasPuzzle.SEQUENCE,
                ScabarasPuzzle.LIGHTS,
                ScabarasPuzzle.OBELISKS,
                ScabarasPuzzle.ADDITION,
            ),
            first.solutions.sideRooms.toSet(),
        )
        assertEquals(4, first.solutions.sideRooms.size)
        assertEquals(5, first.solutions.initialSequence.size)
        assertEquals(5, first.solutions.initialSequence.toSet().size)
        assertTrue(first.solutions.initialSequence.all { it in 0..8 })
        assertEquals((0..4).toSet(), first.solutions.obeliskOrder.toSet())
        assertTrue(first.solutions.additionTarget in 20..45)
        assertEquals(first.solutions.additionTarget, first.solutions.additionTiles.sum())
        assertTrue(first.solutions.additionTiles.all { it in 1..9 })
        assertEquals((0..8).toSet(), first.solutions.northMatchingSymbols.toSet())
        assertEquals((0..8).toSet(), first.solutions.southMatchingSymbols.toSet())
        assertEquals(4, first.matchingSolvedSymbols.size)
        assertEquals(0, group.matchingSolvedSymbols.size)
        assertFalse(first.lights.all { it })
    }

    @Test
    fun soloFirstRoomUnlocksTheOppositeSecondRoomAndThenMatching() {
        val encounter = ScabarasEncounter(seed = 11L, partySize = 1)
        val rooms = encounter.solutions.sideRooms

        assertTrue(encounter.canAttempt(rooms[0]))
        assertFalse(encounter.canAttempt(rooms[1]))
        assertTrue(encounter.canAttempt(rooms[2]))
        assertFalse(encounter.canAttempt(rooms[3]))
        assertFalse(encounter.canAttempt(ScabarasPuzzle.MATCHING))

        solvePuzzle(encounter, rooms[0])
        assertFalse(encounter.canAttempt(rooms[1]))
        assertTrue(encounter.canAttempt(rooms[3]))
        assertFalse(encounter.canAttempt(ScabarasPuzzle.MATCHING))

        solvePuzzle(encounter, rooms[3])
        assertTrue(encounter.canAttempt(ScabarasPuzzle.MATCHING))
        assertEquals(2, encounter.completedPuzzles.size)
        assertFalse(encounter.isComplete)
    }

    @Test
    fun sequenceFailureDisarmsResetsAndANewSeededRequestCanComplete() {
        val encounter = ScabarasEncounter(seed = 22L, partySize = 1)
        unlock(encounter, ScabarasPuzzle.SEQUENCE)

        val first = encounter.requestSequence()
        assertEquals(ScabarasActionOutcome.PROGRESS, first.outcome)
        assertEquals(encounter.solutions.initialSequence, first.tiles)

        val wrong = (0..8).first { it != first.tiles.first() }
        val failure = encounter.stepSequence(wrong)
        assertEquals(ScabarasActionOutcome.FAILURE, failure.outcome)
        assertTrue(failure.progressReset)
        assertEquals(ScabarasPenalty.SMALL_FALLING_ROCKS, failure.penalty)
        assertEquals(0, encounter.sequenceProgress)
        assertTrue(encounter.currentSequence.isEmpty())
        assertEquals(
            ScabarasActionOutcome.NOT_READY,
            encounter.stepSequence(first.tiles.first()).outcome,
        )

        val retry = encounter.requestSequence()
        assertEquals(5, retry.tiles.size)
        var result: ScabarasActionResult? = null
        retry.tiles.forEach { tile -> result = encounter.stepSequence(tile) }
        assertEquals(ScabarasActionOutcome.PUZZLE_COMPLETED, result?.outcome)
        assertTrue(ScabarasPuzzle.SEQUENCE in encounter.completedPuzzles)
    }

    @Test
    fun lightSolutionTogglesNeighboursAndForcedFlipAlwaysReportsTwentyDamage() {
        val encounter = ScabarasEncounter(seed = 33L, partySize = 1)
        unlock(encounter, ScabarasPuzzle.LIGHTS)

        var result: ScabarasActionResult? = null
        encounter.solutions.lightPresses.forEach { tile -> result = encounter.stepLight(tile) }
        assertEquals(ScabarasActionOutcome.PUZZLE_COMPLETED, result?.outcome)
        assertTrue(encounter.lights.all { it })

        val forced = ScabarasEncounter(seed = 33L, partySize = 1)
        unlock(forced, ScabarasPuzzle.LIGHTS)
        val forcedResult = forced.forceFlipLight(0)
        assertEquals(ScabarasPenalty.FORCED_LIGHT_FLIP, forcedResult.penalty)
        assertEquals(20, forcedResult.fixedDamage)
    }

    @Test
    fun wrongObeliskResetsTheOrderAndReportsTheRoomRockfall() {
        val encounter = ScabarasEncounter(seed = 44L, partySize = 1)
        unlock(encounter, ScabarasPuzzle.OBELISKS)

        val wrong = (encounter.solutions.obeliskOrder.first() + 1) % 5
        val failure = encounter.strikeObelisk(wrong)
        assertEquals(ScabarasActionOutcome.FAILURE, failure.outcome)
        assertTrue(failure.progressReset)
        assertEquals(ScabarasPenalty.OBELISK_ROCKFALL, failure.penalty)
        assertEquals(0, encounter.obeliskProgress)

        var result: ScabarasActionResult? = null
        encounter.solutions.obeliskOrder.forEach { obelisk ->
            result = encounter.strikeObelisk(obelisk)
        }
        assertEquals(ScabarasActionOutcome.PUZZLE_COMPLETED, result?.outcome)
    }

    @Test
    fun additionOvershootResetsAndTheSeededTileSolutionCompletes() {
        val encounter = ScabarasEncounter(seed = 55L, partySize = 1)
        unlock(encounter, ScabarasPuzzle.ADDITION)

        var remaining = encounter.solutions.additionTarget - 1
        while (remaining > 0) {
            val value = remaining.coerceAtMost(9)
            assertEquals(ScabarasActionOutcome.PROGRESS, encounter.stepAddition(value).outcome)
            remaining -= value
        }
        val failure = encounter.stepAddition(2)
        assertEquals(ScabarasActionOutcome.FAILURE, failure.outcome)
        assertTrue(failure.progressReset)
        assertEquals(ScabarasPenalty.SMALL_FALLING_ROCKS, failure.penalty)
        assertEquals(0, encounter.currentAdditionSum)

        var result: ScabarasActionResult? = null
        encounter.solutions.additionTiles.forEach { value ->
            result = encounter.stepAddition(value)
        }
        assertEquals(ScabarasActionOutcome.PUZZLE_COMPLETED, result?.outcome)
    }

    @Test
    fun matchingStartsFourPairsSolvedSoloAndDiagonalRouteCompletesTheChallenge() {
        val solo = ScabarasEncounter(seed = 66L, partySize = 1)
        val group = ScabarasEncounter(seed = 66L, partySize = 8)
        assertEquals(4, solo.matchingSolvedSymbols.size)
        assertEquals(0, group.matchingSolvedSymbols.size)

        unlock(solo, ScabarasPuzzle.MATCHING)
        val unsolved = (0..8).filterNot(solo.matchingSolvedSymbols::contains)
        val northMismatchSymbol = unsolved[0]
        val southMismatchSymbol = unsolved[1]
        val northMismatchTile =
            solo.matchingSymbols(ScabarasMatchingBoard.NORTH).indexOf(northMismatchSymbol)
        val southMismatchTile =
            solo.matchingSymbols(ScabarasMatchingBoard.SOUTH).indexOf(southMismatchSymbol)

        assertEquals(
            ScabarasActionOutcome.PROGRESS,
            solo.revealMatching(ScabarasMatchingBoard.NORTH, northMismatchTile).outcome,
        )
        val mismatch = solo.revealMatching(ScabarasMatchingBoard.SOUTH, southMismatchTile)
        assertEquals(ScabarasActionOutcome.FAILURE, mismatch.outcome)
        assertTrue(mismatch.progressReset)
        assertNull(solo.selectedMatchingSymbol(ScabarasMatchingBoard.NORTH))
        assertNull(solo.selectedMatchingSymbol(ScabarasMatchingBoard.SOUTH))

        var result: ScabarasActionResult? = null
        unsolved.forEach { symbol ->
            val northTile = solo.matchingSymbols(ScabarasMatchingBoard.NORTH).indexOf(symbol)
            val southTile = solo.matchingSymbols(ScabarasMatchingBoard.SOUTH).indexOf(symbol)
            solo.revealMatching(ScabarasMatchingBoard.NORTH, northTile)
            result = solo.revealMatching(ScabarasMatchingBoard.SOUTH, southTile)
        }
        assertEquals(ScabarasActionOutcome.CHALLENGE_COMPLETED, result?.outcome)
        assertTrue(result?.challengeComplete == true)
        assertEquals(
            setOf(
                solo.solutions.sideRooms[0],
                solo.solutions.sideRooms[3],
                ScabarasPuzzle.MATCHING,
            ),
            solo.completedPuzzles,
        )
        assertEquals(9, solo.matchingSolvedSymbols.size)
        assertTrue(solo.isComplete)

        solvePuzzle(group, group.solutions.sideRooms[0])
        solvePuzzle(group, group.solutions.sideRooms[3])
        assertFalse(group.canAttempt(ScabarasPuzzle.MATCHING))
        solvePuzzle(group, group.solutions.sideRooms[2])
        solvePuzzle(group, group.solutions.sideRooms[1])
        assertTrue(group.canAttempt(ScabarasPuzzle.MATCHING))
    }

    @Test
    fun resetRestoresTheOriginalSeededLayoutAndClearsCompletion() {
        val encounter = ScabarasEncounter(seed = 77L, partySize = 1)
        val initialLights = encounter.lights
        val firstRoom = encounter.solutions.sideRooms.first()
        solvePuzzle(encounter, firstRoom)
        assertTrue(encounter.completedPuzzles.isNotEmpty())

        encounter.resetProgress()

        assertTrue(encounter.completedPuzzles.isEmpty())
        assertFalse(encounter.isComplete)
        assertEquals(initialLights, encounter.lights)
        assertEquals(0, encounter.sequenceProgress)
        assertTrue(encounter.currentSequence.isEmpty())
        assertEquals(0, encounter.obeliskProgress)
        assertEquals(0, encounter.currentAdditionSum)
        assertEquals(
            encounter.solutions.initiallySolvedMatchingSymbols,
            encounter.matchingSolvedSymbols,
        )
        assertNull(encounter.selectedMatchingSymbol(ScabarasMatchingBoard.NORTH))
        assertNull(encounter.selectedMatchingSymbol(ScabarasMatchingBoard.SOUTH))
    }

    private fun unlock(encounter: ScabarasEncounter, puzzle: ScabarasPuzzle) {
        if (puzzle == ScabarasPuzzle.MATCHING) {
            val roomOrder =
                if (encounter.partySize == 1) {
                    listOf(0, 3)
                } else {
                    listOf(0, 2, 1, 3)
                }
            roomOrder.forEach { roomIndex ->
                val sidePuzzle = encounter.solutions.sideRooms[roomIndex]
                if (sidePuzzle !in encounter.completedPuzzles) {
                    solvePuzzle(encounter, sidePuzzle)
                }
            }
        } else {
            val roomIndex = encounter.solutions.sideRooms.indexOf(puzzle)
            check(roomIndex >= 0)
            val prerequisiteIndex = mapOf(1 to 2, 3 to 0)[roomIndex]
            prerequisiteIndex?.let { index ->
                val prerequisite = encounter.solutions.sideRooms[index]
                if (prerequisite !in encounter.completedPuzzles) {
                    solvePuzzle(encounter, prerequisite)
                }
            }
        }
        assertTrue(encounter.canAttempt(puzzle), "$puzzle should be available")
    }

    private fun solvePuzzle(
        encounter: ScabarasEncounter,
        puzzle: ScabarasPuzzle,
    ): ScabarasActionResult {
        check(puzzle !in encounter.completedPuzzles)
        unlock(encounter, puzzle)
        var result: ScabarasActionResult? = null
        when (puzzle) {
            ScabarasPuzzle.SEQUENCE -> {
                encounter.requestSequence().tiles.forEach { tile ->
                    result = encounter.stepSequence(tile)
                }
            }
            ScabarasPuzzle.LIGHTS -> {
                encounter.solutions.lightPresses.forEach { tile ->
                    result = encounter.stepLight(tile)
                }
            }
            ScabarasPuzzle.OBELISKS -> {
                encounter.solutions.obeliskOrder.forEach { obelisk ->
                    result = encounter.strikeObelisk(obelisk)
                }
            }
            ScabarasPuzzle.ADDITION -> {
                encounter.solutions.additionTiles.forEach { value ->
                    result = encounter.stepAddition(value)
                }
            }
            ScabarasPuzzle.MATCHING -> {
                val unsolved = (0..8).filterNot(encounter.matchingSolvedSymbols::contains)
                unsolved.forEach { symbol ->
                    val north =
                        encounter.matchingSymbols(ScabarasMatchingBoard.NORTH).indexOf(symbol)
                    val south =
                        encounter.matchingSymbols(ScabarasMatchingBoard.SOUTH).indexOf(symbol)
                    encounter.revealMatching(ScabarasMatchingBoard.NORTH, north)
                    result = encounter.revealMatching(ScabarasMatchingBoard.SOUTH, south)
                }
            }
        }
        return checkNotNull(result)
    }
}
