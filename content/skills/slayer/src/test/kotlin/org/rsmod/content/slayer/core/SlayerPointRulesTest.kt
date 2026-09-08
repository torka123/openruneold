package org.rsmod.content.slayer.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SlayerPointRulesTest {
    @Test
    fun `every master has the intended base point award`() {
        val expected =
            mapOf(
                1 to 0,
                2 to 6,
                3 to 8,
                4 to 10,
                5 to 15,
                6 to 12,
                7 to 25,
                8 to 18,
                9 to 0,
                10 to 0,
            )
        expected.forEach { (masterId, points) ->
            assertEquals(points, SlayerPointRules.basePoints(masterId), "master $masterId")
        }
    }

    @Test
    fun `the first five completed assignments award no points`() {
        for (total in 1..5) {
            assertEquals(0, SlayerPointRules.pointsForCompletedTask(5, streak = total, totalTasksDone = total))
            assertEquals(0, SlayerPointRules.pointsForCompletedTask(7, streak = total, totalTasksDone = total))
        }
    }

    @Test
    fun `normal and milestone awards use exact OSRS multipliers`() {
        val milestones = mapOf(1 to 1, 10 to 5, 50 to 15, 100 to 25, 250 to 35, 1_000 to 50)
        val paidMasters = mapOf(2 to 6, 3 to 8, 4 to 10, 5 to 15, 6 to 12, 7 to 25, 8 to 18)
        paidMasters.forEach { (masterId, base) ->
            milestones.forEach { (streak, multiplier) ->
                assertEquals(
                    base * multiplier,
                    SlayerPointRules.pointsForCompletedTask(masterId, streak, totalTasksDone = 1_005),
                    "master=$masterId streak=$streak",
                )
            }
        }
    }

    @Test
    fun `zero point masters never receive milestone points`() {
        listOf(1, 9, 10).forEach { masterId ->
            assertEquals(0, SlayerPointRules.pointsForCompletedTask(masterId, 1_000, 1_005))
        }
    }
}
