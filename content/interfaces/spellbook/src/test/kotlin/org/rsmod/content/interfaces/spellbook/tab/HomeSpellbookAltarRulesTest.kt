package org.rsmod.content.interfaces.spellbook.tab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.rsmod.api.combat.commons.magic.AncientMagicksAccessPolicy
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.map.CoordGrid

class HomeSpellbookAltarRulesTest {
    @Test
    fun `home altar uses the verified occult altar and safe Lumbridge tile`() {
        assertEquals(31858, HOME_OCCULT_ALTAR_ID)
        assertEquals(CoordGrid(3223, 3218, 0), HOME_OCCULT_ALTAR_COORDS)
    }

    @Test
    fun `Ancient spellbook client access is quest requirement free`() {
        assertFalse(AncientMagicksAccessPolicy.REQUIRES_DESERT_TREASURE)
        assertEquals(15, AncientMagicksAccessPolicy.CLIENT_UNLOCK_STAGE)
        assertEquals(1, ANCIENT_CLIENT_UNLOCK_DELAY_TICKS)
    }

    @Test
    fun `venerate menu maps every supported spellbook`() {
        assertEquals(Spellbook.Standard, spellbookForAltarChoice(0))
        assertEquals(Spellbook.Ancients, spellbookForAltarChoice(1))
        assertEquals(Spellbook.Lunars, spellbookForAltarChoice(2))
        assertEquals(Spellbook.Arceuus, spellbookForAltarChoice(3))
        assertNull(spellbookForAltarChoice(4))
    }
}
