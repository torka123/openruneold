package org.rsmod.content.minigames.corruptedgauntlet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.game.loc.LocAngle

class CorruptedGauntletNavigationRulesTest {
    @Test
    fun `left node follows its map angle`() {
        val current = CorruptedGauntletGridPoint(3, 3)

        assertEquals(
            CorruptedGauntletGridPoint(3, 4),
            CorruptedGauntletNavigationRules.destination(
                current,
                CorruptedGauntletConfig.Loc.UNLIT_NODE_LEFT,
                LocAngle.North,
            ),
        )
        assertEquals(
            CorruptedGauntletGridPoint(4, 3),
            CorruptedGauntletNavigationRules.destination(
                current,
                CorruptedGauntletConfig.Loc.UNLIT_NODE_LEFT,
                LocAngle.East,
            ),
        )
    }

    @Test
    fun `right node uses the opposite of its map angle`() {
        val current = CorruptedGauntletGridPoint(3, 3)

        assertEquals(
            CorruptedGauntletGridPoint(3, 2),
            CorruptedGauntletNavigationRules.destination(
                current,
                CorruptedGauntletConfig.Loc.UNLIT_NODE_RIGHT,
                LocAngle.North,
            ),
        )
        assertEquals(
            CorruptedGauntletGridPoint(2, 3),
            CorruptedGauntletNavigationRules.destination(
                current,
                CorruptedGauntletConfig.Loc.UNLIT_NODE_RIGHT,
                LocAngle.East,
            ),
        )
    }

    @Test
    fun `destination outside the seven by seven grid is rejected`() {
        assertNull(
            CorruptedGauntletNavigationRules.destination(
                CorruptedGauntletGridPoint(0, 0),
                CorruptedGauntletConfig.Loc.UNLIT_NODE_LEFT,
                LocAngle.West,
            )
        )
    }
}
