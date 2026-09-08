package org.rsmod.content.minigames.toa

/**
 * Logical level-zero Scabaras challenge puzzles.
 *
 * This model deliberately contains no cache IDs or map coordinates. Runtime bindings must translate
 * verified revision-240 locs into these logical puzzle inputs.
 */
internal enum class ScabarasPuzzle {
    SEQUENCE,
    LIGHTS,
    OBELISKS,
    ADDITION,
    MATCHING,
}

internal enum class ScabarasMatchingBoard {
    NORTH,
    SOUTH,
}

internal enum class ScabarasActionOutcome {
    PROGRESS,
    MATCHED,
    PUZZLE_COMPLETED,
    CHALLENGE_COMPLETED,
    FAILURE,
    NOT_READY,
    ALREADY_COMPLETED,
}

internal enum class ScabarasPenalty {
    SMALL_FALLING_ROCKS,
    OBELISK_ROCKFALL,
    FORCED_LIGHT_FLIP,
}

internal data class ScabarasActionResult(
    val puzzle: ScabarasPuzzle,
    val outcome: ScabarasActionOutcome,
    val challengeComplete: Boolean,
    val progressReset: Boolean = false,
    val penalty: ScabarasPenalty? = null,
    val fixedDamage: Int = 0,
)

internal data class ScabarasSequenceRequest(
    val outcome: ScabarasActionOutcome,
    val tiles: List<Int>,
)

internal data class ScabarasSolutions(
    /** Order is north-first, north-second, south-first, south-second. */
    val sideRooms: List<ScabarasPuzzle>,
    val initialSequence: List<Int>,
    val lightPresses: List<Int>,
    val obeliskOrder: List<Int>,
    val additionTarget: Int,
    val additionTiles: List<Int>,
    val northMatchingSymbols: List<Int>,
    val southMatchingSymbols: List<Int>,
    val initiallySolvedMatchingSymbols: Set<Int>,
)

/**
 * Seeded, deterministic state machine for the level-zero Path of Scabaras challenge.
 *
 * The two first side rooms are available immediately. Completing a first room unlocks the second
 * room on the opposite lane. In a solo raid either diagonal pair unlocks Matching; groups must
 * complete both diagonal pairs. Matching always completes the challenge.
 */
internal class ScabarasEncounter(private val seed: Long, val partySize: Int) {
    init {
        require(partySize in 1..8) { "Scabaras party size must be between 1 and 8." }
    }

    val solutions: ScabarasSolutions = buildSolutions(seed, partySize)

    private val mutableCompletedPuzzles = linkedSetOf<ScabarasPuzzle>()
    val completedPuzzles: Set<ScabarasPuzzle>
        get() = mutableCompletedPuzzles.toSet()

    val isComplete: Boolean
        get() = ScabarasPuzzle.MATCHING in mutableCompletedPuzzles

    private var sequenceAttempt = 0
    var currentSequence: List<Int> = emptyList()
        private set

    var sequenceProgress: Int = 0
        private set

    private var sequenceArmed = false

    private val initialLightMask =
        solutions.lightPresses.fold(ALL_LIGHTS_MASK) { mask, tile ->
            toggleLightNeighbourhood(mask, tile)
        }
    private var lightMask = initialLightMask
    val lights: List<Boolean>
        get() = (0 until LIGHT_COUNT).map { tile -> lightMask and (1 shl tile) != 0 }

    var obeliskProgress: Int = 0
        private set

    var currentAdditionSum: Int = 0
        private set

    private val mutableMatchingSolvedSymbols =
        solutions.initiallySolvedMatchingSymbols.toMutableSet()
    val matchingSolvedSymbols: Set<Int>
        get() = mutableMatchingSolvedSymbols.toSet()

    private var northMatchingSelection: Int? = null
    private var southMatchingSelection: Int? = null

