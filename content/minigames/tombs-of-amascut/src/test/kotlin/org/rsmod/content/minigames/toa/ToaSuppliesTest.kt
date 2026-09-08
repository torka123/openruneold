package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToaSuppliesTest {
    @Test
    fun `first checkpoint cannot be claimed twice and second unlocks after four paths`() {
        val claimed = linkedSetOf<Int>()
        val forfeited = linkedSetOf<Int>()

        assertNull(
            ToaSupplyCheckpointRules.availableCheckpoint(
                claimed,
                forfeited,
                completedPathCount = 1,
                allPathsComplete = false,
            )
        )
        assertEquals(
            ToaSupplyCheckpointRules.FIRST,
            ToaSupplyCheckpointRules.availableCheckpoint(
                claimed,
                forfeited,
                completedPathCount = 2,
                allPathsComplete = false,
            ),
        )

        claimed += ToaSupplyCheckpointRules.FIRST
        assertNull(
            ToaSupplyCheckpointRules.availableCheckpoint(
                claimed,
                forfeited,
                completedPathCount = 2,
                allPathsComplete = false,
            )
        )
        assertEquals(
            ToaSupplyCheckpointRules.SECOND,
            ToaSupplyCheckpointRules.availableCheckpoint(
                claimed,
                forfeited,
                completedPathCount = 4,
                allPathsComplete = true,
            ),
        )

        claimed += ToaSupplyCheckpointRules.SECOND
        assertNull(
            ToaSupplyCheckpointRules.availableCheckpoint(
                claimed,
                forfeited,
                completedPathCount = 4,
                allPathsComplete = true,
            )
        )
    }

    @Test
    fun `unclaimed first checkpoint is forfeited after the third path`() {
        val claimed = linkedSetOf<Int>()
        val forfeited = linkedSetOf<Int>()

        assertNull(
            ToaSupplyCheckpointRules.availableCheckpoint(
                claimed,
                forfeited,
                completedPathCount = 3,
                allPathsComplete = false,
            )
        )
        assertEquals(setOf(ToaSupplyCheckpointRules.FIRST), forfeited)
        assertNull(
            ToaSupplyCheckpointRules.availableCheckpoint(
                claimed,
                forfeited,
                completedPathCount = 2,
                allPathsComplete = false,
            )
        )
        assertEquals(
            ToaSupplyCheckpointRules.SECOND,
            ToaSupplyCheckpointRules.availableCheckpoint(
                claimed,
                forfeited,
                completedPathCount = 4,
                allPathsComplete = true,
            ),
        )
    }

    @Test
    fun `ownership classifies current foreign and ordinary objects exactly`() {
        val current = ToaSupplyOwnershipRules.varsForInstance(0x1234L)
        val foreign = ToaSupplyOwnershipRules.varsForInstance(0x5678L)

        assertTrue(ToaSupplyOwnershipRules.isTagged(current))
        assertTrue(ToaSupplyOwnershipRules.isTagged(foreign))
        assertEquals(
            ToaSupplyOwnership.CURRENT_RAID,
            ToaSupplyOwnershipRules.classify(current, current),
        )
        assertEquals(
            ToaSupplyOwnership.FOREIGN_RAID,
            ToaSupplyOwnershipRules.classify(foreign, current),
        )
        assertEquals(ToaSupplyOwnership.UNOWNED, ToaSupplyOwnershipRules.classify(0, current))
    }

    @Test
    fun `discarded foreign token is never restored even if a later token collides`() {
        val reusedToken = ToaSupplyOwnershipRules.varsForInstance(0x1234L)

        assertFalse(ToaSupplyOwnershipRules.canRestoreQuarantined(reusedToken, reusedToken))
        assertFalse(
            ToaSupplyOwnershipRules.canRestoreQuarantined(
                reusedToken,
                ToaSupplyOwnershipRules.varsForInstance(0x5678L),
            )
        )
    }

    @Test
    fun `help tiers use deterministic stochastic rounding and retain one item`() {
        val cases =
            listOf(
                Triple(ToaInvocation.NEED_SOME_HELP, 2, 2 to 1),
                Triple(ToaInvocation.NEED_LESS_HELP, 4, 2 to 1),
                Triple(ToaInvocation.NO_HELP_NEEDED, 15, 2 to 1),
            )

        cases.forEach { (invocation, input, expected) ->
            val invocations = setOf(invocation)
            assertEquals(expected.first, ToaSupplyInvocationRules.scaled(input, invocations, 0.0))
            assertEquals(expected.second, ToaSupplyInvocationRules.scaled(input, invocations, 1.0))
            assertEquals(1, ToaSupplyInvocationRules.scaled(1, invocations, 1.0))
        }
        assertEquals(0, ToaSupplyInvocationRules.scaled(0, emptySet(), 0.0))
        assertEquals(10, ToaSupplyInvocationRules.scaled(10, emptySet(), 1.0))
    }

    @Test
    fun `all challenge items are guarded while an ordinary hammer remains permanent`() {
        assertEquals(setOf(27295, 27296, 27297), ToaSupplies.CHALLENGE_ITEM_IDS)
        assertTrue(ToaSupplies.CHALLENGE_ITEM_IDS.all { it in ToaSupplies.LOCAL_DISCARD_GUARD_IDS })
        assertTrue(ToaSupplies.REVISION_ITEM_IDS.all { it in ToaSupplies.LOCAL_DISCARD_GUARD_IDS })
        assertEquals(2347, ApmekenLiveTuning.HAMMER_ITEM)
        assertTrue(ApmekenLiveTuning.HAMMER_ITEM in ToaSupplies.LOCAL_DISCARD_GUARD_IDS)
        assertFalse(ApmekenLiveTuning.HAMMER_ITEM in ToaSupplies.STALE_SAFE_ITEM_IDS)
    }
}
