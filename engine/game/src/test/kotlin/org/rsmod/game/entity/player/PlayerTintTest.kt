package org.rsmod.game.entity.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.rsmod.game.entity.Player

class PlayerTintTest {
    @Test
    fun `clear uses the unsigned wire representation of captured signed sentinels`() {
        assertEquals(0, PlayerTint.CLEAR.startTime)
        assertEquals(0, PlayerTint.CLEAR.endTime)
        assertEquals(255, PlayerTint.CLEAR.hue)
        assertEquals(255, PlayerTint.CLEAR.saturation)
        assertEquals(255, PlayerTint.CLEAR.lightness)
        assertEquals(0, PlayerTint.CLEAR.weight)
    }

    @Test
    fun `player queues tint and explicit clear separately`() {
        val player = Player()
        val trail = PlayerTint(0, 605, 0, 0, 0, 112)

        player.tint(trail)
        assertEquals(trail, player.pendingTint)

        player.clearTint()
        assertEquals(PlayerTint.CLEAR, player.pendingTint)
    }

    @Test
    fun `tint fields reject values outside the protocol ranges`() {
        assertFailsWith<IllegalArgumentException> { PlayerTint(-1, 0, 0, 0, 0, 0) }
        assertFailsWith<IllegalArgumentException> { PlayerTint(0, 65_536, 0, 0, 0, 0) }
        assertFailsWith<IllegalArgumentException> { PlayerTint(0, 0, 256, 0, 0, 0) }
    }
}
