@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.minigames.corruptedgauntlet

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

class CorruptedGauntletPersistenceTest {
    @Test
    fun `item snapshot round trips item vars and rejects truncated state`() {
        val expected =
            CorruptedGauntletItemSnapshot(
                runId = UUID.randomUUID(),
                returnCoord = CoordGrid(3032, 6127, 1),
                inventory = arrayOf(InvObj(995, 123_456), null, InvObj(4151, 1, 77)),
                equipment = arrayOf(null, InvObj(11802, 1)),
            )
        val encoded = CorruptedGauntletSnapshotCodec.encode(expected)

        assertEquals(expected, CorruptedGauntletSnapshotCodec.decode(encoded))
        assertNull(CorruptedGauntletSnapshotCodec.decode(encoded.dropLast(1)))
        assertNull(CorruptedGauntletSnapshotCodec.decode(mutableListOf(0)))
    }

    @Test
    fun `pending reward round trips and rejects corrupt quantities`() {
        val expected =
            CorruptedGauntletPendingReward(
                UUID.randomUUID(),
                UUID.randomUUID(),
                CorruptedGauntletRewardOutcome.SUCCESS,
                preparationTicks = 500,
                killTicks = 190,
                rewards =
                    listOf(
                        CorruptedGauntletReward(23_866, 9),
                        CorruptedGauntletReward(1_163, 4, noted = true),
                        CorruptedGauntletReward(25_859, 1),
                    ),
            )
        val encoded = CorruptedGauntletPendingRewardCodec.encode(expected)

        assertEquals(expected, CorruptedGauntletPendingRewardCodec.decode(encoded))
        val corrupt = encoded.toMutableList().also { it[it.lastIndex - 1] = 0 }
        assertNull(CorruptedGauntletPendingRewardCodec.decode(corrupt))
        assertNull(CorruptedGauntletPendingRewardCodec.decode(encoded.dropLast(1)))
    }

    @Test
    fun `one run can create only one durable reward`() {
        val run = UUID.randomUUID()
        val other = UUID.randomUUID()

        assertEquals(
            CorruptedGauntletRewardPreparation.NEW,
            CorruptedGauntletPendingRewardRules.preparation(run, null, null),
        )
        assertEquals(
            CorruptedGauntletRewardPreparation.EXISTING_FOR_RUN,
            CorruptedGauntletPendingRewardRules.preparation(run, run, run),
        )
        assertEquals(
            CorruptedGauntletRewardPreparation.ALREADY_FINALIZED,
            CorruptedGauntletPendingRewardRules.preparation(run, null, run),
        )
        assertEquals(
            CorruptedGauntletRewardPreparation.BLOCKED_BY_OTHER_REWARD,
            CorruptedGauntletPendingRewardRules.preparation(run, other, null),
        )
    }
}
