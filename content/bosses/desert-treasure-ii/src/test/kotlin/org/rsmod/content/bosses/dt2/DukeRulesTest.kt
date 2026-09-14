package org.rsmod.content.bosses.dt2

import kotlin.test.*

class DukeRulesTest {
    @Test
    fun `normal capture has five autos gaze three autos then gas`() {
        val rotation = DukeRotation()
        assertEquals(
            List(5) { DukeAction.STANDARD } +
                DukeAction.GAZE +
                List(3) { DukeAction.STANDARD } +
                DukeAction.GAS,
            List(10) { rotation.next(485, false) },
        )
        assertEquals(8, rotation.standards)
    }

    @Test
    fun `gas threshold changes latch after the next gas and gaze cannot precede gas directly`() {
        val rotation = DukeRotation()
        repeat(10) { rotation.next(485, false) }
        val next = List(20) { rotation.next(100, false) }
        var standards = 0
        val gaps = mutableListOf<Int>()
        for ((i, action) in next.withIndex()) {
            if (action == DukeAction.STANDARD) standards++
            if (action == DukeAction.GAS) {
                gaps += standards
                standards = 0
            }
            if (i > 0 && action == DukeAction.GAS) assertNotEquals(DukeAction.GAZE, next[i - 1])
        }
        assertEquals(5, gaps.first(), "Crossing enrage cannot shorten an already latched interval")
        assertTrue(gaps.drop(1).all { it in 3..4 })
    }

    @Test
    fun `both modes retain separate health thresholds and gas sequences`() {
        assertEquals(5, DukeRules.rate(122, false))
        assertEquals(4, DukeRules.rate(121, false))
        assertEquals(5, DukeRules.rate(441, true))
        assertEquals(4, DukeRules.rate(440, true))
        assertEquals(1, DukeRules.gasCount(243, false))
        assertEquals(2, DukeRules.gasCount(242, false))
        assertEquals(1, DukeRules.gasCount(1290, true))
        assertEquals(4, DukeRules.gasCount(1289, true))
        assertEquals(listOf(0, 3), DukeRules.gasOffsets(2))
        assertEquals(listOf(0, 3, 5, 7), DukeRules.gasOffsets(4))
    }

    @Test
    fun `poison and powder tolerance reaches exactly one wake threshold`() {
        for (awakened in listOf(false, true)) {
            val poison = DukePreparation()
            repeat(DukeRules.poisonDoses(awakened) - 1) {
                assertFalse(poison.feed(awakened, false))
            }
            assertTrue(poison.feed(awakened, false))
            val powder = DukePreparation()
            repeat(if (awakened) 73 else 39) { assertFalse(powder.feed(awakened, true)) }
            assertTrue(powder.feed(awakened, true))
        }
        assertEquals(
            listOf(1, 1, 2, 5, 6, 6),
            listOf(1, 39, 40, 79, 80, 99).map(DukeRules::powderYield),
        )
        assertEquals(
            listOf(3, 4, 4, 5, 5, 6),
            listOf(69, 70, 74, 75, 79, 80).map(DukeRules::saltYield),
        )
    }

    @Test
    fun `pillars dodge slam and gaze while adjacent chip remains possible`() {
        assertTrue(DukeRules.pillarProtected(3034, 6451))
        assertTrue(DukeRules.pillarProtected(3043, 6451))
        assertFalse(DukeRules.pillarProtected(3036, 6451))
        assertFalse(DukeRules.slamArea(3043, 6451))
        assertTrue(DukeRules.icicleArea(3043, 6451))
        assertTrue(DukeRules.slamArea(3042, 6451))
        assertFalse(DukeRules.slamArea(3042, 6450))
        assertFalse(DukeRules.meleeRange(3039, 6447))
    }

    @Test
    fun `vent targeting and preparation rotate on the three by three grid`() {
        assertEquals(3042 to 6450, DukeRules.nearestVent(3043, 6451))
        assertEquals(listOf(3042 to 6442, 3039 to 6446, 3036 to 6450), DukeRules.prepVents(0))
        assertEquals(listOf(3036 to 6442, 3042 to 6446, 3039 to 6450), DukeRules.prepVents(1))
        assertEquals(DukeRules.prepVents(0), DukeRules.prepVents(3))
        assertTrue(DukeRules.ventArea(3043, 6451, 3042 to 6450))
        assertFalse(DukeRules.ventArea(3044, 6451, 3042 to 6450))
    }

    @Test
    fun `counters remain deterministic over long normal and awakened runs`() {
        for (awakened in listOf(false, true)) {
            val a = DukeRotation()
            val b = DukeRotation()
            repeat(2000) { step ->
                val hp = ((if (awakened) 1697 else 485) - step).coerceAtLeast(1)
                assertEquals(a.next(hp, awakened), b.next(hp, awakened))
            }
        }
    }
}
