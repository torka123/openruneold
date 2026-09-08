package org.rsmod.api.player.worn

import dev.openrune.ServerCacheManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.rsmod.game.inv.InvObj

class NightmareStaffEquipmentChecksTest {
    @Test
    fun `only harmonised Nightmare staff overrides spell speed to four ticks`() {
        val cache = ServerCacheManager.init(240)
        try {
            val harmonised = InvObj("obj.nightmare_staff_harmonised")
            assertTrue(EquipmentChecks.isHarmonisedNightmareStaff(harmonised))
            assertEquals(4, EquipmentChecks.spellAttackRateOverride(harmonised))
            assertEquals(6, EquipmentChecks.spellAttackRateOverride(InvObj("obj.twinflame_staff")))
            assertNull(EquipmentChecks.spellAttackRateOverride(InvObj("obj.nightmare_staff")))
            assertFalse(EquipmentChecks.isHarmonisedNightmareStaff(null))
        } finally {
            cache.close()
        }
    }
}
