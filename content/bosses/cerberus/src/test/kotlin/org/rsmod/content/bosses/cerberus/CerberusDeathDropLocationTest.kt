package org.rsmod.content.bosses.cerberus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.BeforeAll
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class CerberusDeathDropLocationTest {
    @Test
    fun `death piles from different clones return to the recoverable public exit`() {
        val world = CerberusTestWorld()
        val hook = CerberusDeathDropLocation(world.instances, world.arena)
        repeat(4) { slot ->
            val player = world.enter(slot + 1)
            val coords = world.tile(player, 24, 35)
            assertEquals(CERBERUS_PUBLIC_EXIT,
                hook.dropLocation(context(player, coords)))
            assertNull(hook.dropLocation(context(player, coords.copy(level = 1))))
        }
    }

    @Test
    fun `stale instance membership cannot move death items from an external destination`() {
        val world = CerberusTestWorld()
        val first = world.enter(1)
        val second = world.enter(2)
        val hook = CerberusDeathDropLocation(world.instances, world.arena)
        assertNull(hook.dropLocation(context(first, CoordGrid(3200, 3200, 0))))
        assertNull(hook.dropLocation(context(first, world.tile(second, 24, 35))))
    }

    private fun context(player: Player, coords: CoordGrid) = PlayerDeathContext(
        player, coords, false, 0, false, true, false, false, false, 0, null)

    companion object {
        @JvmStatic @BeforeAll
        fun loadCache() = CerberusTestCache.ensureLoaded()
    }
}
