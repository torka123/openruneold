package org.rsmod.content.minigames.corruptedgauntlet

import dev.openrune.ServerCacheManager
import dev.openrune.util.WeaponCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.rsmod.api.config.refs.params

class CorruptedGauntletWeaponCacheTest {
    @Test
    fun `revision 240 weapons expose native combat categories and definitions`() {
        Revision240GauntletCache.initialize()

        WEAPONS.forEach { (id, expectedCategory) ->
            val weapon = assertNotNull(ServerCacheManager.getItem(id), "Missing weapon item $id")
            assertTrue(weapon.isEquipable, "Weapon $id must remain wieldable")
            assertEquals(expectedCategory, weapon.resolvedWeaponCategory, "Weapon $id category")
            assertTrue(weapon.param(params.attackrate) > 0, "Weapon $id needs an attack rate")
            assertNotNull(
                weapon.paramOrNull(params.attack_anim_stance1),
                "Weapon $id needs its cache attack animation",
            )
        }

        BOWS.forEach { id ->
            val bow = assertNotNull(ServerCacheManager.getItem(id))
            assertNotNull(bow.paramOrNull(params.proj_type), "Bow $id needs its cache projectile")
        }
    }

    private companion object {
        val WEAPONS =
            mapOf(
                23_849 to WeaponCategory.Polearm,
                23_850 to WeaponCategory.Polearm,
                23_851 to WeaponCategory.Polearm,
                23_852 to WeaponCategory.PoweredStaff,
                23_853 to WeaponCategory.PoweredStaff,
                23_854 to WeaponCategory.PoweredStaff,
                23_855 to WeaponCategory.Bow,
                23_856 to WeaponCategory.Bow,
                23_857 to WeaponCategory.Bow,
            )
        val BOWS = listOf(23_855, 23_856, 23_857)
    }
}
