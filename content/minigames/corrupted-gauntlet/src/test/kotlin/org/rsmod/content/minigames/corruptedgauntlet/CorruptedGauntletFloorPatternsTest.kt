package org.rsmod.content.minigames.corruptedgauntlet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CorruptedGauntletFloorPatternsTest {
    @Test
    fun `floor selection is deterministic and remains inside the twelve tile arena`() {
        repeat(64) { ordinal ->
            val first = CorruptedGauntletFloorPatterns.select(1_000, 0xC0FFEE, ordinal)
            assertEquals(first, CorruptedGauntletFloorPatterns.select(1_000, 0xC0FFEE, ordinal))
            assertTrue(first.isNotEmpty())
            assertTrue(first.all { (x, z) -> x in 0..11 && z in 0..11 })
        }
    }

    @Test
    fun `final phase patterns preserve safe tiles instead of lighting the whole arena`() {
        repeat(32) { ordinal ->
            val active = CorruptedGauntletFloorPatterns.select(333, 99, ordinal)
            assertTrue(active.size < 144)
            assertTrue(active.size >= 48)
        }
    }

    @Test
    fun `each health phase has multiple stable patterns`() {
        listOf(1_000, 666, 333).forEach { hitpoints ->
            val patterns =
                (0 until 128)
                    .map { CorruptedGauntletFloorPatterns.select(hitpoints, 7, it) }
                    .toSet()
            assertTrue(
                patterns.size > 1,
                "phase at $hitpoints hitpoints must not repeat one layout",
            )
        }
    }
}
