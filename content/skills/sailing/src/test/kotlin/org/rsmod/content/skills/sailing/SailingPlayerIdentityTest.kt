package org.rsmod.content.skills.sailing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.rsmod.game.entity.Player

class SailingPlayerIdentityTest {
    @Test
    fun `uninitialized character identity fails closed`() {
        assertNull(SailingPlayerIdentity.idOrNull(Player()))
    }

    @Test
    fun `identity uses the durable character id`() {
        val player = Player()
        player.characterId = 42

        assertEquals(42L, SailingPlayerIdentity.idOrNull(player))
    }

    @Test
    fun `debug boat ids are stable positive and character scoped`() {
        val first = SailingPlayerIdentity.debugBoatId(42)
        val repeat = SailingPlayerIdentity.debugBoatId(42)
        val second = SailingPlayerIdentity.debugBoatId(43)

        assertEquals(first, repeat)
        assertNotEquals(first, second)
        assertTrue(first.value > 0)
    }

    @Test
    fun `owned boat ids are stable and scoped to character plus slot`() {
        val first = SailingBoatIds.owned(characterId = 42, ownershipSlot = 0)

        assertEquals(first, SailingBoatIds.owned(characterId = 42, ownershipSlot = 0))
        assertNotEquals(first, SailingBoatIds.owned(characterId = 42, ownershipSlot = 1))
        assertNotEquals(first, SailingBoatIds.owned(characterId = 43, ownershipSlot = 0))
        assertNotEquals(first, SailingBoatIds.debug(characterId = 42))
    }

    @Test
    fun `owned boat id rejects invalid identity and slots`() {
        assertFailsWith<IllegalArgumentException> { SailingBoatIds.owned(0, 0) }
        assertFailsWith<IllegalArgumentException> { SailingBoatIds.owned(42, -1) }
        assertFailsWith<IllegalArgumentException> { SailingBoatIds.owned(42, 5) }
    }
}
