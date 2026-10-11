package org.rsmod.content.bosses.cerberus

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CerberusDecisionTest {
    @Test
    fun `full schedule and RNG calls respect exact HP boundaries through attack 35`() {
        val triples = setOf(1, 11, 21, 31)
        val souls = setOf(7, 14, 28, 35)
        val lava = setOf(5, 10, 15, 20, 25, 30)
        for (hp in listOf(600, 400, 399, 200, 199, 1)) {
            var calls = 0
            for (turn in 1..35) {
                val expected = when {
                    turn in triples -> CerberusAttack.TRIPLE
                    hp < 400 && turn in souls -> CerberusAttack.SOULS
                    hp < 200 && turn in lava -> CerberusAttack.LAVA
                    else -> CerberusAttack.BASIC
                }
                assertEquals(expected, CerberusRules.selectAttack(turn, hp) { bound ->
                    calls++
                    assertEquals(10, bound)
                    1
                }, "hp=$hp attack=$turn")
            }
            assertEquals(when { hp < 200 -> 10; hp < 400 -> 4; else -> 0 }, calls)
        }
    }

    @Test
    fun `priority collision consumes one skip and cannot fall through to lava`() {
        var calls = 0
        val skip: (Int) -> Int = { calls++; 0 }
        assertEquals(CerberusAttack.TRIPLE, CerberusRules.selectAttack(21, 1, nextSkipRoll = skip))
        assertEquals(0, calls)
        assertEquals(CerberusAttack.BASIC, CerberusRules.selectAttack(35, 1, nextSkipRoll = skip))
        assertEquals(1, calls)
    }

    @Test
    fun `unrelated decisions preserve the seeded random sequence`() {
        val actual = Random(5862)
        val expected = Random(5862)
        for (turn in listOf(1, 2, 3, 4, 6, 8, 9, 11, 21, 31)) {
            CerberusRules.selectAttack(turn, 199) { actual.nextInt(it) }
        }
        assertEquals(expected.nextInt(), actual.nextInt())
        CerberusRules.selectAttack(35, 199, forcedSkip = false) { error("Forced skip consumed RNG") }
        CerberusRules.selectAttack(35, 199, forcedAttack = CerberusAttack.TRIPLE) { error("Forced attack consumed RNG") }
    }

    @Test
    fun `all skip outcomes are explicit and bounded`() {
        for (turn in listOf(5, 7, 35)) {
            val outcomes = (0..9).map { roll -> CerberusRules.selectAttack(turn, 1) { roll } }
            assertEquals(1, outcomes.count { it == CerberusAttack.BASIC })
            assertEquals(9, outcomes.count { it == if (turn == 5) CerberusAttack.LAVA else CerberusAttack.SOULS })
        }
    }

    @Test
    fun `complete lava protection matrix includes overlapping potion state`() {
        // Index bits: shield, prayer, regular antifire, super antifire.
        val expected = listOf(15, 12, 15, 12, 15, 12, 12, 12, 11, 11, 11, 11, 11, 11, 11, 11)
        expected.forEachIndexed { mask, maximum ->
            assertEquals(maximum, CerberusRules.lavaMaximumDamage(
                mask and 1 != 0, mask and 2 != 0, mask and 4 != 0, mask and 8 != 0,
            ), "protection mask=$mask")
        }
    }

    @Test
    fun `lava chooses exactly three unique tiles without retries or fallback`() {
        val legal = listOf(10, 11, 12, 13, 14, 14)
        for (first in 0..3) for (second in 0..2) {
            val bounds = mutableListOf<Int>()
            val picks = ArrayDeque(listOf(first, second))
            val tiles = CerberusRules.lavaTiles(12, legal) { bound ->
                bounds += bound
                picks.removeFirst()
            }
            assertEquals(listOf(4, 3), bounds)
            assertEquals(12, tiles.first())
            assertEquals(3, tiles.toSet().size)
            assertTrue(tiles.all { it in legal })
        }
        assertFailsWith<IllegalArgumentException> {
            CerberusRules.lavaTiles(12, listOf(12, 13, 13)) { error("Invalid catalog consumed RNG") }
        }
    }

    @Test
    fun `all footprint cells and surrounding cardinal and diagonal cells use nearest edge`() {
        val footprint = (22..26).flatMap { x -> (34..38).map { z -> x to z } }
        for (x in 0..63) for (z in 0..63) {
            val nearest = footprint.minOf { (bx, bz) -> maxOf(kotlin.math.abs(x - bx), kotlin.math.abs(z - bz)) }
            assertEquals(nearest, CerberusRules.distanceToFootprint(22, 34, 5, x, z))
            if (nearest == 0) assertTrue(!CerberusRules.isLavaTileLegal(x, z))
        }
    }
}
