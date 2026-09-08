package org.rsmod.content.systems.breach

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

    @Test
    fun `breach roster cannot select the removed wilderness Yama`() {
        assertEquals(
            setOf(
                BreachBossKind.CERBERUS,
                BreachBossKind.CORRUPTED_HUNLLEF,
                BreachBossKind.WHISPERER,
                BreachBossKind.VARDORVIS,
            ),
            BreachBossKind.entries.toSet(),
        )
    }

class BreachRulesTest {
    @Test
    fun `schedule uses strict utc half-hour boundaries`() {
        assertEquals(
            Instant.parse("2026-08-22T10:30:00Z").toEpochMilli(),
            nextHalfHourBoundary(Instant.parse("2026-08-22T10:29:59Z").toEpochMilli()),
        )
        assertEquals(
            Instant.parse("2026-08-22T11:00:00Z").toEpochMilli(),
            nextHalfHourBoundary(Instant.parse("2026-08-22T10:30:00Z").toEpochMilli()),
        )
    }

    @Test
    fun `target count is forty percent clamped to two through eight`() {
        assertEquals(0, targetCount(0))
        assertEquals(1, targetCount(1))
        assertEquals(2, targetCount(2))
        assertEquals(4, targetCount(10))
        assertEquals(8, targetCount(100))
    }

    @Test
    fun `effective damage never counts overkill`() {
        assertEquals(4, effectiveDamage(99, 4))
        assertEquals(0, effectiveDamage(-1, 100))
        assertEquals(25, effectiveDamage(25, 100))
    }

    @Test
    fun `boss hitpoints scale once per attacker and cap at sixty five thousand`() {
        assertEquals(30_000, scaledBreachHitpoints(30_000, 5_000, 65_000, 0))
        assertEquals(35_000, scaledBreachHitpoints(30_000, 5_000, 65_000, 1))
        assertEquals(40_000, scaledBreachHitpoints(30_000, 5_000, 65_000, 2))
        assertEquals(65_000, scaledBreachHitpoints(30_000, 5_000, 65_000, 7))
        assertEquals(65_000, scaledBreachHitpoints(30_000, 5_000, 65_000, 100))
    }

    @Test
    fun `roat rewards guarantee mvp and make non mvp roll proportional`() {
        val entries =
            listOf(
                BreachContribution(1, 11, "mvp", 60, 1, 10),
                BreachContribution(2, 22, "passes", 25, 2, 10),
                BreachContribution(3, 33, "fails", 15, 3, 10),
            )
        val rolls = ArrayDeque(listOf(24L, 15L))
        val result = roatRewardDecisions(entries, 4, 750, { rolls.removeFirst() }, { 1 })
        assertTrue(result.single { it.accountId == 1 }.mvp)
        assertTrue(result.single { it.accountId == 1 }.mainReward)
        assertEquals(240, result.single { it.accountId == 1 }.pkPoints)
        assertTrue(result.single { it.accountId == 2 }.mainReward)
        assertFalse(result.single { it.accountId == 3 }.mainReward)
    }

    @Test
    fun `mvp ties are deterministic and pet rolls are independent`() {
        val entries =
            listOf(
                BreachContribution(9, 90, "later", 50, 2, 5),
                BreachContribution(7, 70, "first", 50, 1, 5),
            )
        assertEquals(7, chooseMvp(entries)?.accountId)
        var petCalls = 0
        val result =
            roatRewardDecisions(
                entries,
                4,
                750,
                { Long.MAX_VALUE },
                {
                    petCalls++
                    0
                },
            )
        assertEquals(2, petCalls)
        assertTrue(result.all(RoatRewardDecision::pet))
    }

    @Test
    fun `basis points are reporting only and do not create old fixed tiers`() {
        assertEquals(100, contributionBasisPoints(1, 100))
        assertEquals(250, contributionBasisPoints(25, 1000))
        assertEquals(500, contributionBasisPoints(1, 20))
    }
}
