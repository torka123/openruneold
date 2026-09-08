package org.rsmod.content.minigames.corruptedgauntlet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CorruptedGauntletCompletionVarpTest {
    @Test
    fun `normal and corrupted completion counters use distinct native varps`() {
        assertEquals(2_353, CorruptedGauntletConfig.NORMAL_COMPLETION_VARP)
        assertEquals(2_354, CorruptedGauntletConfig.CORRUPTED_COMPLETION_VARP)
    }
}
