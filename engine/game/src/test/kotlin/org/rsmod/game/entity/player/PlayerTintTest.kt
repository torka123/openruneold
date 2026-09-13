package org.rsmod.game.entity.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
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
    fun `owned tint remains clearable after its pending update is consumed`() {
        val player = Player()
        val tint = PlayerTint(0, 605, 0, 0, 0, 112)
        val token = player.tintOwned(tint)
        assertSame(tint, player.pendingTint)

        player.pendingTint = null
        assertTrue(player.clearTint(token))
        assertSame(PlayerTint.CLEAR, player.pendingTint)

        player.pendingTint = null
        assertFalse(player.clearTint(token))
        assertNull(player.pendingTint, "An already-cleared token must not queue another clear")
    }

    @Test
    fun `owned replacement gets a fresh token for identical object or equal tint value`() {
        val tint = PlayerTint(0, 605, 0, 0, 0, 112)
        for (replacement in listOf(tint, tint.copy())) {
            val player = Player()
            val oldToken = player.tintOwned(tint)
            val newToken = player.tintOwned(replacement)
            assertNotSame(oldToken, newToken)

            assertFalse(player.clearTint(oldToken))
            assertSame(replacement, player.pendingTint)
            player.pendingTint = null
            assertFalse(player.clearTint(oldToken))
            assertNull(player.pendingTint)
            assertTrue(player.clearTint(newToken))
            assertSame(PlayerTint.CLEAR, player.pendingTint)
        }
    }

    @Test
    fun `legacy tint replaces owned tint even for identical object or equal value`() {
        val tint = PlayerTint(0, 605, 0, 0, 0, 112)
        for (replacement in listOf(tint, tint.copy())) {
            val player = Player()
            val token = player.tintOwned(tint)
            player.tint(replacement)

            assertFalse(player.clearTint(token))
            assertSame(replacement, player.pendingTint)
            player.pendingTint = null
            assertFalse(player.clearTint(token))
            assertNull(player.pendingTint)
        }
    }

    @Test
    fun `direct non-null pending writes replace ownership without relying on value equality`() {
        val tint = PlayerTint(0, 605, 0, 0, 0, 112)
        for (replacement in listOf(tint, tint.copy(), PlayerTint.CLEAR)) {
            val player = Player()
            val token = player.tintOwned(tint)
            player.pendingTint = replacement

            assertFalse(player.clearTint(token))
            assertSame(replacement, player.pendingTint)
            player.pendingTint = null
            assertFalse(player.clearTint(token))
            assertNull(player.pendingTint)
        }
    }

    @Test
    fun `foreign player token cannot clear or invalidate the local owner`() {
        val first = Player()
        val second = Player()
        val tint = PlayerTint(0, 605, 0, 0, 0, 112)
        val firstToken = first.tintOwned(tint)
        val secondToken = second.tintOwned(tint)

        assertFalse(first.clearTint(secondToken))
        assertFalse(second.clearTint(firstToken))
        assertSame(tint, first.pendingTint)
        assertSame(tint, second.pendingTint)
        first.pendingTint = null
        second.pendingTint = null
        assertFalse(first.clearTint(secondToken))
        assertFalse(second.clearTint(firstToken))
        assertNull(first.pendingTint)
        assertNull(second.pendingTint)
        assertTrue(first.clearTint(firstToken))
        assertTrue(second.clearTint(secondToken))
    }

    @Test
    fun `explicit clear invalidates owned cleanup without requeuing after consumption`() {
        val player = Player()
        val token = player.tintOwned(PlayerTint(0, 605, 0, 0, 0, 112))
        player.clearTint()

        assertSame(PlayerTint.CLEAR, player.pendingTint)
        assertFalse(player.clearTint(token))
        player.pendingTint = null
        assertFalse(player.clearTint(token))
        assertNull(player.pendingTint)
    }

    @Test
    fun `tint fields reject values outside the protocol ranges`() {
        assertFailsWith<IllegalArgumentException> { PlayerTint(-1, 0, 0, 0, 0, 0) }
        assertFailsWith<IllegalArgumentException> { PlayerTint(0, 65_536, 0, 0, 0, 0) }
        assertFailsWith<IllegalArgumentException> { PlayerTint(0, 0, 256, 0, 0, 0) }
    }
}
