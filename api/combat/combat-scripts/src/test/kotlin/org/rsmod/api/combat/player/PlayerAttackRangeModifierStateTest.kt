package org.rsmod.api.combat.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.rsmod.game.entity.Player

class PlayerAttackRangeModifierStateTest {
    @Test
    fun `sensory range reduction halves magic and weapon ranges`() {
        val player = Player()
        PlayerAttackRangeModifierState.set(player, numerator = 1, denominator = 2)

        assertEquals(5, PlayerAttackRangeModifierState.apply(player, 10))
        assertEquals(3, PlayerAttackRangeModifierState.apply(player, 7))
        assertEquals(1, PlayerAttackRangeModifierState.apply(player, 1))
    }

    @Test
    fun `clearing restores unmodified range`() {
        val player = Player()
        PlayerAttackRangeModifierState.set(player, numerator = 1, denominator = 2)
        PlayerAttackRangeModifierState.clear(player)

        assertEquals(10, PlayerAttackRangeModifierState.apply(player, 10))
    }

    @Test
    fun `invalid scale is rejected`() {
        val player = Player()
        assertFailsWith<IllegalArgumentException> {
            PlayerAttackRangeModifierState.set(player, numerator = 0, denominator = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            PlayerAttackRangeModifierState.set(player, numerator = 1, denominator = 0)
        }
    }
}
