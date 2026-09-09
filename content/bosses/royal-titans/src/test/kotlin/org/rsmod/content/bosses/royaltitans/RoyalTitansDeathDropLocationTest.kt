package org.rsmod.content.bosses.royaltitans

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.api.death.*
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.hook.GroundItemDropResolver
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

class RoyalTitansDeathDropLocationTest {
    @AfterEach fun clearMocks() = framework().clearInlineMocks()

    @Test
    fun `native unsafe pile survives instance destruction without overriding respawn`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(2)
        val session = assertNotNull(f.instances.sessionForPlayer(player))
        val deathCoords = player.coords
        val context = context(player, deathCoords)
        val hook = RoyalTitansDeathDropLocation(f.instances)
        val destination = assertNotNull(hook.dropLocation(context))
        assertEquals(ROYAL_TITANS_OUTSIDE, destination)
        val handling = assertNotNull(RoyalTitansPlayerDeathHook(f.manager).handleDeath(context))
        assertEquals(3, handling.keepCount)
        assertEquals(
            4,
            RoyalTitansPlayerDeathHook(f.manager)
                .handleDeath(context.copy(hasProtectItem = true))
                ?.keepCount,
        )
        assertEquals(UntradeableHandling.DROP, handling.untradeableHandling)
        assertEquals(PlayerDeathDrops.DROP_DURATION_STANDARD, handling.dropDuration)
        assertSame(player, handling.dropReceiver)
        val drops =
            PlayerDeathDrops(
                f.clock,
                ObjRepository(f.clock, f.objRegistry),
                mock(MarketPrices::class.java),
                GroundItemDropResolver(emptySet()),
                emptySet(),
            )
        val item = InvObj("obj.coins", 731)
        drops.applyDrops(
            player,
            PlayerDeathDrops.DeathDropResult(
                emptyList(),
                emptyList(),
                listOf(item),
                emptyList(),
                0,
            ),
            handling,
            destination,
        )
        drops.spawnRemains(destination, handling)
        assertTrue(f.objRegistry.findAll(deathCoords).none())
        val pile = f.objRegistry.findAll(destination).first { it.type == item.id }
        assertEquals(731, pile.count)
        assertEquals(player.observerUUID, pile.receiverId)

        // Native PlayerDeath telejumps before calling cleanup. Exercise those public boundaries.
        val respawn = CoordGrid(3222, 3218, 0)
        player.coords = respawn
        val cleanup = RoyalTitansDeathCleanup(f.manager)
        cleanup.cleanup(player)
        cleanup.cleanup(player)
        assertEquals(respawn, player.coords)
        assertNull(f.instances.sessionForId(session.id))
        assertFalse(f.manager.isActive(player))
        assertTrue(f.instances.npcsForInstance(session.id).isEmpty())
        assertSame(pile, f.objRegistry.findAll(destination).first { it.type == item.id })
        assertEquals(
            2,
            f.objRegistry.count(),
            "One lost-item stack and native bones, no duplicate pile",
        )
        assertEquals(
            1,
            mockingDetails(f.statistics).invocations.count { it.method.name == "death" },
        )
    }

    @Test
    fun `only the original matching instance and plane can redirect a death pile`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val other = f.newPlayer(2)
        assertTrue(f.manager.enter(other))
        val outsider = f.newPlayer(3)
        val hook = RoyalTitansDeathDropLocation(f.instances)
        assertEquals(ROYAL_TITANS_OUTSIDE, hook.dropLocation(context(player, player.coords)))
        assertEquals(ROYAL_TITANS_OUTSIDE, hook.dropLocation(context(other, other.coords)))
        assertNull(hook.dropLocation(context(player, player.coords.copy(level = 1))))
        assertNull(hook.dropLocation(context(player, other.coords)))
        assertNull(hook.dropLocation(context(player, CoordGrid(3222, 3218, 0))))
        assertNull(hook.dropLocation(context(outsider, player.coords)))
    }

    private fun context(player: Player, coords: CoordGrid) =
        PlayerDeathContext(player, coords, false, 0, false, true, false, false, false, 0, null)

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}
