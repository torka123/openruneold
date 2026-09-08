package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.rsmod.map.CoordGrid

class DoomRockTargetingTest {
    @Test
    fun `large boulder splits halfway from snapshotted player tile toward boss centre`() {
        val boss = CoordGrid(100, 100, 0)

        assertEquals(
            CoordGrid(105, 105, 0),
            DoomRockTargeting.splitTile(CoordGrid(110, 110, 0), boss),
        )
        assertEquals(
            CoordGrid(105, 100, 0),
            DoomRockTargeting.splitTile(CoordGrid(110, 100, 0), boss),
        )
    }

    @Test
    fun nullablePlannerPreservesCompleteDeterministicPlans() {
        val player = CoordGrid(100, 100, 0)
        val candidates = (1..20).map { CoordGrid(100 + it, 100, 0) }

        val nullablePlan = DoomRockTargeting.tryPlan(player, candidates)
        val strictPlan = DoomRockTargeting.plan(player, candidates)

        requireNotNull(nullablePlan)
        assertEquals(strictPlan, nullablePlan)
        assertEquals(player, nullablePlan.target)
        assertEquals(DoomRules.ROCK_DEBRIS_COUNT, nullablePlan.tiles.size)
        assertEquals(
            listOf(player) + candidates.take(DoomRules.ROCK_DEBRIS_COUNT - 1),
            nullablePlan.tiles,
        )
    }

    @Test
    fun nullablePlannerReturnsNullWhenUniqueTargetsAreInsufficient() {
        val player = CoordGrid(100, 100, 0)
        val duplicate = CoordGrid(101, 100, 0)

        val plan =
            DoomRockTargeting.tryPlan(
                playerAtDetonation = player,
                candidates = listOf(duplicate, duplicate, CoordGrid(102, 100, 1)),
                debrisCount = 3,
            )

        assertNull(plan)
    }

    @Test
    fun nullablePlannerReturnsNullWhenReservationsExhaustValidTargets() {
        val player = CoordGrid(100, 100, 0)
        val first = CoordGrid(101, 100, 0)
        val second = CoordGrid(102, 100, 0)

        val plan =
            DoomRockTargeting.tryPlan(
                playerAtDetonation = player,
                candidates = listOf(first, second),
                reserved = setOf(player, first),
                debrisCount = 2,
            )

        assertNull(plan)
    }
}
