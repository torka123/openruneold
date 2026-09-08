package org.rsmod.api.combat.manager

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.CombatStance

class WeaponBasicAttackFxTest {
    @Test
    fun `slash swords use slash except for their lunge stance`() {
        val swords = listOf("obj.arkan_blade", "obj.thunder_khopesh", "obj.deadman_thunder_khopesh")

        for (weapon in swords) {
            assertEquals(
                BasicAttackFx("seq.human_sword_slash", 2500),
                WeaponBasicAttackFx.resolve(weapon, CombatStance.Stance1),
            )
            assertEquals(
                BasicAttackFx("seq.human_sword_slash", 2500),
                WeaponBasicAttackFx.resolve(weapon, CombatStance.Stance2),
            )
            assertEquals(
                BasicAttackFx("seq.human_sword_stab", 2501),
                WeaponBasicAttackFx.resolve(weapon, CombatStance.Stance3),
            )
            assertEquals(
                BasicAttackFx("seq.human_sword_slash", 2500),
                WeaponBasicAttackFx.resolve(weapon, CombatStance.Stance4),
            )
        }
    }

    @Test
    fun `noxious halberds use the matching stab and sweep effects`() {
        for (weapon in listOf("obj.noxious_halberd", "obj.br_noxious_halberd")) {
            assertEquals(
                BasicAttackFx("seq.human_spear_spike", 2562),
                WeaponBasicAttackFx.resolve(weapon, CombatStance.Stance1),
            )
            assertEquals(
                BasicAttackFx("seq.human_scythe_sweep", 2524),
                WeaponBasicAttackFx.resolve(weapon, CombatStance.Stance2),
            )
            assertEquals(
                BasicAttackFx("seq.human_spear_spike", 2562),
                WeaponBasicAttackFx.resolve(weapon, CombatStance.Stance4),
            )
        }
    }

    @Test
    fun `unrelated weapons do not receive an override`() {
        assertNull(WeaponBasicAttackFx.resolve("obj.dragon_scimitar", CombatStance.Stance1))
    }
}
