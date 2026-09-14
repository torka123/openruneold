package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.hit.modify
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType

/** Retained native AI/RNG, NvP launch and impact contracts; no new OSRS timing assumptions. */
@OptIn(InternalApi::class)
class LeviathanBossCombatTest {
    @Test
    fun `each native AI attempt retains RNG across cooldown bites and a special deadline`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.LEVIATHAN)
        val oracle = Dt2SeededRng(42)
        f.activateLeviathan()
        val far = f.player.coords
        val adjacent = f.boss.coords.translate(-1, 2)
        var expectedCounter = 0
        for (offset in 0..13) {
            if (offset > 0) f.advance(1)
            // Includes an accepted bite (3) and rejected bites (4, 10).
            val bite = offset in setOf(3, 4, 10)
            f.player.coords = if (bite) adjacent else far
            val expected =
                if (bite) Dt2AttackStyle.MELEE
                else Dt2Rules.leviathanStyle(0, false, oracle.nextInt(Int.MAX_VALUE))
            val accepted = offset % 3 == 0
            f.attack()
            if (accepted) {
                expectedCounter++
                val hit = f.deferredHits().last()
                assertEquals(expected.hitType(), hit.second.builder.type, "AI offset $offset")
                assertEquals(if (bite) 1 else f.projectiles.last().serverCycles, hit.first)
                assertEquals(f.clock.cycle + 3, f.boss.actionDelay)
            }
            assertEquals(expectedCounter, f.run.attackCounter, "AI offset $offset")
            assertEquals(expectedCounter, f.deferredHits().size)
        }
        assertEquals(5, expectedCounter)
        assertTrue(f.manager.forcePhase(f.player, LeviathanSpecial.LIGHTNING_BARRAGE.name))
        assertNull(f.run.leviathanOpportunity)
        assertEquals(LEVIATHAN_INACTIVE, f.registry.of(f.boss).currentPhaseName)
        val end = f.run.specialEndCycle
        while (f.clock.cycle < end) {
            f.attack()
            assertEquals(5, f.run.attackCounter)
            f.advance(1)
        }
        // Exercise native completion at its deadline; intermediate special damage is unrelated.
        f.manager.tick(f.player)
        assertEquals(LeviathanAction.STANDARD_VOLLEY, f.run.leviathanAction)
        assertEquals(end + 2, f.boss.actionDelay)
        f.player.coords = far
        repeat(3) { offset ->
            if (offset > 0) f.advance(1)
            val expected = Dt2Rules.leviathanStyle(0, false, oracle.nextInt(Int.MAX_VALUE))
            f.attack()
            assertEquals(if (offset == 2) 6 else 5, f.run.attackCounter)
            if (offset == 2)
                assertEquals(expected.hitType(), f.deferredHits().last().second.builder.type)
        }
        assertEquals(f.clock.cycle + 3, f.run.leviathanRoarCycle)
        assertEquals(f.run.leviathanRoarCycle + 1, f.boss.actionDelay)
        assertEquals(
            oracle.nextInt(Int.MAX_VALUE),
            f.run.rng.nextInt(Int.MAX_VALUE),
            "Rejected cooldown attempts still advance the shared encounter RNG; inactive special attempts do not",
        )
    }

    @Test
    fun `normal awakened and enraged phases arm the retained native cadence`() {
        data class Case(
            val difficulty: Dt2Difficulty,
            val counter: Int,
            val enraged: Boolean,
            val rate: Int,
        )
        for (case in
            listOf(
                Case(Dt2Difficulty.NORMAL, 0, false, 3),
                Case(Dt2Difficulty.NORMAL, 6, false, 2),
                Case(Dt2Difficulty.NORMAL, 22, false, 1),
                Case(Dt2Difficulty.NORMAL, 0, true, 2),
                Case(Dt2Difficulty.AWAKENED, 6, false, 2),
                Case(Dt2Difficulty.AWAKENED, 6, true, 1),
            )) {
            try {
                val f =
                    Dt2NativeCombatFixture(
                        bossKind = Dt2Boss.LEVIATHAN,
                        difficulty = case.difficulty,
                    )
                f.run.attackCounter = case.counter
                f.run.enraged = case.enraged
                f.activateLeviathan()
                assertEquals(
                    leviathanOrdinaryPhase(case.rate),
                    f.registry.of(f.boss).currentPhaseName,
                )
                f.attack()
                assertEquals(100 + case.rate, f.boss.actionDelay)
                repeat(case.rate) { offset ->
                    f.advance(1)
                    f.attack()
                    assertEquals(
                        case.counter + if (offset == case.rate - 1) 2 else 1,
                        f.run.attackCounter,
                    )
                }
                assertEquals(100 + 2 * case.rate, f.boss.actionDelay)
                assertEquals(2, f.deferredHits().size)
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `actual shadow stun returns at one tick without an extra ordinary cooldown`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.LEVIATHAN)
        val oracle = Dt2SeededRng(42)
        f.activateLeviathan()
        oracle.nextInt(Int.MAX_VALUE)
        f.attack()
        f.damageBoss(1, shadow = true)
        assertEquals(LeviathanAction.STUNNED, f.run.leviathanAction)
        assertEquals(LEVIATHAN_INACTIVE, f.registry.of(f.boss).currentPhaseName)
        val end = f.run.specialEndCycle
        while (f.clock.cycle < end) {
            f.attack()
            assertEquals(1, f.run.attackCounter)
            f.advance(1)
        }
        f.manager.tick(f.player)
        assertEquals(LeviathanAction.STANDARD_VOLLEY, f.run.leviathanAction)
        assertEquals(end + 1, f.boss.actionDelay)
        oracle.nextInt(Int.MAX_VALUE)
        f.attack()
        assertEquals(1, f.run.attackCounter)
        f.advance(1)
        val expected = Dt2Rules.leviathanStyle(0, false, oracle.nextInt(Int.MAX_VALUE))
        f.attack()
        assertEquals(2, f.run.attackCounter)
        assertEquals(expected.hitType(), f.deferredHits().last().second.builder.type)
        assertEquals(end + 4, f.boss.actionDelay)
        assertEquals(oracle.nextInt(Int.MAX_VALUE), f.run.rng.nextInt(Int.MAX_VALUE))
    }

    @Test
    fun `sixth accepted shot preserves roar guard and next tier resumes two ticks after roar`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.LEVIATHAN)
        f.run.attackCounter = 5
        f.activateLeviathan()
        f.attack()
        assertEquals(6, f.run.attackCounter)
        assertEquals(103, f.run.leviathanRoarCycle)
        assertEquals(104, f.boss.actionDelay)
        f.advance(3)
        f.attack()
        assertEquals(
            6,
            f.run.attackCounter,
            "Guard prevents an AI launch before the same-cycle roar tick",
        )
        f.manager.tick(f.player)
        assertEquals(LeviathanAction.ROAR, f.run.leviathanAction)
        assertEquals(LEVIATHAN_INACTIVE, f.registry.of(f.boss).currentPhaseName)
        assertEquals(107, f.run.specialEndCycle)
        f.advance(4)
        f.manager.tick(f.player)
        assertEquals(LeviathanAction.STANDARD_VOLLEY, f.run.leviathanAction)
        assertEquals(leviathanOrdinaryPhase(2), f.registry.of(f.boss).currentPhaseName)
        assertEquals(109, f.boss.actionDelay)
        f.advance(1)
        f.attack()
        assertEquals(6, f.run.attackCounter)
        f.advance(1)
        f.attack()
        assertEquals(7, f.run.attackCounter)
        assertEquals(111, f.boss.actionDelay)
    }

    @Test
    fun `native orb retains distance flight impact prayer and actual damage callbacks`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.LEVIATHAN)
        f.activateLeviathan()
        f.attack()
        val first = f.deferredHits().single()
        val projectile = f.projectiles.single()
        assertEquals(projectile.serverCycles, first.first)
        assertTrue(first.first > 1)
        assertEquals(0, projectile.sourceIndex)
        assertEquals(550, projectile.startHeight)
        assertEquals(100, projectile.endHeight)
        assertTrue(first.second.builder.damage > 0)
        protectAll(f, true)
        first.second.modifier.modify(first.second.builder, f.player)
        assertEquals(0, first.second.builder.damage, "Protection is read at impact, after launch")
        first.second.builder.build().dispatchImpact(f.player, 0)
        assertTrue(f.run.leviathanPerfectKill)
        f.advance(3)
        f.attack()
        val second = f.deferredHits().last().second
        protectAll(f, false)
        second.modifier.modify(second.builder, f.player)
        assertTrue(second.builder.damage > 0)
        second.builder.build().dispatchImpact(f.player, 1)
        assertFalse(f.run.leviathanPerfectKill)
        assertTrue(LeviathanPerfectFailure.STANDARD_ORB in f.run.leviathanPerfectFailures)
        f.advance(3)
        f.attack()
        val late = f.deferredHits().last().second
        f.run.generation++
        late.modifier.modify(late.builder, f.player)
        assertEquals(
            0,
            late.builder.damage,
            "Retained native generation guard suppresses obsolete impacts",
        )
    }

    @Test
    fun `death retires prepared opportunity and native pending impact rewards no damage`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.LEVIATHAN)
        f.activateLeviathan()
        f.attack()
        val pending = f.deferredHits().single().second
        f.advance(1)
        f.attack()
        assertNotNull(f.run.leviathanOpportunity)
        f.boss.hitpoints = 0
        assertNotNull(f.manager.beginBossDeath(f.boss))
        assertNull(f.run.leviathanOpportunity)
        assertEquals(LEVIATHAN_INACTIVE, f.registry.of(f.boss).currentPhaseName)
        pending.modifier.modify(pending.builder, f.player)
        assertEquals(0, pending.builder.damage)
        f.advance(10)
        f.attack()
        assertEquals(1, f.run.attackCounter)
    }

    private fun protectAll(f: Dt2NativeCombatFixture, enabled: Boolean) {
        for (prayer in listOf("magic", "missiles", "melee")) {
            VarPlayerIntMapSetter.set(
                f.player,
                "varbit.prayer_protectfrom$prayer",
                if (enabled) 1 else 0,
            )
        }
    }

    private fun Dt2AttackStyle.hitType(): HitType =
        when (this) {
            Dt2AttackStyle.MELEE -> HitType.Melee
            Dt2AttackStyle.RANGED -> HitType.Ranged
            Dt2AttackStyle.MAGIC -> HitType.Magic
        }

    private fun Dt2NativeCombatFixture.damageBoss(
        damage: Int,
        shadow: Boolean = false,
    ): HitBuilder {
        val builder =
            HitBuilder(
                type = if (shadow) HitType.Magic else HitType.Melee,
                damage = damage,
                sourceUid = player.uid.packed,
                sourceSlot = player.slotId,
                isFromNpc = false,
                isFromPlayer = true,
                clientDelay = 0,
                righthandType = null,
                secondaryType = if (shadow) RSCM.getRSCM("obj.88_shadow_barrage") else null,
                targetHitmark = 0,
                sourceHitmark = 0,
                publicHitmark = null,
                zeroDamageHitmarkLit = null,
                zeroDamageHitmarkTint = null,
                maxDamageHitmarkLit = null,
                targetMaxDamageThreshold = Int.MAX_VALUE,
                sourceMaxDamageThreshold = Int.MAX_VALUE,
            )
        events.publish(NpcHitEvents.Modify(boss, builder))
        return builder
    }

    @AfterTest
    fun releaseMocks() {
        framework().clearInlineMocks()
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
