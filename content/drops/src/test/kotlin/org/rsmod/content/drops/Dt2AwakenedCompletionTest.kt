package org.rsmod.content.drops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.api.droptable.NpcKillRewardContext

class Dt2AwakenedCompletionTest {
    @Test
    fun `kit is awarded once only when eligible awakened Leviathan is the last boss`() {
        var state = Dt2AwakenedCompletionState()
        listOf(Dt2AwakenedBoss.VARDORVIS, Dt2AwakenedBoss.WHISPERER, Dt2AwakenedBoss.DUKE)
            .forEachIndexed { index, boss ->
                val update = Dt2AwakenedCompletionRules.record(state, boss, context("boss-$index"))
                assertFalse(update.awardKit)
                state = update.state
            }

        val leviathan =
            Dt2AwakenedCompletionRules.record(
                state,
                Dt2AwakenedBoss.LEVIATHAN,
                context("leviathan-first"),
            )
        val repeated =
            Dt2AwakenedCompletionRules.record(
                leviathan.state,
                Dt2AwakenedBoss.LEVIATHAN,
                context("leviathan-second"),
            )

        assertTrue(leviathan.awardKit)
        assertTrue(leviathan.state.kitClaimed)
        assertFalse(repeated.awardKit)
        assertEquals(1, repeated.state.recentEventIds.count { it == "leviathan-second" })
    }

    @Test
    fun `the fourth boss can be any eligible awakened DT2 boss`() {
        var state =
            Dt2AwakenedCompletionRules.record(
                    Dt2AwakenedCompletionState(),
                    Dt2AwakenedBoss.LEVIATHAN,
                    context("leviathan-early"),
                )
                .state
        var finalAward = false
        Dt2AwakenedBoss.entries
            .filterNot { it == Dt2AwakenedBoss.LEVIATHAN }
            .forEachIndexed { index, boss ->
                val update = Dt2AwakenedCompletionRules.record(state, boss, context("later-$index"))
                state = update.state
                finalAward = update.awardKit
            }

        assertEquals(Dt2AwakenedBoss.entries.toSet(), state.completed)
        assertTrue(state.kitClaimed)
        assertTrue(finalAward)
    }

    @Test
    fun `ineligible normal and duplicate events cannot advance completion`() {
        val initial = Dt2AwakenedCompletionState()
        val ineligible =
            Dt2AwakenedCompletionRules.record(
                initial,
                Dt2AwakenedBoss.VARDORVIS,
                context("debug", rewardEligible = false),
            )
        val normal =
            Dt2AwakenedCompletionRules.record(
                initial,
                Dt2AwakenedBoss.VARDORVIS,
                context("normal", awakened = false),
            )
        val first =
            Dt2AwakenedCompletionRules.record(initial, Dt2AwakenedBoss.VARDORVIS, context("one"))
        val duplicate =
            Dt2AwakenedCompletionRules.record(
                first.state,
                Dt2AwakenedBoss.VARDORVIS,
                context("one"),
            )

        assertFalse(ineligible.changed)
        assertFalse(normal.changed)
        assertFalse(duplicate.changed)
        assertEquals(1, duplicate.state.completed.size)
    }

    @Test
    fun `eligible awakened completion requires a stable event id`() {
        assertFailsWith<IllegalArgumentException> {
            Dt2AwakenedCompletionRules.record(
                Dt2AwakenedCompletionState(),
                Dt2AwakenedBoss.DUKE,
                context(eventId = null),
            )
        }
    }

    @Test
    fun `persistent codec round trips completion and claim guard`() {
        val state =
            Dt2AwakenedCompletionState(
                completed = setOf(Dt2AwakenedBoss.DUKE, Dt2AwakenedBoss.LEVIATHAN),
                kitClaimed = true,
                recentEventIds = listOf("run:one", "run|two"),
            )

        assertEquals(
            state,
            Dt2AwakenedCompletionCodec.decode(Dt2AwakenedCompletionCodec.encode(state)),
        )
        assertEquals(
            "dt2_awakened_completion_v1",
            Dt2AwakenedCompletionAttributes.STATE.persistenceKey,
        )
        assertFailsWith<IllegalArgumentException> { Dt2AwakenedCompletionCodec.decode("bad") }
    }

    private fun context(
        eventId: String?,
        awakened: Boolean = true,
        rewardEligible: Boolean = true,
    ) =
        NpcKillRewardContext(
            rewardEligible = rewardEligible,
            awakened = awakened,
            perfectKill = false,
            eventId = eventId,
        )
}
