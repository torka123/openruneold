package org.rsmod.content.skills.farming

import kotlin.test.Test
import kotlin.test.assertEquals

class ResurrectCropsRulesTest {
    @Test
    fun `resurrection chance scales from fifty to seventy five percent`() {
        assertEquals(50, ResurrectCropsScript.successChance(1))
        assertEquals(50, ResurrectCropsScript.successChance(78))
        assertEquals(61, ResurrectCropsScript.successChance(88))
        assertEquals(75, ResurrectCropsScript.successChance(99))
        assertEquals(75, ResurrectCropsScript.successChance(120))
    }
}