    fun canAttempt(puzzle: ScabarasPuzzle): Boolean {
        if (isComplete || puzzle in mutableCompletedPuzzles) return false
        if (puzzle == ScabarasPuzzle.MATCHING) {
            return if (partySize == 1) {
                SECOND_ROOM_INDICES.any { solutions.sideRooms[it] in mutableCompletedPuzzles }
            } else {
                solutions.sideRooms.all(mutableCompletedPuzzles::contains)
            }
        }
        val roomIndex = solutions.sideRooms.indexOf(puzzle)
        if (roomIndex == -1) return false
        if (roomIndex in FIRST_ROOM_INDICES) return true
        val prerequisite = OPPOSITE_FIRST_ROOM_BY_SECOND_ROOM[roomIndex] ?: return false
        return solutions.sideRooms[prerequisite] in mutableCompletedPuzzles
    }

    fun resetProgress() {
        mutableCompletedPuzzles.clear()
        sequenceAttempt = 0
        currentSequence = emptyList()
        sequenceProgress = 0
        sequenceArmed = false
        lightMask = initialLightMask
        obeliskProgress = 0
        currentAdditionSum = 0
        mutableMatchingSolvedSymbols.clear()
        mutableMatchingSolvedSymbols.addAll(solutions.initiallySolvedMatchingSymbols)
        northMatchingSelection = null
        southMatchingSelection = null
    }

    fun requestSequence(): ScabarasSequenceRequest {
        if (ScabarasPuzzle.SEQUENCE in mutableCompletedPuzzles) {
            return ScabarasSequenceRequest(
                outcome = ScabarasActionOutcome.ALREADY_COMPLETED,
                tiles = emptyList(),
            )
        }
        if (!canAttempt(ScabarasPuzzle.SEQUENCE)) {
            return ScabarasSequenceRequest(
                outcome = ScabarasActionOutcome.NOT_READY,
                tiles = emptyList(),
            )
        }
        currentSequence = sequenceForAttempt(seed, sequenceAttempt++)
        sequenceProgress = 0
        sequenceArmed = true
        return ScabarasSequenceRequest(
            outcome = ScabarasActionOutcome.PROGRESS,
            tiles = currentSequence,
        )
    }

    fun stepSequence(tile: Int): ScabarasActionResult {
        require(tile in 0 until SEQUENCE_TILE_COUNT)
        unavailable(ScabarasPuzzle.SEQUENCE)?.let {
            return it
        }
        if (!sequenceArmed) {
            return result(ScabarasPuzzle.SEQUENCE, ScabarasActionOutcome.NOT_READY)
        }
        if (tile != currentSequence[sequenceProgress]) {
            sequenceProgress = 0
            sequenceArmed = false
            currentSequence = emptyList()
            return result(
                puzzle = ScabarasPuzzle.SEQUENCE,
                outcome = ScabarasActionOutcome.FAILURE,
                progressReset = true,
                penalty = ScabarasPenalty.SMALL_FALLING_ROCKS,
            )
        }
        sequenceProgress++
        if (sequenceProgress == currentSequence.size) {
            sequenceArmed = false
            return complete(ScabarasPuzzle.SEQUENCE)
        }
        return result(ScabarasPuzzle.SEQUENCE, ScabarasActionOutcome.PROGRESS)
    }

    fun stepLight(tile: Int): ScabarasActionResult {
        require(tile in 0 until LIGHT_COUNT)
        unavailable(ScabarasPuzzle.LIGHTS)?.let {
            return it
        }
        lightMask = toggleLightNeighbourhood(lightMask, tile)
        return if (lightMask == ALL_LIGHTS_MASK) {
            complete(ScabarasPuzzle.LIGHTS)
        } else {
            result(ScabarasPuzzle.LIGHTS, ScabarasActionOutcome.PROGRESS)
        }
    }

