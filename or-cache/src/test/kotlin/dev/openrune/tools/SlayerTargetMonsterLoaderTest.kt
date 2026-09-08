package dev.openrune.tools

import dev.openrune.types.SlayerTargetMonster
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SlayerTargetMonsterLoaderTest {

    @Test
    fun `preserves every task assignment for a shared npc`() {
        val mappings =
            SlayerTargetMonsterLoader.mapTaskIdsByNpc(
                listOf(
                    SlayerTargetMonster(targetId = 4, targets = listOf(100, 101)),
                    SlayerTargetMonster(targetId = 124, targets = listOf(100)),
                )
            )

        assertArrayEquals(intArrayOf(4, 124), mappings.getValue(100))
        assertArrayEquals(intArrayOf(4), mappings.getValue(101))
        assertEquals(2, mappings.size)
    }

    @Test
    fun `deduplicates a repeated task assignment without dropping another task`() {
        val mappings =
            SlayerTargetMonsterLoader.mapTaskIdsByNpc(
                listOf(
                    SlayerTargetMonster(targetId = 10, targets = listOf(200)),
                    SlayerTargetMonster(targetId = 10, targets = listOf(200)),
                    SlayerTargetMonster(targetId = 25, targets = listOf(200)),
                )
            )

        assertArrayEquals(intArrayOf(10, 25), mappings.getValue(200))
    }
}
