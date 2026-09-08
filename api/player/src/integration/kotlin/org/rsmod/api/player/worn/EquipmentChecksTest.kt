package org.rsmod.api.player.worn

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.inv.InvObj

class EquipmentChecksIntegrationTest {
    @Test
    fun GameTestState.`barrows sets accept usable states and reject broken pieces`() =
        runBasicGameTest {
            val states = listOf("", "_100", "_75", "_50", "_25")
            val brothers = listOf("ahrim", "dharok", "guthan", "karil", "torag", "verac")
            for (brother in brothers) {
                for (state in states) {
                    val helm = InvObj("obj.barrows_${brother}_head$state")
                    val body = InvObj("obj.barrows_${brother}_body$state")
                    val legs = InvObj("obj.barrows_${brother}_legs$state")
                    val weapon = InvObj("obj.barrows_${brother}_weapon$state")
                    assertTrue(isSet(brother, helm, body, legs, weapon), "$brother$state")
                }
                assertFalse(
                    isSet(
                        brother,
                        InvObj("obj.barrows_${brother}_head_broken"),
                        InvObj("obj.barrows_${brother}_body"),
                        InvObj("obj.barrows_${brother}_legs"),
                        InvObj("obj.barrows_${brother}_weapon"),
                    ),
                    "$brother broken helm",
                )
            }
        }

    @Test
    fun GameTestState.`amulet of the damned remains active while degraded`() = runBasicGameTest {
        assertTrue(EquipmentChecks.isAmuletOfTheDamned(InvObj("obj.damned_amulet")))
        assertTrue(EquipmentChecks.isAmuletOfTheDamned(InvObj("obj.damned_amulet_degraded")))
        assertFalse(EquipmentChecks.isAmuletOfTheDamned(null))
    }

    @Test
    fun GameTestState.`moon sets accept active degraded and bounty realm equivalents`() =
        runBasicGameTest {
            assertTrue(
                EquipmentChecks.isBloodMoonSet(
                    InvObj("obj.blood_moon_helm_degraded"),
                    InvObj("obj.blood_moon_chestplate_degraded"),
                    InvObj("obj.blood_moon_tassets_degraded"),
                    InvObj("obj.dual_macuahuitl"),
                ),
            )
            assertTrue(
                EquipmentChecks.isBlueMoonSet(
                    InvObj("obj.br_frost_moon_helm"),
                    InvObj("obj.br_frost_moon_chestplate"),
                    InvObj("obj.br_frost_moon_tassets"),
                    InvObj("obj.br_frostmoon_spear"),
                ),
            )
            assertTrue(
                EquipmentChecks.isEclipseMoonSet(
                    InvObj("obj.eclipse_moon_helm"),
                    InvObj("obj.eclipse_moon_chestplate"),
                    InvObj("obj.eclipse_moon_tassets"),
                    InvObj("obj.eclipse_atlatl"),
                ),
            )
            assertFalse(
                EquipmentChecks.isEclipseMoonSet(
                    InvObj("obj.eclipse_moon_helm"),
                    InvObj("obj.eclipse_moon_chestplate"),
                    null,
                    InvObj("obj.eclipse_atlatl"),
                ),
            )
        }

    private fun isSet(
        brother: String,
        helm: InvObj,
        body: InvObj,
        legs: InvObj,
        weapon: InvObj,
    ): Boolean =
        when (brother) {
            "ahrim" -> EquipmentChecks.isAhrimSet(helm, body, legs, weapon)
            "dharok" -> EquipmentChecks.isDharokSet(helm, body, legs, weapon)
            "guthan" -> EquipmentChecks.isGuthanSet(helm, body, legs, weapon)
            "karil" -> EquipmentChecks.isKarilSet(helm, body, legs, weapon)
            "torag" -> EquipmentChecks.isToragSet(helm, body, legs, weapon)
            "verac" -> EquipmentChecks.isVeracSet(helm, body, legs, weapon)
            else -> error("Unknown brother: $brother")
        }
}
