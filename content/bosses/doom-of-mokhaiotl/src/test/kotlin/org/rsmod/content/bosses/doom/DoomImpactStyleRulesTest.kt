package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals

class DoomImpactStyleRulesTest {
    @Test
    fun `normal attack uses the inbound rock style on a matching impact tick`() {
        assertEquals(
            listOf(DoomAttackStyle.RANGED),
            DoomImpactStyleRules.candidates(
                enabledStyles = DoomAttackStyle.entries,
                rockStylesByImpact =
                    listOf(104 to DoomAttackStyle.MAGIC, 105 to DoomAttackStyle.RANGED),
                normalImpactCycle = 105,
            ),
        )
    }

    @Test
    fun `normal attack keeps every enabled style when no rock lands with it`() {
        assertEquals(
            DoomAttackStyle.entries,
            DoomImpactStyleRules.candidates(
                enabledStyles = DoomAttackStyle.entries,
                rockStylesByImpact = listOf(104 to DoomAttackStyle.MAGIC),
                normalImpactCycle = 105,
            ),
        )
    }
}
