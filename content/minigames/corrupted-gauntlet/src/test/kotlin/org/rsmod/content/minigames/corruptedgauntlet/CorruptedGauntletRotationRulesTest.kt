package org.rsmod.content.minigames.corruptedgauntlet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CorruptedGauntletRotationRulesTest {
    @Test
    fun `edge and corner room rotations keep every doorway facing the grid`() {
        val layout = CorruptedGauntletLayoutGenerator.generate(42)
        assertEquals(2, layout.room(0, 0).rotation)
        assertEquals(1, layout.room(6, 0).rotation)
        assertEquals(3, layout.room(0, 6).rotation)
        assertEquals(0, layout.room(6, 6).rotation)
        for (axis in 1..5) {
            assertEquals(3, layout.room(0, axis).rotation)
            assertEquals(1, layout.room(6, axis).rotation)
            assertEquals(2, layout.room(axis, 0).rotation)
            assertEquals(0, layout.room(axis, 6).rotation)
        }
    }

    @Test
    fun `starting room rotation opens toward the boss`() {
        repeat(64) { seed ->
            val layout = CorruptedGauntletLayoutGenerator.generate(seed.toLong())
            val expected =
                when {
                    layout.start.z < layout.boss.z -> 0
                    layout.start.x > layout.boss.x -> 1
                    layout.start.z > layout.boss.z -> 2
                    else -> 3
                }
            assertEquals(expected, layout[layout.start].rotation)
        }
    }
}
