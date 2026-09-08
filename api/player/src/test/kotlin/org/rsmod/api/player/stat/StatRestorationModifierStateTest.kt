package org.rsmod.api.player.stat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.rsmod.game.entity.Player

class StatRestorationModifierStateTest {
    @Test
    fun defaultPercentagePreservesGainsExactly() {
        val player = Player()

        assertEquals(100, StatRestorationModifierState.percentage(player, HITPOINTS))
        assertEquals(17, StatRestorationModifierState.scale(player, HITPOINTS, 17))
    }

    @Test
    fun percentagesArePerStatAndClearIndependently() {
        val player = Player()
        StatRestorationModifierState.setPercentage(player, HITPOINTS, 50)
        StatRestorationModifierState.setPercentage(player, PRAYER, 0)

        assertEquals(8, StatRestorationModifierState.scale(player, HITPOINTS, 17))
        assertEquals(0, StatRestorationModifierState.scale(player, PRAYER, 17))
        assertEquals(17, StatRestorationModifierState.scale(player, ATTACK, 17))

        StatRestorationModifierState.clear(player, HITPOINTS)
        assertEquals(17, StatRestorationModifierState.scale(player, HITPOINTS, 17))
        assertEquals(0, StatRestorationModifierState.scale(player, PRAYER, 17))

        StatRestorationModifierState.clear(player)
        assertEquals(17, StatRestorationModifierState.scale(player, PRAYER, 17))
    }

    @Test
    fun defaultAssignmentClearsTheStatOverride() {
        val player = Player()
        StatRestorationModifierState.setPercentage(player, HITPOINTS, 50)

        StatRestorationModifierState.setPercentage(player, HITPOINTS, 100)

        assertEquals(100, StatRestorationModifierState.percentage(player, HITPOINTS))
    }

    @Test
    fun largeAmountsDoNotOverflowAndInvalidValuesFail() {
        val player = Player()
        StatRestorationModifierState.setPercentage(player, HITPOINTS, 50)

        assertEquals(
            1_073_741_823,
            StatRestorationModifierState.scale(player, HITPOINTS, Int.MAX_VALUE),
        )
        assertFailsWith<IllegalArgumentException> {
            StatRestorationModifierState.setPercentage(player, HITPOINTS, -1)
        }
        assertFailsWith<IllegalArgumentException> {
            StatRestorationModifierState.setPercentage(player, HITPOINTS, 101)
        }
        assertFailsWith<IllegalArgumentException> {
            StatRestorationModifierState.scale(player, HITPOINTS, -1)
        }
    }

    private companion object {
        const val HITPOINTS = "stat.hitpoints"
        const val PRAYER = "stat.prayer"
        const val ATTACK = "stat.attack"
    }
}