    fun forceFlipLight(tile: Int): ScabarasActionResult {
        require(tile in 0 until LIGHT_COUNT)
        unavailable(ScabarasPuzzle.LIGHTS)?.let {
            return it
        }
        lightMask = lightMask xor (1 shl tile)
        if (lightMask == ALL_LIGHTS_MASK) {
            return complete(ScabarasPuzzle.LIGHTS)
                .copy(penalty = ScabarasPenalty.FORCED_LIGHT_FLIP, fixedDamage = FORCED_FLIP_DAMAGE)
        }
        return result(
            puzzle = ScabarasPuzzle.LIGHTS,
            outcome = ScabarasActionOutcome.PROGRESS,
            penalty = ScabarasPenalty.FORCED_LIGHT_FLIP,
            fixedDamage = FORCED_FLIP_DAMAGE,
        )
    }

    fun strikeObelisk(obelisk: Int): ScabarasActionResult {
        require(obelisk in 0 until OBELISK_COUNT)
        unavailable(ScabarasPuzzle.OBELISKS)?.let {
            return it
        }
        if (obelisk != solutions.obeliskOrder[obeliskProgress]) {
            obeliskProgress = 0
            return result(
                puzzle = ScabarasPuzzle.OBELISKS,
                outcome = ScabarasActionOutcome.FAILURE,
                progressReset = true,
                penalty = ScabarasPenalty.OBELISK_ROCKFALL,
            )
        }
        obeliskProgress++
        return if (obeliskProgress == OBELISK_COUNT) {
            complete(ScabarasPuzzle.OBELISKS)
        } else {
            result(ScabarasPuzzle.OBELISKS, ScabarasActionOutcome.PROGRESS)
        }
    }

    fun stepAddition(value: Int): ScabarasActionResult {
        require(value in MIN_ADDITION_TILE..MAX_ADDITION_TILE)
        unavailable(ScabarasPuzzle.ADDITION)?.let {
            return it
        }
        currentAdditionSum += value
        if (currentAdditionSum > solutions.additionTarget) {
            currentAdditionSum = 0
            return result(
                puzzle = ScabarasPuzzle.ADDITION,
                outcome = ScabarasActionOutcome.FAILURE,
                progressReset = true,
                penalty = ScabarasPenalty.SMALL_FALLING_ROCKS,
            )
        }
        return if (currentAdditionSum == solutions.additionTarget) {
            complete(ScabarasPuzzle.ADDITION)
        } else {
            result(ScabarasPuzzle.ADDITION, ScabarasActionOutcome.PROGRESS)
        }
    }

    fun matchingSymbols(board: ScabarasMatchingBoard): List<Int> =
        when (board) {
            ScabarasMatchingBoard.NORTH -> solutions.northMatchingSymbols
            ScabarasMatchingBoard.SOUTH -> solutions.southMatchingSymbols
        }

    fun selectedMatchingSymbol(board: ScabarasMatchingBoard): Int? =
        when (board) {
            ScabarasMatchingBoard.NORTH -> northMatchingSelection
            ScabarasMatchingBoard.SOUTH -> southMatchingSelection
        }

    fun revealMatching(board: ScabarasMatchingBoard, tile: Int): ScabarasActionResult {
        require(tile in 0 until MATCHING_TILE_COUNT)
        unavailable(ScabarasPuzzle.MATCHING)?.let {
            return it
        }

        val symbol = matchingSymbols(board)[tile]
        if (symbol in mutableMatchingSolvedSymbols) {
            return result(ScabarasPuzzle.MATCHING, ScabarasActionOutcome.ALREADY_COMPLETED)
        }

        when (board) {
            ScabarasMatchingBoard.NORTH -> northMatchingSelection = symbol
            ScabarasMatchingBoard.SOUTH -> southMatchingSelection = symbol
        }

        val north = northMatchingSelection
        val south = southMatchingSelection
        if (north == null || south == null) {
            return result(ScabarasPuzzle.MATCHING, ScabarasActionOutcome.PROGRESS)
        }

        northMatchingSelection = null
        southMatchingSelection = null
        if (north != south) {
            return result(
                puzzle = ScabarasPuzzle.MATCHING,
                outcome = ScabarasActionOutcome.FAILURE,
                progressReset = true,
            )
        }

        mutableMatchingSolvedSymbols += north
        return if (mutableMatchingSolvedSymbols.size == MATCHING_SYMBOL_COUNT) {
            complete(ScabarasPuzzle.MATCHING)
        } else {
            result(ScabarasPuzzle.MATCHING, ScabarasActionOutcome.MATCHED)
        }
    }

