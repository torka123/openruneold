package org.rsmod.content.bosses.wilderness.ethereum

import kotlin.test.Test
import kotlin.test.assertEquals

class EthereumBraceletMechanicsTest {
    @Test
    fun `damage is reduced by seventy five percent with floor rounding`() {
        assertEquals(0, EthereumBraceletMechanics.reduceDamage(0))
        assertEquals(0, EthereumBraceletMechanics.reduceDamage(3))
        assertEquals(1, EthereumBraceletMechanics.reduceDamage(4))
        assertEquals(4, EthereumBraceletMechanics.reduceDamage(17))
        assertEquals(7, EthereumBraceletMechanics.reduceDamage(30))
    }

    @Test
    fun `absorption never exceeds sixteen thousand charges`() {
        assertEquals(20, EthereumBraceletMechanics.absorbableEther(100, 20))
        assertEquals(5, EthereumBraceletMechanics.absorbableEther(15_995, 20))
        assertEquals(0, EthereumBraceletMechanics.absorbableEther(16_000, 20))
    }

    @Test
    fun `guaranteed ether maximum follows the main game combat formula`() {
        val levelsToMaximum =
            mapOf(
                7 to 6,
                15 to 8,
                52 to 16,
                60 to 16,
                82 to 20,
                90 to 20,
                98 to 20,
                105 to 22,
                120 to 22,
                126 to 24,
                135 to 24,
            )
        levelsToMaximum.forEach { (level, maximum) ->
            assertEquals(maximum, EthereumBraceletMechanics.maximumGuaranteedEther(level))
        }
    }
}
