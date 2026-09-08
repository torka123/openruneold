package org.rsmod.content.skills.prayer.items

import kotlin.test.Test
import kotlin.test.assertEquals

class PrayerUnlockScrollSpecTest {
    @Test
    fun `all five prayer scrolls map to unique persistent unlocks`() {
        assertEquals(listOf(21079, 30626, 21034, 30627, 21047), PRAYER_SCROLL_UNLOCKS.map { it.itemId })
        assertEquals(
            mapOf(
                "Augury" to "varbit.prayer_augury_unlocked",
                "Deadeye" to "varbit.prayer_deadeye_unlocked",
                "Rigour" to "varbit.prayer_rigour_unlocked",
                "Mystic Vigour" to "varbit.prayer_mystic_vigour_unlocked",
                "Preserve" to "varbit.prayer_preserve_unlocked",
            ),
            PRAYER_SCROLL_UNLOCKS.associate { it.prayerName to it.unlockVarbit },
        )
        assertEquals(PRAYER_SCROLL_UNLOCKS.size, PRAYER_SCROLL_UNLOCKS.map { it.itemId }.distinct().size)
        assertEquals(PRAYER_SCROLL_UNLOCKS.size, PRAYER_SCROLL_UNLOCKS.map { it.unlockVarbit }.distinct().size)
    }
}
