package org.rsmod.content.minigames.clanhall

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.rsmod.api.net.social.ClanPartyChestEntry

class ClanHallBalloonRulesTest {
    @Test
    fun `stackable prize stays together in one balloon`() {
        val queue =
            ClanHallBalloonPrizeQueue.from(
                listOf(
                    ClanPartyChestEntry(
                        itemId = 995,
                        quantity = 50_000,
                        depositedBy = 1,
                        depositedAtEpochMillis = 1,
                    )
                )
            ) {
                true
            }

        assertEquals(1, queue.remainingBalloonCount)
        assertEquals(ClanHallBalloonPrize(995, 50_000), queue.takeNext())
        assertNull(queue.takeNext())
    }

    @Test
    fun `nonstackable prizes split one item per balloon`() {
        val queue =
            ClanHallBalloonPrizeQueue.from(
                listOf(
                    ClanPartyChestEntry(
                        itemId = 4151,
                        quantity = 3,
                        depositedBy = 1,
                        depositedAtEpochMillis = 1,
                    )
                )
            ) {
                false
            }

        assertEquals(3, queue.remainingBalloonCount)
        assertEquals(List(3) { ClanHallBalloonPrize(4151, 1) }, List(3) { queue.takeNext() })
        assertNull(queue.takeNext())
    }

    @Test
    fun `active balloon IDs map to the exact popped variants`() {
        ClanHallAssets.PARTY_BALLOONS.forEachIndexed { index, active ->
            assertEquals(active, ClanHallBalloonRules.activeId(index))
            assertEquals(active + 8, ClanHallBalloonRules.poppedId(active))
        }
        assertEquals(115, ClanHallBalloonRules.activeId(8))
    }

    @Test
    fun `safe tile selection is stable distinct and capped`() {
        assertEquals(
            listOf(1, 3),
            ClanHallBalloonRules.safeTiles(listOf(1, 1, 2, 3, 4), limit = 2) { it % 2 == 1 },
        )
    }

    @Test
    fun `party drop timing and coffer cost use OSRS contracts`() {
        assertEquals(1_000L, ClanHallBalloonRules.LEVER_COST)
        assertEquals(2, ClanHallBalloonRules.POPPED_DURATION_TICKS)
        assertEquals(500, ClanHallBalloonRules.PRIZE_GROUND_DURATION_TICKS)
    }
}
