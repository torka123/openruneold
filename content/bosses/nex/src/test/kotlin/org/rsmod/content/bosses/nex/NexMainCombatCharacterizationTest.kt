package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.hit.HitType

/** Legacy production behavior before main-loop conversion; not OSRS certification. */
class NexMainCombatCharacterizationTest {
    @Test
    fun `all five phase normals retain native presentation prayer and actual HP impacts`() {
        data class Case(
            val phase: NexState,
            val maximum: Int,
            val protectedDamage: Int,
            val projectile: Int,
        )
        val cases =
            listOf(
                Case(NexState.SMOKE, 33, 16, 1997),
                Case(NexState.SHADOW, 60, 30, 1999),
                Case(NexState.BLOOD, 33, 11, 2000),
                Case(NexState.ICE, 33, 16, 2004),
                Case(NexState.ZAROS, 33, 16, 2007),
            )
        for (case in cases) for (protected in listOf(false, true)) {
            val f = NexMinionTestWorld()
            f.reachPhase(case.phase)
            f.player.coords = NEX_SPAWN.translateX(-4)
            f.protectMagic(protected)
            if (case.phase == NexState.SHADOW)
                org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(
                    f.player,
                    "varbit.prayer_protectfrommissiles",
                    if (protected) 1 else 0,
                )
            val boss = f.npc(11278)
            val start = f.clock.cycle
            f.attack(boss)
            assertEquals(if (case.phase == NexState.ZAROS) 9188 else 9189, boss.pendingSequence.id)
            assertEquals(case.projectile, f.projectiles().last().spotanim)
            assertEquals(start + 4, boss.actionDelay)
            assertEquals(1, f.accuracyRolls)
            val damage = if (protected) case.protectedDamage else case.maximum
            if (case.phase == NexState.ZAROS) {
                // Final magic defers prayer reduction until the native impact processor.
                assertTrue(f.hits().isEmpty())
                assertEquals(case.maximum, f.deferredHits().single().second.builder.damage)
            } else assertEquals(damage, f.hits().single().second.damage)
            f.processPlayerHits()
            // The native poison pipeline applies its first tick during impact processing.
            val poisonDamage = if (case.phase == NexState.SMOKE) 8 else 0
            assertEquals(
                99 - damage - poisonDamage,
                f.player.hitpoints,
                "${case.phase}, prayer=$protected",
            )
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `same target melee continuation consumes three normal opportunities and only one choice roll`() {
        val f = NexMinionTestWorld()
        val boss = f.npc(11278)
        f.player.coords = NEX_SPAWN.translateX(-1)
        f.selectionRolls.add(0) // Start the draft's one-in-three melee branch.
        repeat(3) {
            f.attack(boss)
            assertEquals(HitType.Melee, f.hits().last().second.type)
            assertEquals(27, f.hits().last().second.damage)
            assertEquals(9180, boss.pendingSequence.id)
            f.tick(4)
        }
        assertEquals(listOf(3), f.selectionBounds)
        f.attack(boss) // Default upper roll chooses magic after both continuations.
        assertEquals(HitType.Magic, f.hits().last().second.type)
        assertEquals(listOf(3, 3), f.selectionBounds)
    }

    @Test
    fun `forced special interrupts an unfinished melee continuation without consuming it`() {
        val f = NexMinionTestWorld()
        val boss = f.npc(11278)
        f.player.coords = NEX_SPAWN.translateX(-4)
        repeat(4) {
            f.attack(boss)
            f.tick(4)
        }
        assertTrue(f.selectionBounds.isEmpty(), "Out-of-melee normals consume no style-choice RNG")
        f.player.coords = NEX_SPAWN.translateX(-1)
        f.selectionRolls.add(0)
        f.attack(boss) // Fifth normal starts a melee chain.
        f.tick(4)
        assertEquals(5, f.hits().size)
        f.attack(boss) // Sixth opportunity is Choke, leaving both extra melee attacks pending.
        assertEquals(5, f.hits().size)
        f.tick(5)
        f.attack(boss)
        assertEquals(6, f.hits().size)
        assertEquals(HitType.Melee, f.hits().last().second.type)
        assertEquals(listOf(3), f.selectionBounds)
    }

    @Test
    fun `leaving melee range cancels continuation without rolling until reentry`() {
        val f = NexMinionTestWorld()
        val boss = f.npc(11278)
        f.player.coords = NEX_SPAWN.translateX(-1)
        f.selectionRolls.add(0)
        f.attack(boss)
        f.tick(4)
        f.player.coords = NEX_SPAWN.translateX(-4)
        f.attack(boss)
        assertEquals(HitType.Magic, f.hits().last().second.type)
        assertEquals(listOf(3), f.selectionBounds)
        f.tick(4)
        f.player.coords = NEX_SPAWN.translateX(-1)
        f.attack(boss) // The default upper roll must win over the expired melee chain.
        assertEquals(HitType.Magic, f.hits().last().second.type)
        assertEquals(listOf(3, 3), f.selectionBounds)
    }

    @Test
    fun `final magic retains launch and late prayer policy through real impact processing`() {
        for (launch in listOf(false, true)) for (impact in listOf(false, true)) {
            val f = NexMinionTestWorld()
            f.reachPhase(NexState.ZAROS)
            f.player.coords = NEX_SPAWN.translateX(-4)
            f.protectMagic(launch)
            f.attack(f.npc(11278))
            assertEquals(33, f.deferredHits().single().second.builder.damage)
            f.protectMagic(impact)
            f.processPlayerHits()
            val damage =
                when {
                    launch -> 16
                    impact -> 24
                    else -> 33
                }
            assertEquals(99 - damage, f.player.hitpoints, "launch=$launch impact=$impact")
            assertTrue(f.deferredHits().isEmpty())
            framework().clearInlineMocks()
        }
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
