package org.rsmod.content.other.special.weapons.scripts.charge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebweaverBowChargePolicyTest {
    @Test
    fun `uncharged bow requires the activation cost before accepting ammo`() {
        assertNull(
            WebweaverBowChargePolicy.planLoad(
                activated = false,
                currentAmmo = 0,
                availableEther = 999,
            )
        )

        val activatedOnly =
            WebweaverBowChargePolicy.planLoad(
                activated = false,
                currentAmmo = 0,
                availableEther = 1_000,
            )
        assertEquals(1_000, activatedOnly?.totalEtherRemoved)
        assertEquals(0, activatedOnly?.totalAmmo)

        val activatedWithAmmo =
            WebweaverBowChargePolicy.planLoad(
                activated = false,
                currentAmmo = 0,
                availableEther = 1_250,
            )
        assertEquals(1_250, activatedWithAmmo?.totalEtherRemoved)
        assertEquals(250, activatedWithAmmo?.totalAmmo)
    }

    @Test
    fun `activated bow loads usable ether and caps at sixteen thousand`() {
        val plan =
            WebweaverBowChargePolicy.planLoad(
                activated = true,
                currentAmmo = 15_900,
                availableEther = 500,
            )
        assertEquals(100, plan?.ammoAdded)
        assertEquals(16_000, plan?.totalAmmo)
        assertEquals(100, plan?.totalEtherRemoved)
        assertNull(
            WebweaverBowChargePolicy.planLoad(
                activated = true,
                currentAmmo = 16_000,
                availableEther = 500,
            )
        )
    }

    @Test
    fun `uncharge refunds activation ether and all unused ammo`() {
        assertEquals(1_000, WebweaverBowChargePolicy.refundForUncharge(0))
        assertEquals(1_375, WebweaverBowChargePolicy.refundForUncharge(375))
        assertEquals(17_000, WebweaverBowChargePolicy.refundForUncharge(16_000))
    }
}
