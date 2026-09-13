package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.hit.HitType

/** Native registration, selector, hit-queue and repository tests, not OSRS visual certification. */
@OptIn(InternalApi::class)
class NexMainNativeCombatTest {
    @Test
    fun `main forms have one owner and two five-normal cycles use native cadence`() {
        val f = NexMinionTestWorld()
        val boss = f.npc(11278)
        val encounter = f.encounters.of(boss)
        assertSame(f.encounters.registeredBoss("nex"), encounter.spec)
        assertEquals("SMOKE", encounter.currentPhaseName)
        f.player.coords = NEX_SPAWN.translateX(-4)
        val start = f.clock.cycle
        repeat(12) { index ->
            f.attack(boss)
            val count = encounter.phaseAbilityCount
            f.attack(boss)
            assertEquals(count, encounter.phaseAbilityCount, "same-tick call consumed an attack")
            if (index < 11) f.tick(if (index == 5) 5 else 4)
        }
        assertEquals(
            List(5) { "SMOKE.normal" } +
                "SMOKE.special" +
                List(5) { "SMOKE.normal" } +
                "SMOKE.special",
            encounter.recentAttackTraces().map { it.ability },
        )
        assertEquals(
            listOf(0, 4, 8, 12, 16, 20, 25, 29, 33, 37, 41, 45).map { start + it },
            encounter.recentAbilityCommitTicks(),
        )
        assertEquals(start + 50, boss.actionDelay)
    }

