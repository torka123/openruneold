package org.rsmod.content.bosses.doom

import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DoomBossHudTest {
    @Test
    fun `label includes the current delve for base and deep runs`() {
        assertEquals("Doom of Mokhaiotl (Delve 1)", DoomBossHud.label(1))
        assertEquals("Doom of Mokhaiotl (Delve 8)", DoomBossHud.label(8))
        assertEquals("Doom of Mokhaiotl (Delve 9)", DoomBossHud.label(9))
    }

    @Test
    fun `label rejects invalid delve zero`() {
        assertFailsWith<IllegalArgumentException> { DoomBossHud.label(0) }
    }

    @Test
    fun `normal health HUD is hidden for the complete shield phase`() {
        assertFalse(DoomBossHud.showsNormalHealth(DoomState.SHIELD_TRANSITION))
        assertFalse(DoomBossHud.showsNormalHealth(DoomState.SHIELDED))
        assertTrue(DoomBossHud.showsNormalHealth(DoomState.ACTIVE))
        assertTrue(DoomBossHud.showsNormalHealth(DoomState.MELEE_CHARGE))
        assertTrue(DoomBossHud.showsNormalHealth(DoomState.BURROWING))
    }

    @Test
    fun `shield HUD uses the packet-confirmed blue palette and headbar`() {
        assertEquals("Doom of Mokhaiotl (Shielded)", DoomBossHud.shieldLabel())
        assertEquals("headbar.shield_120", DoomAssets.SHIELD_HEADBAR)
        assertEquals(Color(0, 32, 32), DoomBossHud.SHIELD_STYLE.inner)
        assertEquals(Color(0, 152, 120), DoomBossHud.SHIELD_STYLE.back)
        assertEquals(Color(0, 208, 168), DoomBossHud.SHIELD_STYLE.sliding)
        assertEquals(Color(0, 208, 168), DoomBossHud.SHIELD_STYLE.remaining)
    }
}
