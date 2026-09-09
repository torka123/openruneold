package org.rsmod.content.bosses.royaltitans

import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessLauncher.Companion.withProtectedAccess
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.player.PlayerRegistryResult
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.stat.PlayerStatChangedEvent
import org.rsmod.game.stat.StatLevelKind

@OptIn(InternalApi::class)
class RoyalTitansStatObservationTest {
    @AfterEach fun clearMocks() = framework().clearInlineMocks()

    @Test
    fun `native registry dispatches immutable drain and restore immediately even while protected`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val registry =
            PlayerRegistry(PlayerList(), f.collision, ZonePlayerActivityBitSet(), f.events, f.clock)
        val changes = mutableListOf<PlayerStatChangedEvent>()
        f.events.subscribeUnbound(PlayerStatChangedEvent::class.java) { changes += this }
        assertEquals(PlayerRegistryResult.Add.Success, registry.add(player))
        var lostPrayer = false
        f.events.subscribeUnbound(PlayerStatChangedEvent::class.java) {
            if (kind == StatLevelKind.Current && currentLevel < previousLevel) lostPrayer = true
        }
        assertTrue(
            withProtectedAccess(player, mock(ProtectedAccessContext::class.java)) {
                player.statSub("stat.prayer", constant = 1, percent = 0)
                assertTrue(lostPrayer, "Must be visible before same-tick completion")
                player.statHeal("stat.prayer", constant = 1, percent = 0)
                assertEquals(
                    listOf(99 to 98, 98 to 99),
                    changes.map { it.previousLevel to it.currentLevel },
                )
            }
        )
        assertTrue(
            changes.all { it.kind == StatLevelKind.Current && it.mutationCycle == f.clock.cycle }
        )
        assertTrue(changes.all { it.membership?.isValid() == true })
        player.combatHitScope?.invalidate()
        assertTrue(changes.all { it.membership?.isValid() == false })
        assertEquals(
            listOf(99 to 98, 98 to 99),
            changes.map { it.previousLevel to it.currentLevel },
        )
    }

    @Test
    fun `offline no-op unsigned and base changes preserve their exact semantics`() {
        val f = RoyalTitansTestWorld()
        val player = f.newPlayer()
        val changes = mutableListOf<PlayerStatChangedEvent>()
        f.events.subscribeUnbound(PlayerStatChangedEvent::class.java) { changes += this }
        player.statMap.setCurrentLevel("stat.prayer", 90)
        assertTrue(changes.isEmpty())
        player.statMap.bindObservations(player, f.events, f.clock)
        player.statMap.setCurrentLevel("stat.prayer", 90)
        assertTrue(changes.isEmpty())
        player.statMap.setCurrentLevel("stat.prayer", 200.toByte())
        player.statMap.setBaseLevel("stat.prayer", 201.toByte())
        assertEquals(
            listOf(90 to 200, 99 to 201),
            changes.map { it.previousLevel to it.currentLevel },
        )
        assertEquals(listOf(StatLevelKind.Current, StatLevelKind.Base), changes.map { it.kind })
        player.statMap.unbindObservations()
        player.statMap.setCurrentLevel("stat.prayer", 99)
        assertEquals(2, changes.size)
    }

    @Test
    fun `reentrant restoration does not rewrite an earlier mutation observation`() {
        val f = RoyalTitansTestWorld()
        val player = f.newPlayer()
        val changes = mutableListOf<PlayerStatChangedEvent>()
        player.statMap.bindObservations(player, f.events, f.clock)
        f.events.subscribeUnbound(PlayerStatChangedEvent::class.java) {
            if (currentLevel == 98) player.statMap.setCurrentLevel("stat.prayer", 99)
        }
        f.events.subscribeUnbound(PlayerStatChangedEvent::class.java) { changes += this }
        player.statMap.setCurrentLevel("stat.prayer", 98)
        assertEquals(
            listOf(98 to 99, 99 to 98),
            changes.map { it.previousLevel to it.currentLevel },
        )
        assertEquals(99, player.statMap.getCurrentLevel("stat.prayer").toInt())
    }

    @Test
    fun `failed registry operations retain bindings and deletion removes them`() {
        val f = RoyalTitansTestWorld()
        val player = f.newPlayer()
        val registry =
            PlayerRegistry(PlayerList(), f.collision, ZonePlayerActivityBitSet(), f.events, f.clock)
        val changes = mutableListOf<PlayerStatChangedEvent>()
        f.events.subscribeUnbound(PlayerStatChangedEvent::class.java) { changes += this }
        assertEquals(PlayerRegistryResult.Add.Success, registry.add(player))
        assertIs<PlayerRegistryResult.Add.ListSlotMismatch>(registry.add(player))
        val impostor = f.newPlayer()
        assertIs<PlayerRegistryResult.Delete.ListSlotMismatch>(registry.del(impostor))
        player.statMap.setCurrentLevel("stat.prayer", 98)
        assertEquals(1, changes.size)
        assertEquals(PlayerRegistryResult.Delete.Success, registry.del(player))
        changes.clear()
        player.statMap.setCurrentLevel("stat.prayer", 97)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `world phase mutations snapshot actual map clock not the previous player processing tick`() {
        val f = RoyalTitansTestWorld()
        val player = f.newPlayer()
        val changes = mutableListOf<PlayerStatChangedEvent>()
        player.statMap.bindObservations(player, f.events, f.clock)
        f.events.subscribeUnbound(PlayerStatChangedEvent::class.java) { changes += this }
        val previous = player.currentMapClock
        f.clock.tick()
        player.statMap.setCurrentLevel("stat.prayer", 98)
        assertEquals(previous, player.currentMapClock)
        assertEquals(previous + 1, changes.single().mutationCycle)
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = RoyalTitansCombatIntegrationTest.cache()
    }
}
