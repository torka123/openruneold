package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

/**
 * Native registered specials, owned world queues and player impacts; draft mechanics, not parity.
 */
@OptIn(InternalApi::class)
class NexSpecialLifecycleTest {
    private enum class Special(
        val phase: NexState,
        val index: Int,
        val delay: Int,
        val damage: Int,
    ) {
        DASH(NexState.SMOKE, 1, 2, 50),
        SMASH(NexState.SHADOW, 0, 3, 50),
        DARKNESS(NexState.SHADOW, 1, 0, 8),
        SACRIFICE(NexState.BLOOD, 1, 8, 80),
        CONTAINMENT(NexState.ICE, 0, 3, 60),
        PRISON(NexState.ICE, 1, 5, 75),
    }

    @Test
    fun `registered specials preserve callback countdown and native one tick impact`() {
        for (special in Special.entries) {
            val f = pendingSpecial(special)
            emitHit(f, special)
            val hit = f.hits().single().second
            assertEquals(special.damage, hit.damage, special.name)
            assertEquals(HitType.Typeless, hit.type)
            assertEquals(
                99,
                f.player.hitpoints,
                "The world callback must not apply damage directly",
            )
            val boss = f.npc(11278)
            val hp = boss.hitpoints
            f.processPlayerHits(1)
            assertEquals(99 - special.damage, f.player.hitpoints, special.name)
            if (special == Special.SACRIFICE) assertEquals(hp + special.damage, boss.hitpoints)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `departure after special callback cancels queued damage and sacrifice healing`() {
        val departures: List<(NexMinionTestWorld) -> Unit> =
            listOf(
                { it.player.coords = NEX_BANK_RETURN },
                { it.player.clientDisconnected.set(true) },
                { it.player.pendingLogout = true },
                { it.player.loggingOut = true },
                { it.player.statMap.setCurrentLevel("stat.hitpoints", 0) },
                { it.manager.playerLoggedOut(it.player) },
                {
                    it.players[it.player.slotId] =
                        Player().apply {
                            slotId = it.player.slotId
                            uuid = 999
                            observerUUID = 999
                            assignUid()
                        }
                },
            )
        for (special in Special.entries) for ((index, depart) in departures.withIndex()) {
            val f = pendingSpecial(special)
            emitHit(f, special)
            val boss = f.npc(11278)
            val bossHp = boss.hitpoints
            depart(f)
            val playerHp = f.player.hitpoints
            f.processPlayerHits(1)
            assertEquals(playerHp, f.player.hitpoints, "$special departure=$index")
            assertEquals(bossHp, boss.hitpoints, "$special must not heal after an invalid hit")
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `NPC deletion removes owned pending callbacks immediately`() {
        for (special in Special.entries.filter { it.delay > 0 }) {
            val f = pendingSpecial(special)
            val boss = f.npc(11278)
            val encounter = f.encounters.of(boss)
            assertTrue(f.worldQueues.isNotEmpty, special.name)
            f.repository.del(boss, Int.MAX_VALUE)
            assertTrue(encounter.isEnded)
            assertTrue(f.worldQueues.isEmpty, "$special retained its callback after deletion")
            val coords = f.player.coords
            val prayer = f.player.statMap.getCurrentLevel("stat.prayer")
            f.tick(12)
            assertEquals(coords, f.player.coords)
            assertEquals(prayer, f.player.statMap.getCurrentLevel("stat.prayer"))
            assertTrue(f.hits().isEmpty())
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `source deletion or encounter replacement cannot revive an already emitted special hit`() {
        for (deleteNpc in listOf(false, true)) for (special in Special.entries) {
            val f = pendingSpecial(special)
            emitHit(f, special)
            val boss = f.npc(11278)
            val old = f.encounters.of(boss)
            if (deleteNpc) f.repository.del(boss, Int.MAX_VALUE)
            else {
                f.encounters.remove(boss)
                assertNotSame(old, f.encounters.of(boss))
            }
            assertTrue(old.isEnded)
            f.processPlayerHits(1)
            assertEquals(99, f.player.hitpoints, special.name)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `removing native hidden death source cancels pending Wrath and reward work`() {
        val f = NexMinionTestWorld(nativeDeaths = true)
        f.reachPhase(NexState.ZAROS)
        val boss = f.npc(11278)
        boss.hitpoints = 1
        f.damage(boss, 1)
        var ticks = 0
        while (!boss.hidden && ticks++ < 40) f.tick()
        assertTrue(boss.hidden)
        assertTrue(f.worldQueues.isNotEmpty, "Death should have armed the existing Wrath delay")
        clearInvocations(f.rewardService)
        f.repository.del(boss, Int.MAX_VALUE)
        assertTrue(f.worldQueues.isEmpty, "Death work must retire with its native encounter")
        f.tick(12)
        verifyNoInteractions(f.rewardService)
        assertTrue(f.hits().isEmpty())
        assertEquals(5, f.npcs.count())
    }

    @Test
    fun `departing duo member does not cancel the surviving members emitted smash`() {
        val f = pendingSpecial(Special.SMASH, duo = true)
        emitHit(f, Special.SMASH)
        val partner = requireNotNull(f.partner)
        assertEquals(1, f.hits(partner).size)
        f.player.coords = NEX_BANK_RETURN
        f.processPlayerHits(1)
        assertEquals(99, f.player.hitpoints)
        assertEquals(49, partner.hitpoints)
    }

    @Test
    fun `deflect reflection uses impact lifetime without changing reflected damage`() {
        for (depart in listOf(false, true)) {
            val f = NexMinionTestWorld()
            f.reachPhase(NexState.ZAROS)
            val boss = f.npc(11278)
            f.manager.setZarosOverhead(boss, NexOverhead.DEFLECT_MELEE)
            f.damage(boss, 20, HitType.Melee)
            f.tick()
            assertEquals(10, f.hits().single().second.damage)
            if (depart) f.player.coords = NEX_BANK_RETURN
            f.processPlayerHits(1)
            assertEquals(if (depart) 99 else 89, f.player.hitpoints)
            framework().clearInlineMocks()
        }
    }

    private fun pendingSpecial(special: Special, duo: Boolean = false): NexMinionTestWorld {
        val f = NexMinionTestWorld(duo = duo)
        f.reachPhase(special.phase)
        val boss = f.npc(11278)
        f.player.coords = NEX_SPAWN.translateX(-1)
        f.partner?.coords = NEX_SPAWN.translateX(-2)
        repeat(special.index) {
            forceSpecial(f, special.phase)
            f.tick(12)
            f.player.queueList.clear()
            f.partner?.queueList?.clear()
        }
        f.player.coords = NEX_SPAWN.translateX(-1)
        assertTrue(f.worldQueues.isEmpty)
        forceSpecial(f, special.phase)
        if (special == Special.DASH) f.player.coords = CoordGrid(2924, 5203)
        assertEquals("${special.phase}.special", f.encounters.of(boss).lastAbilityName)
        return f
    }

    private fun forceSpecial(f: NexMinionTestWorld, phase: NexState) {
        val boss = f.npc(11278)
        assertTrue(f.encounters.of(boss).forceNextAbility("$phase.special"))
        f.attack(boss)
    }

    private fun emitHit(f: NexMinionTestWorld, special: Special) {
        // A native attack is issued during pre-tick; finish that issuing cycle before processing
        // subsequent world callbacks. No GameCycle/OSRS visual parity is asserted by this fixture.
        f.clock.tick()
        if (special == Special.DARKNESS) {
            repeat(2) { if (f.hits().isEmpty()) f.tick() }
        } else {
            repeat(special.delay - 1) {
                f.tick()
                assertTrue(f.hits().isEmpty(), "$special callback fired early")
            }
            f.tick()
        }
        assertEquals(1, f.hits().size, special.name)
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
