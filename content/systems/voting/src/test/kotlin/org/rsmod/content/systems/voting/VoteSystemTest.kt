package org.rsmod.content.systems.voting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

class VoteSystemTest {
    @Test
    fun `false is Zuk and true is Sol`() {
        assertEquals(VoteBossKind.TZKAL_ZUK, bossFromSecureRoll(false))
        assertEquals(VoteBossKind.SOL_HEREDIT, bossFromSecureRoll(true))
    }

    @Test
    fun `Zuk fireballs use exact ranged and magic caps`() {
        assertEquals(0, zukAttackMaxHit(-1, HitType.Ranged))
        assertEquals(17, zukAttackMaxHit(17, HitType.Ranged))
        assertEquals(28, zukAttackMaxHit(45, HitType.Ranged))
        assertEquals(23, zukAttackMaxHit(45, HitType.Magic))
        assertEquals(ZukAttackProfile(HitType.Ranged, 28), zukAttackProfile(true, 28))
        assertEquals(ZukAttackProfile(HitType.Magic, 23), zukAttackProfile(false, 28))
    }

    @Test
    fun `size seven Zuk is centered on the configured lava tile`() {
        assertEquals(CoordGrid(3083, 3849, 0), centeredNpcAnchor(CoordGrid(3086, 3852, 0), 7))
    }

    @Test
    fun `eastern Zuk bridge removal covers thirty unique revision 240 objects`() {
        assertEquals(30, EASTERN_ZUK_BRIDGE_REMOVALS.size)
        assertEquals(
            30,
            EASTERN_ZUK_BRIDGE_REMOVALS.map { Triple(it.coords, it.shape, it.angle) }
                .distinct()
                .size,
        )
    }

    @Test
    fun `threshold math consumes exact blocks without losing remainder`() {
        assertEquals(0 to 1, thresholdsCreated(99, 1, 100))
        assertEquals(51 to 2, thresholdsCreated(75, 176, 100))
        assertEquals(99 to 0, thresholdsCreated(24, 75, 100))
    }

    @Test
    fun `claim parser requires unique vote ids`() {
        val parser = VoteClaimParser()
        assertEquals(
            listOf("a", "b"),
            parser.parse("{\"success\":true,\"votes\":[{\"id\":\"a\"},{\"voteId\":\"b\"}]}").voteIds,
        )
        assertTrue(
            parser.parse("{\"success\":true,\"count\":2}").error!!.contains("unique claim IDs")
        )
        assertTrue(
            parser.parse("{\"success\":false,\"message\":\"nope\"}").error!!.contains("nope")
        )
    }

    @Test
    fun `state model retains selected boss and ordered queue`() {
        val first = VoteBossEventRecord(thresholdSerial = 4, boss = VoteBossKind.SOL_HEREDIT)
        val second =
            VoteBossEventRecord(thresholdSerial = 5, boss = VoteBossKind.TZKAL_ZUK, arenaIndex = 1)
        val state = VoteGlobalState(queue = mutableListOf(first, second))
        assertEquals(
            listOf(VoteBossKind.SOL_HEREDIT, VoteBossKind.TZKAL_ZUK),
            state.queue.map { it.boss },
        )
        assertEquals(listOf(4L, 5L), state.queue.map { it.thresholdSerial })
        assertEquals(listOf(0, 1), state.queue.map { it.arenaIndex })
    }

    @Test
    fun `restart requeues live events but never resurrects terminal events`() {
        assertTrue(shouldRequeueAfterRestart(VoteBossEventState.QUEUED))
        assertTrue(shouldRequeueAfterRestart(VoteBossEventState.WARNING))
        assertTrue(shouldRequeueAfterRestart(VoteBossEventState.ACTIVE))
        assertEquals(false, shouldRequeueAfterRestart(VoteBossEventState.DEAD))
        assertEquals(false, shouldRequeueAfterRestart(VoteBossEventState.TIMED_OUT))
        assertEquals(false, shouldRequeueAfterRestart(VoteBossEventState.CLEANUP))
    }

    @Test
    fun `multiple unclaimed raffle wins remain independently claimable`() {
        val state = VoteGlobalState()
        state.unclaimedRafflePrizes.getOrPut(42, ::mutableListOf) +=
            RafflePrize(drawId = "first", accountId = 42)
        state.unclaimedRafflePrizes.getOrPut(42, ::mutableListOf) +=
            RafflePrize(drawId = "second", accountId = 42)
        assertEquals(
            listOf("first", "second"),
            state.unclaimedRafflePrizes.getValue(42).map { it.drawId },
        )
    }
}
