@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.minigames.corruptedgauntlet

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CorruptedGauntletRewardTableCacheTest {
    @Test
    fun `success has exactly three common rolls and current shard bounds`() {
        initializeRevision240()

        repeat(250) { seed ->
            val random = Random(seed)
            val rewards =
                CorruptedGauntletChestTable.success(nextInt = random::nextInt, ownsCape = true)
            val shards = rewards.single { it.objId == CorruptedGauntletChestTable.CRYSTAL_SHARD }
            assertTrue(shards.count in 7..12)
            assertFalse(rewards.any { it.objId == CorruptedGauntletChestTable.GAUNTLET_CAPE })
            assertTrue(rewards.size >= 2)
        }
    }

    @Test
    fun `cape is granted only when not already owned and failures never roll uniques`() {
        initializeRevision240()

        val zero = { _: Int -> 0 }
        val success = CorruptedGauntletChestTable.success(zero, ownsCape = false)
        assertNotNull(
            success.singleOrNull { it.objId == CorruptedGauntletChestTable.GAUNTLET_CAPE }
        )

        val partial = CorruptedGauntletChestTable.failure(zero, bossDamaged = true)
        val minimal = CorruptedGauntletChestTable.failure(zero, bossDamaged = false)
        val uniques = CorruptedGauntletUniqueReward.entries.mapTo(hashSetOf()) { it.itemId }
        assertTrue(partial.none { it.objId in uniques })
        assertTrue(minimal.none { it.objId in uniques })
        assertEquals(1, partial.size)
        assertEquals(1, minimal.size)
    }

    private fun initializeRevision240() {
        Revision240GauntletCache.initialize()
    }
}