    private fun unavailable(puzzle: ScabarasPuzzle): ScabarasActionResult? =
        when {
            puzzle in mutableCompletedPuzzles ->
                result(puzzle, ScabarasActionOutcome.ALREADY_COMPLETED)
            !canAttempt(puzzle) -> result(puzzle, ScabarasActionOutcome.NOT_READY)
            else -> null
        }

    private fun complete(puzzle: ScabarasPuzzle): ScabarasActionResult {
        mutableCompletedPuzzles += puzzle
        return result(
            puzzle = puzzle,
            outcome =
                if (isComplete) {
                    ScabarasActionOutcome.CHALLENGE_COMPLETED
                } else {
                    ScabarasActionOutcome.PUZZLE_COMPLETED
                },
        )
    }

    private fun result(
        puzzle: ScabarasPuzzle,
        outcome: ScabarasActionOutcome,
        progressReset: Boolean = false,
        penalty: ScabarasPenalty? = null,
        fixedDamage: Int = 0,
    ): ScabarasActionResult =
        ScabarasActionResult(
            puzzle = puzzle,
            outcome = outcome,
            challengeComplete = isComplete,
            progressReset = progressReset,
            penalty = penalty,
            fixedDamage = fixedDamage,
        )

    private companion object {
        const val SEQUENCE_TILE_COUNT = 9
        const val SEQUENCE_LENGTH = 5
        const val LIGHT_COUNT = 8
        const val ALL_LIGHTS_MASK = (1 shl LIGHT_COUNT) - 1
        const val FORCED_FLIP_DAMAGE = 20
        const val OBELISK_COUNT = 5
        const val MIN_ADDITION_TARGET = 20
        const val MAX_ADDITION_TARGET = 45
        const val MIN_ADDITION_TILE = 1
        const val MAX_ADDITION_TILE = 9
        const val MATCHING_TILE_COUNT = 9
        const val MATCHING_SYMBOL_COUNT = 9
        val FIRST_ROOM_INDICES = setOf(0, 2)
        val SECOND_ROOM_INDICES = setOf(1, 3)
        val OPPOSITE_FIRST_ROOM_BY_SECOND_ROOM = mapOf(1 to 2, 3 to 0)
        const val SOLO_PRE_SOLVED_PAIRS = 4

        const val ROOM_SALT = 0x53434101L
        const val SEQUENCE_SALT = 0x53434102L
        const val LIGHT_SALT = 0x53434103L
        const val OBELISK_SALT = 0x53434104L
        const val ADDITION_SALT = 0x53434105L
        const val NORTH_MATCHING_SALT = 0x53434106L
        const val SOUTH_MATCHING_SALT = 0x53434107L
        const val SOLO_MATCHING_SALT = 0x53434108L

        fun buildSolutions(seed: Long, partySize: Int): ScabarasSolutions {
            val sideRooms =
                shuffled(
                    listOf(
                        ScabarasPuzzle.SEQUENCE,
                        ScabarasPuzzle.LIGHTS,
                        ScabarasPuzzle.OBELISKS,
                        ScabarasPuzzle.ADDITION,
                    ),
                    ScabarasRng(seed xor ROOM_SALT),
                )
            val initialSequence = sequenceForAttempt(seed, 0)
            val lightRng = ScabarasRng(seed xor LIGHT_SALT)
            var lightPresses: List<Int>
            do {
                val pressMask = lightRng.nextInt(ALL_LIGHTS_MASK) + 1
                lightPresses =
                    (0 until LIGHT_COUNT).filter { tile -> pressMask and (1 shl tile) != 0 }
            } while (
                lightPresses.fold(ALL_LIGHTS_MASK) { mask, tile ->
                    toggleLightNeighbourhood(mask, tile)
                } == ALL_LIGHTS_MASK
            )

            val obeliskOrder =
                shuffled((0 until OBELISK_COUNT).toList(), ScabarasRng(seed xor OBELISK_SALT))
            val additionRng = ScabarasRng(seed xor ADDITION_SALT)
            val additionTarget =
                MIN_ADDITION_TARGET +
                    additionRng.nextInt(MAX_ADDITION_TARGET - MIN_ADDITION_TARGET + 1)
            val additionTiles = additionSolution(additionTarget, additionRng)
            val northMatchingSymbols =
                shuffled(
                    (0 until MATCHING_SYMBOL_COUNT).toList(),
                    ScabarasRng(seed xor NORTH_MATCHING_SALT),
                )
            val southMatchingSymbols =
                shuffled(
                    (0 until MATCHING_SYMBOL_COUNT).toList(),
                    ScabarasRng(seed xor SOUTH_MATCHING_SALT),
                )
            val initiallySolvedMatchingSymbols =
                if (partySize == 1) {
                    shuffled(
                            (0 until MATCHING_SYMBOL_COUNT).toList(),
                            ScabarasRng(seed xor SOLO_MATCHING_SALT),
                        )
                        .take(SOLO_PRE_SOLVED_PAIRS)
                        .toSet()
                } else {
                    emptySet()
                }

            return ScabarasSolutions(
                sideRooms = sideRooms,
                initialSequence = initialSequence,
                lightPresses = lightPresses,
                obeliskOrder = obeliskOrder,
                additionTarget = additionTarget,
                additionTiles = additionTiles,
                northMatchingSymbols = northMatchingSymbols,
                southMatchingSymbols = southMatchingSymbols,
                initiallySolvedMatchingSymbols = initiallySolvedMatchingSymbols,
            )
        }

        fun sequenceForAttempt(seed: Long, attempt: Int): List<Int> {
            val attemptSalt = SEQUENCE_SALT + attempt.toLong() * ScabarasRng.GAMMA
            return shuffled(
                    (0 until SEQUENCE_TILE_COUNT).toList(),
                    ScabarasRng(seed xor attemptSalt),
                )
                .take(SEQUENCE_LENGTH)
        }

        fun toggleLightNeighbourhood(mask: Int, tile: Int): Int {
            val previous = (tile + LIGHT_COUNT - 1) % LIGHT_COUNT
            val next = (tile + 1) % LIGHT_COUNT
            return mask xor (1 shl previous) xor (1 shl tile) xor (1 shl next)
        }

        fun additionSolution(target: Int, rng: ScabarasRng): List<Int> {
            val candidates =
                (1 until (1 shl MAX_ADDITION_TILE))
                    .map { mask ->
                        (MIN_ADDITION_TILE..MAX_ADDITION_TILE).filter { value ->
                            mask and (1 shl (value - 1)) != 0
                        }
                    }
                    .filter { values -> values.sum() == target }
            check(candidates.isNotEmpty()) { "No Scabaras addition solution for $target." }
            return shuffled(candidates[rng.nextInt(candidates.size)], rng)
        }

        fun <T> shuffled(values: List<T>, rng: ScabarasRng): List<T> {
            val result = values.toMutableList()
            for (index in result.lastIndex downTo 1) {
                val swapIndex = rng.nextInt(index + 1)
                val value = result[index]
                result[index] = result[swapIndex]
                result[swapIndex] = value
            }
            return result
        }
    }
}

/** Local PRNG avoids JVM/Kotlin random implementation drift in deterministic raid tests. */
private class ScabarasRng(seed: Long) {
    private var state = seed

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 1) % bound.toLong()).toInt()
    }

    private fun nextLong(): Long {
        state += GAMMA
        var value = state
        value = (value xor (value ushr 30)) * MIX_1
        value = (value xor (value ushr 27)) * MIX_2
        return value xor (value ushr 31)
    }

    companion object {
        const val GAMMA = -7046029254386353131L
        private const val MIX_1 = -4658895280553007687L
        private const val MIX_2 = -7723592293110705685L
    }
}
