package org.rsmod.content.interfaces.prayer.tab

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrayerAccessRulesTest {
    @Test
    fun `chivalry and piety ignore only their quest unlock gate`() {
        assertTrue(isQuestRequirementFreePrayer("varbit.prayer_chivalry"))
        assertTrue(isQuestRequirementFreePrayer("varbit.prayer_piety"))
        assertFalse(isQuestRequirementFreePrayer("varbit.prayer_rigour"))
        assertFalse(isQuestRequirementFreePrayer("varbit.prayer_augury"))
    }
}
