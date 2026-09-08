package org.rsmod.api.player.bonus

import kotlin.test.Test
import kotlin.test.assertEquals
import org.rsmod.api.player.prayer.PrayerDrainResistance
import org.rsmod.game.entity.Player

class PrayerBonusModifierStateTest {
    @Test
    fun defaultModifierPreservesTheRawPrayerBonus() {
        assertEquals(12, PrayerBonusModifierState.apply(Player(), 12))
    }

    @Test
    fun multiplierAndAddendComposeAndClearTogether() {
        val player = Player()
        PrayerBonusModifierState.set(player, multiplier = 3, addend = -2)

        assertEquals(28, PrayerBonusModifierState.apply(player, 10))

        PrayerBonusModifierState.clear(player)
        assertEquals(10, PrayerBonusModifierState.apply(player, 10))
    }

    @Test
    fun activeModifierSafelyCapsOverflowAndAllowsANegativeAddend() {
        val player = Player()
        PrayerBonusModifierState.set(player, multiplier = Int.MAX_VALUE, addend = Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, PrayerBonusModifierState.apply(player, Int.MAX_VALUE))

        PrayerBonusModifierState.set(player, multiplier = 0, addend = -5)
        assertEquals(-5, PrayerBonusModifierState.apply(player, 100))

        PrayerBonusModifierState.set(player, multiplier = -1)
        assertEquals(-100, PrayerBonusModifierState.apply(player, 100))
    }

    @Test
    fun drainResistanceUsesTheSharedFormulaAndCapsOverflow() {
        assertEquals(60, PrayerDrainResistance.calculate(0))
        assertEquals(80, PrayerDrainResistance.calculate(10))
        assertEquals(Int.MAX_VALUE, PrayerDrainResistance.calculate(Int.MAX_VALUE))
        assertEquals(Int.MIN_VALUE, PrayerDrainResistance.calculate(Int.MIN_VALUE))
    }
}
