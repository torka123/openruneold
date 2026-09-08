package org.rsmod.content.minigames.corruptedgauntlet

/**
 * Native representation of the 12-by-12 Hunllef floor patterns.
 *
 * These offsets are encounter behaviour data translated from the permitted reference and are
 * deliberately independent of its classes and scheduler. The active map places this grid two tiles
 * inside the 16-by-16 boss-room chunk.
 */
internal object CorruptedGauntletFloorPatterns {
    private const val ARENA_SIZE = 12

    private val easy =
        listOf(
            square(6, 0, 0),
            square(6, 6, 0),
            square(6, 0, 6),
            square(6, 6, 6),
            square(6, 2, 2),
            square(6, 4, 2),
            square(6, 2, 4),
            square(6, 4, 4),
            rectangle(4, 12, 0, 0),
            rectangle(4, 12, 8, 0),
            rectangle(12, 4, 0, 0),
            rectangle(12, 4, 0, 8),
        )

    private val medium =
        listOf(
            rectangle(4, 12, 0, 0) + rectangle(4, 12, 8, 0),
            rectangle(12, 4, 0, 0) + rectangle(12, 4, 0, 8),
        )

    private val hard =
        listOf(
            square(3, 0, 0) + square(3, 9, 0) + square(3, 0, 9) + square(3, 9, 9) + square(4, 4, 4),
            rectangle(2, 12, 0, 0) +
                rectangle(2, 12, 10, 0) +
                rectangle(8, 2, 2, 0) +
                rectangle(8, 2, 2, 10),
            square(4, 1, 1) + square(4, 7, 1) + square(4, 1, 7) + square(4, 7, 7),
            square(4, 0, 0) + square(4, 8, 0) + square(4, 0, 8) + square(4, 8, 8),
        )

    fun select(currentHitpoints: Int, seed: Long, ordinal: Int): Set<Pair<Int, Int>> {
        require(ordinal >= 0)
        val candidates =
            when (CorruptedGauntletRules.hunllefPhase(currentHitpoints)) {
                CorruptedGauntletHunllefPhase.FIRST -> easy
                CorruptedGauntletHunllefPhase.SECOND -> easy + medium
                CorruptedGauntletHunllefPhase.FINAL -> hard
            }
        var mixed = seed xor (ordinal.toLong() * -7_046_029_254_386_353_131L)
        mixed = (mixed xor (mixed ushr 30)) * -4_658_895_280_553_007_687L
        mixed = (mixed xor (mixed ushr 27)) * -7_723_592_293_110_705_685L
        mixed = mixed xor (mixed ushr 31)
        return candidates[Math.floorMod((mixed xor (mixed ushr 32)).toInt(), candidates.size)]
    }

    private fun square(size: Int, offsetX: Int, offsetZ: Int): Set<Pair<Int, Int>> =
        rectangle(size, size, offsetX, offsetZ)

    private fun rectangle(sizeX: Int, sizeZ: Int, offsetX: Int, offsetZ: Int): Set<Pair<Int, Int>> =
        buildSet {
            repeat(sizeX) { x ->
                repeat(sizeZ) { z ->
                    val tile = (offsetX + x) to (offsetZ + z)
                    require(tile.first in 0 until ARENA_SIZE && tile.second in 0 until ARENA_SIZE)
                    add(tile)
                }
            }
        }
}
