package org.rsmod.content.skills.sailing

import kotlin.test.Test
import kotlin.test.assertFalse

class SailingReleaseGateTest {
    @Test
    fun `regular player gameplay remains disabled until acceptance gates pass`() {
        assertFalse(SailingReleaseGate.playerGameplayEnabled)
    }
}
