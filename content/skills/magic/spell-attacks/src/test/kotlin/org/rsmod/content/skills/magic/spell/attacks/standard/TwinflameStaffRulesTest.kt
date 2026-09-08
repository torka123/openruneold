package org.rsmod.content.skills.magic.spell.attacks.standard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.rsmod.api.config.constants

class TwinflameStaffRulesTest {
    @Test
    fun `auto weakness preserves tier and changes element through wave`() {
        val weaknesses =
            listOf(
                constants.elemental_weakness_wind,
                constants.elemental_weakness_water,
                constants.elemental_weakness_earth,
                constants.elemental_weakness_fire,
            )
        val tiers =
            listOf(
                listOf(
                    "obj.17_wind_bolt",
                    "obj.23_water_bolt",
                    "obj.29_earth_bolt",
                    "obj.35_fire_bolt",
                ),
                listOf(
                    "obj.41_wind_blast",
                    "obj.47_water_blast",
                    "obj.53_earth_blast",
                    "obj.59_fire_blast",
                ),
                listOf(
                    "obj.62_wind_wave",
                    "obj.65_water_wave",
                    "obj.70_earth_wave",
                    "obj.75_fire_wave",
                ),
            )
        for (tier in tiers) {
            for (selected in tier) {
                for ((index, weakness) in weaknesses.withIndex()) {
                    assertEquals(tier[index], TwinflameStaffRules.weaknessSpell(selected, weakness))
                }
            }
        }
    }

    @Test
    fun `surge spells are never auto substituted`() {
        assertNull(
            TwinflameStaffRules.weaknessSpell(
                "obj.81_wind_surge",
                constants.elemental_weakness_fire,
            )
        )
    }

    @Test
    fun `wave spells downgrade to the opposing blast at Royal Titans walls`() {
        assertEquals(
            "obj.47_water_blast",
            TwinflameStaffRules.royalTitansWallSpell(
                "obj.75_fire_wave",
                "npc.rs_loc_attack_fire_hitbox",
            ),
        )
        assertEquals(
            "obj.59_fire_blast",
            TwinflameStaffRules.royalTitansWallSpell(
                "obj.62_wind_wave",
                "npc.rs_loc_attack_ice_hitbox",
            ),
        )
    }

    @Test
    fun `Royal Titans wall downgrade never changes non-wave spells`() {
        assertNull(
            TwinflameStaffRules.royalTitansWallSpell(
                "obj.47_water_blast",
                "npc.rs_loc_attack_fire_hitbox",
            )
        )
        assertNull(TwinflameStaffRules.royalTitansWallSpell("obj.62_wind_wave", "goblin"))
    }

    @Test
    fun `unknown weakness or spell falls back`() {
        assertNull(TwinflameStaffRules.weaknessSpell("obj.17_wind_bolt", -1))
        assertNull(TwinflameStaffRules.weaknessSpell("obj.50_magic_dart", 0))
    }

    @Test
    fun `only bolt blast and wave spells echo`() {
        val strikes =
            listOf(
                "obj.01_wind_strike",
                "obj.05_water_strike",
                "obj.09_earth_strike",
                "obj.13_fire_strike",
            )
        val bolts =
            listOf("obj.17_wind_bolt", "obj.23_water_bolt", "obj.29_earth_bolt", "obj.35_fire_bolt")
        val blasts =
            listOf(
                "obj.41_wind_blast",
                "obj.47_water_blast",
                "obj.53_earth_blast",
                "obj.59_fire_blast",
            )
        val waves =
            listOf("obj.62_wind_wave", "obj.65_water_wave", "obj.70_earth_wave", "obj.75_fire_wave")
        val surges =
            listOf(
                "obj.81_wind_surge",
                "obj.85_water_surge",
                "obj.90_earth_surge",
                "obj.95_fire_surge",
            )
        strikes.forEach { assertFalse(TwinflameStaffRules.echoes(it), it) }
        bolts.forEach { assertTrue(TwinflameStaffRules.echoes(it), it) }
        blasts.forEach { assertTrue(TwinflameStaffRules.echoes(it), it) }
        waves.forEach { assertTrue(TwinflameStaffRules.echoes(it), it) }
        surges.forEach { assertFalse(TwinflameStaffRules.echoes(it), it) }
    }

    @Test
    fun `echo is forty percent rounded down`() {
        assertEquals(0, TwinflameStaffRules.echoDamage(1))
        assertEquals(3, TwinflameStaffRules.echoDamage(9))
        assertEquals(10, TwinflameStaffRules.echoDamage(25))
    }

    @Test
    fun `echo queued from first impact survives until the following game tick`() {
        assertEquals(1, TwinflameStaffRules.ECHO_VISIBLE_DELAY_TICKS)
        assertEquals(2, TwinflameStaffRules.ECHO_IMPACT_QUEUE_DELAY_CYCLES)
    }
}