    @Test
    fun `raw disconnect cancels pending ordinary hits in every main phase`() {
        for (phase in
            listOf(NexState.SMOKE, NexState.SHADOW, NexState.BLOOD, NexState.ICE, NexState.ZAROS)) {
            val f = NexMinionTestWorld()
            f.reachPhase(phase)
            f.player.coords = NEX_SPAWN.translateX(-4)
            f.attack(f.npc(11278))
            assertEquals(1, f.hits().size + f.deferredHits().size)
            f.player.clientDisconnected.set(true)
            f.processPlayerHits()
            assertEquals(99, f.player.hitpoints, phase.name)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `native deletion retires main encounter and pending impact effects`() {
        val f = NexMinionTestWorld()
        val boss = f.npc(11278)
        f.player.coords = NEX_SPAWN.translateX(-4)
        f.attack(boss)
        val encounter = f.encounters.of(boss)
        f.repository.del(boss, Int.MAX_VALUE)
        assertTrue(encounter.isEnded)
        assertFalse(f.hits().single().second.isValidNow())
        f.processPlayerHits()
        assertEquals(99, f.player.hitpoints)
    }

    @Test
    fun `fired smoke spell retains its launch effects across guard handoff`() {
        val f = NexMinionTestWorld()
        val boss = f.npc(11278)
        val encounter = f.encounters.of(boss)
        val uid = boss.uid
        f.player.coords = NEX_SPAWN.translateX(-4)
        f.attack(boss)
        f.reachGuard(11283)
        f.manager.handleDeath(f.npc(11283))
        assertEquals("SHADOW", encounter.currentPhaseName)
        assertSame(encounter, f.encounters.of(boss))
        assertEquals(uid, boss.uid)
        f.processPlayerHits()
        assertEquals(58, f.player.hitpoints) // 33 magic + native initial poison8, not Shadow's60.
    }

    @Test
    fun `Zaros twelve-opportunity rotation preserves form identity and pending spells`() {
        val f = NexMinionTestWorld()
        f.reachPhase(NexState.ZAROS)
        val boss = f.npc(11278)
        val encounter = f.encounters.of(boss)
        val uid = boss.uid
        f.player.coords = NEX_SPAWN.translateX(-4)
        val expected = List(4) { 11280 } + List(4) { 11281 } + List(4) { 11278 }
        repeat(24) { index ->
            f.attack(boss)
            assertEquals(expected[index % 12], boss.visType.id)
            assertEquals(uid, boss.uid)
            assertSame(encounter, f.encounters.of(boss))
            assertTrue(f.deferredHits().all { it.second.builder.validity?.isValid() == true })
            f.tick(4)
        }
        assertTrue(f.selectionBounds.isEmpty())
    }

    @Test
    fun `melee continuation crosses Zaros overhead boundary without another random draw`() {
        val f = NexMinionTestWorld()
        f.reachPhase(NexState.ZAROS)
        val boss = f.npc(11278)
        f.player.coords = NEX_SPAWN.translateX(-4)
        repeat(3) {
            f.attack(boss)
            f.tick(4)
        }
        f.player.coords = NEX_SPAWN.translateX(-1)
        f.selectionRolls.add(0)
        repeat(3) {
            f.attack(boss)
            assertEquals(HitType.Melee, f.hits().last().second.type)
            assertEquals(if (it == 0) 11280 else 11281, boss.visType.id)
            f.tick(4)
        }
        assertEquals(listOf(3), f.selectionBounds)
    }

    @Test
    fun `native footprint range supports each cardinal side of the three tile boss`() {
        // Explicit M25 geometry correction, not preservation of the draft's anchor-only range.
        for ((x, z) in listOf(-1 to 1, 3 to 1, 1 to -1, 1 to 3)) {
            val f = NexMinionTestWorld()
            f.player.coords = NEX_SPAWN.translate(x, z)
            f.selectionRolls.add(0)
            f.attack(f.npc(11278))
            assertEquals(HitType.Melee, f.hits().single().second.type, "$x,$z")
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `guard handoff cannot shorten an already launched ordinary attack cooldown`() {
        val f = NexMinionTestWorld()
        val boss = f.npc(11278)
        f.player.coords = NEX_SPAWN.translateX(-4)
        f.attack(boss)
        val ready = boss.actionDelay
        f.reachGuard(11283)
        f.manager.handleDeath(f.npc(11283))
        while (f.clock.cycle < ready) {
            f.attack(boss)
            assertEquals(1, f.hits().size)
            f.tick()
        }
        f.attack(boss)
        assertEquals(HitType.Ranged, f.hits().last().second.type)
        assertEquals(2, f.hits().size)
    }

    @Test
    fun `handoff during special retains the legacy two-tick readiness instead of stale five-tick override`() {
        val f = NexMinionTestWorld()
        val boss = f.npc(11278)
        f.player.coords = NEX_SPAWN.translateX(-4)
        repeat(5) {
            f.attack(boss)
            f.tick(4)
        }
        f.attack(boss)
        f.reachGuard(11283)
        f.manager.handleDeath(f.npc(11283))
        val ready = f.clock.cycle + 2
        repeat(2) {
            f.attack(boss)
            assertEquals(5, f.hits().size)
            f.tick()
        }
        assertEquals(ready, f.clock.cycle)
        f.attack(boss)
        assertEquals(6, f.hits().size)
        assertEquals(ready + 4, boss.actionDelay)
    }

    @Test
    fun `primary miss still produces one secondary splash and handoff is consumed once`() {
        val f = NexMinionTestWorld(duo = true)
        val secondary = assertNotNull(f.partner)
        f.player.coords = NEX_SPAWN.translateX(-4)
        secondary.coords = f.player.coords.translateZ(1)
        f.accurate = false
        val boss = f.npc(11278)
        f.attack(boss)
        assertEquals(0, f.hits().single().second.damage)
        assertEquals(33, f.hits(secondary).single().second.damage)
        assertEquals(3, f.hits(secondary).single().first)
        f.manager.magicSplash(boss, f.player)
        f.attack(boss) // Same-tick native rejection cannot create another splash.
        assertEquals(1, f.hits(secondary).size)
        f.processPlayerHits()
        assertEquals(99, f.player.hitpoints)
        assertEquals(58, secondary.hitpoints)
    }

    @Test
    fun `secondary validity is bound to participant and room identity`() {
        val f = NexMinionTestWorld(duo = true)
        val secondary = assertNotNull(f.partner)
        f.player.coords = NEX_SPAWN.translateX(-4)
        secondary.coords = f.player.coords.translateZ(1)
        f.attack(f.npc(11278))
        secondary.clientDisconnected.set(true)
        f.processPlayerHits()
        assertEquals(58, f.player.hitpoints)
        assertEquals(99, secondary.hitpoints)
    }

    @Test
    fun `final secondary retains characterized launch-only prayer while primary uses late prayer`() {
        val f = NexMinionTestWorld(duo = true)
        f.reachPhase(NexState.ZAROS)
        val secondary = assertNotNull(f.partner)
        f.player.coords = NEX_SPAWN.translateX(-4)
        secondary.coords = f.player.coords.translateZ(1)
        f.attack(f.npc(11278))
        assertEquals(33, f.hits(secondary).single().second.damage)
        f.protectMagic(true)
        org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(
            secondary,
            "varbit.prayer_protectfrommagic",
            1,
        )
        f.processPlayerHits()
        assertEquals(75, f.player.hitpoints)
        assertEquals(66, secondary.hitpoints) // Explicit existing discrepancy, not parity approval.
    }

    @Test
    fun `target replacement does not inherit the former player's two melee attempts`() {
        val f = NexMinionTestWorld(duo = true)
        val secondary = assertNotNull(f.partner)
        val boss = f.npc(11278)
        f.player.coords = NEX_SPAWN.translateX(-1)
        secondary.coords = f.player.coords.translateZ(1)
        f.selectionRolls.add(0)
        f.attack(boss)
        f.tick(4)
        f.attack(boss, secondary)
        assertEquals(HitType.Magic, f.hits(secondary).last().second.type)
        assertEquals(listOf(3, 3), f.selectionBounds)
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
