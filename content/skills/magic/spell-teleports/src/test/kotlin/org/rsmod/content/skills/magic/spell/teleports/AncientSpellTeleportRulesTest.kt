package org.rsmod.content.skills.magic.spell.teleports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.rsmod.map.CoordGrid

class AncientSpellTeleportRulesTest {
    @Test
    fun `all nine Ancient teleport buttons are registered`() {
        assertEquals(
            listOf(
                "obj.01_zaros_home_tele",
                "obj.54_paddewwa_teleport",
                "obj.60_senntisten_teleport",
                "obj.66_kharyllyl_teleport",
                "obj.72_lassar_teleport",
                "obj.78_dareeyak_teleport",
                "obj.84_carrallagar_teleport",
                "obj.90_annakarl_teleport",
                "obj.96_ghorrock_teleport",
            ),
            AncientSpellTeleportScript.AncientSpellTeleport.entries.map { it.spellObj },
        )
    }

    @Test
    fun `home has the server home destination and other destinations remain cache driven`() {
        val teleports = AncientSpellTeleportScript.AncientSpellTeleport.entries
        assertEquals(CoordGrid(3087, 3495, 0), teleports.first().fixedDestination)
        teleports.drop(1).forEach { assertNull(it.fixedDestination, it.name) }
    }

    @Test
    fun `Ancient teleport uses the player cast and separate teleport graphic sequences`() {
        assertEquals(1979, AncientTeleportVisualRules.PLAYER_ANIMATION_ID)
        assertEquals(392, AncientTeleportVisualRules.SPOTANIM_ID)
        assertEquals(0, AncientTeleportVisualRules.SPOTANIM_HEIGHT)
        assertEquals(3, AncientTeleportVisualRules.CAST_DELAY)
    }
}
