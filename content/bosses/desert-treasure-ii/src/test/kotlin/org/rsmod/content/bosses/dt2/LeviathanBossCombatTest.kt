package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.hit.modify
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle

/** Retained native AI/RNG, NvP launch and impact contracts; no new OSRS timing assumptions. */
@OptIn(InternalApi::class)
class LeviathanBossCombatTest {
    @Test
    fun `forced roar replacing a timed special restores its still due opportunity`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.LEVIATHAN)
        f.activateLeviathan()
        assertTrue(f.manager.forcePhase(f.player, LeviathanSpecial.LIGHTNING_BARRAGE.name))
        f.advance(f.run.specialEndCycle - f.clock.cycle)
        f.manager.tick(f.player)
        val phases = assertNotNull(f.run.leviathanCombatPhases)
        val deadline = f.run.nextSpecialCycle
        f.advance(deadline - f.clock.cycle)
        f.manager.tick(f.player)
        assertEquals(LeviathanAction.SMOKE_BLAST, f.run.leviathanAction)
        assertEquals(0, phases.specialOwner.size)
        f.advance(1)
        assertTrue(f.manager.forcePhase(f.player, LeviathanSpecial.VOLLEY_ROAR.name))
        val roarEnd = f.run.specialEndCycle
        f.advance(roarEnd - f.clock.cycle)
        f.manager.tick(f.player)
        assertEquals(LeviathanAction.SMOKE_BLAST, f.run.leviathanAction)
        assertEquals(roarEnd, phases.enteredTick)
        assertEquals(
            deadline,
            f.run.nextSpecialCycle,
            "Forced roar retains the original due deadline",
        )
        assertEquals(0, phases.specialOwner.size)
        assertEquals(1, phases.owner.size, "Only the resumed smoke completion remains queued")
    }

    @Test
    fun `native roar opportunity waits through stun and fires before the next special boundary`() {
        for (difficulty in Dt2Difficulty.entries) {
            try {
                val f =
                    Dt2NativeCombatFixture(bossKind = Dt2Boss.LEVIATHAN, difficulty = difficulty)
                f.run.attackCounter = 5
                f.activateLeviathan()
                f.attack()
                val phases = assertNotNull(f.run.leviathanCombatPhases)
                val roar = f.run.leviathanRoarCycle
                assertEquals(1, phases.roarOwner.size)
                f.advance(1)
                f.damageBoss(1, shadow = true)
                val stunEnd = f.run.specialEndCycle
                f.advance(roar - f.clock.cycle)
                f.queueProcess.process()
                f.manager.tick(f.player)
                assertEquals(LeviathanAction.STUNNED, f.run.leviathanAction)
                assertEquals(1, phases.roarOwner.size)
                f.advance(stunEnd - f.clock.cycle)
                f.manager.tick(f.player)
                assertEquals(LeviathanAction.STANDARD_VOLLEY, f.run.leviathanAction)
                assertEquals(1, phases.roarOwner.size, "The roar dispatch precedes stun completion")
                f.advance(1)
                f.manager.tick(f.player)
                assertEquals(LeviathanAction.ROAR, f.run.leviathanAction)
                assertEquals(0, phases.roarOwner.size)
                val entered = phases.enteredTick
                f.manager.tick(f.player)
                assertEquals(entered, phases.enteredTick)
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `native idle opportunity deferred by stun fires after same tick completion`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.LEVIATHAN)
        f.activateLeviathan()
        assertTrue(f.manager.forcePhase(f.player, LeviathanSpecial.LIGHTNING_BARRAGE.name))
        f.advance(f.run.specialEndCycle - f.clock.cycle)
        f.manager.tick(f.player)
        val phases = assertNotNull(f.run.leviathanCombatPhases)
        assertEquals(1, phases.specialOwner.size)
        val deadline = f.run.nextSpecialCycle
        f.advance(deadline - 1 - f.clock.cycle)
        f.damageBoss(1, shadow = true)
        val stunEnd = f.run.specialEndCycle
        f.advance(1)
        f.manager.tick(f.player)
        assertEquals(LeviathanAction.STUNNED, f.run.leviathanAction)
        assertEquals(1, phases.specialOwner.size)
        f.advance(stunEnd - f.clock.cycle)
        f.manager.tick(f.player)
        assertEquals(LeviathanAction.SMOKE_BLAST, f.run.leviathanAction)
        assertEquals(stunEnd, phases.enteredTick)
        assertEquals(0, phases.specialOwner.size)
    }

    @Test
    fun `native enrage cancels both pending opportunity owners and cleanup closes them`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.LEVIATHAN)
        f.activateLeviathan()
        assertTrue(f.manager.forcePhase(f.player, LeviathanSpecial.LIGHTNING_BARRAGE.name))
        f.advance(f.run.specialEndCycle - f.clock.cycle)
        f.manager.tick(f.player)
        val phases = assertNotNull(f.run.leviathanCombatPhases)
        assertEquals(1, phases.specialOwner.size)
        f.run.attackCounter = 5
        f.advance(2)
        f.attack()
        assertEquals(1, phases.roarOwner.size)
        f.damageBoss(f.boss.hitpoints - 1)
        assertTrue(f.run.enraged)
        assertEquals(0, phases.roarOwner.size)
        assertEquals(0, phases.specialOwner.size)
        assertEquals(LeviathanAction.ROAR, f.run.leviathanAction)
        f.registry.remove(f.boss)
        assertTrue(phases.roarOwner.isClosed)
        assertTrue(phases.specialOwner.isClosed)
        assertEquals(0, phases.owner.size)
    }

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
        assertEquals(
            leviathanActionPhase(LeviathanAction.LIGHTNING_BARRAGE),
            f.registry.of(f.boss).currentPhaseName,
        )
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
        assertEquals(
            leviathanActionPhase(LeviathanAction.STUNNED),
            f.registry.of(f.boss).currentPhaseName,
        )
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
        assertEquals(
            leviathanActionPhase(LeviathanAction.ROAR),
            f.registry.of(f.boss).currentPhaseName,
        )
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

    @Test
    fun `native special identities complete only at their LateCycle boundary in both modes`() {
        for (difficulty in Dt2Difficulty.entries) for (special in LeviathanSpecial.entries) {
            try {
                val f =
                    Dt2NativeCombatFixture(bossKind = Dt2Boss.LEVIATHAN, difficulty = difficulty)
                f.activateLeviathan()
                assertTrue(f.manager.forcePhase(f.player, special.name))
                val action =
                    when (special) {
                        LeviathanSpecial.VOLLEY_ROAR -> LeviathanAction.ROAR
                        LeviathanSpecial.LIGHTNING_BARRAGE -> LeviathanAction.LIGHTNING_BARRAGE
                        LeviathanSpecial.SMOKE_BLAST -> LeviathanAction.SMOKE_BLAST
                    }
                val phases = assertNotNull(f.run.leviathanCombatPhases)
                assertEquals(leviathanActionPhase(action), phases.encounter.currentPhaseName)
                assertEquals(action, f.run.leviathanAction)
                assertEquals(100, phases.enteredTick)
                val end = 100 + Dt2Rules.leviathanSpecialContract(special).duration
                assertEquals(end, f.run.specialEndCycle)
                f.advance(end - 101)
                f.manager.tick(f.player)
                assertEquals(action, f.run.leviathanAction)
                assertEquals(1, phases.owner.size)
                f.advance(1)
                f.queueProcess.process()
                assertEquals(
                    action,
                    f.run.leviathanAction,
                    "Normal world queues must not complete LateCycle effects",
                )
                f.manager.tick(f.player)
                assertEquals(LeviathanAction.STANDARD_VOLLEY, f.run.leviathanAction)
                assertEquals(end + 2, f.boss.actionDelay)
                assertEquals(0, phases.owner.size)
                assertEquals(
                    if (special == LeviathanSpecial.VOLLEY_ROAR) 0 else 1,
                    f.run.specialIndex,
                )
                if (special != LeviathanSpecial.VOLLEY_ROAR) {
                    val expectedNext = Dt2Rules.leviathanNextSpecial(special)
                    assertEquals(expectedNext, f.run.leviathanNextSpecial)
                    assertTrue(f.manager.forcePhase(f.player, null))
                    assertEquals(expectedNext, f.run.activeSpecial)
                }
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `replacing an actual shadow stun cancels its completion without shortening the new phase`() {
        for (difficulty in Dt2Difficulty.entries) {
            try {
                val f =
                    Dt2NativeCombatFixture(bossKind = Dt2Boss.LEVIATHAN, difficulty = difficulty)
                f.activateLeviathan()
                f.damageBoss(1, shadow = true)
                val phases = assertNotNull(f.run.leviathanCombatPhases)
                val stunEnd = 100 + Dt2Rules.leviathanStunTicks(difficulty)
                assertEquals(stunEnd, f.run.specialEndCycle)
                assertEquals(
                    leviathanActionPhase(LeviathanAction.STUNNED),
                    phases.encounter.currentPhaseName,
                )
                f.advance(1)
                assertTrue(f.manager.forcePhase(f.player, LeviathanSpecial.LIGHTNING_BARRAGE.name))
                assertEquals(
                    1,
                    phases.owner.size,
                    "Replacing the phase physically cancels its old completion",
                )
                val newEnd = f.run.specialEndCycle
                f.advance(stunEnd - f.clock.cycle)
                f.manager.tick(f.player)
                assertEquals(LeviathanAction.LIGHTNING_BARRAGE, f.run.leviathanAction)
                assertEquals(newEnd, f.boss.actionDelay)
                f.advance(newEnd - f.clock.cycle)
                f.manager.tick(f.player)
                assertEquals(LeviathanAction.STANDARD_VOLLEY, f.run.leviathanAction)
                assertEquals(newEnd + 2, f.boss.actionDelay)
                assertEquals(1, f.run.specialIndex)
                assertEquals(LeviathanSpecial.SMOKE_BLAST, f.run.leviathanNextSpecial)
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `normal and awakened stun expiry use the native phase deadline and one tick recovery`() {
        for (difficulty in Dt2Difficulty.entries) {
            try {
                val f =
                    Dt2NativeCombatFixture(bossKind = Dt2Boss.LEVIATHAN, difficulty = difficulty)
                f.activateLeviathan()
                f.damageBoss(1, shadow = true)
                val end = 100 + Dt2Rules.leviathanStunTicks(difficulty)
                val phases = assertNotNull(f.run.leviathanCombatPhases)
                f.advance(end - 101)
                f.manager.tick(f.player)
                assertEquals(LeviathanAction.STUNNED, f.run.leviathanAction)
                f.advance(1)
                f.queueProcess.process()
                assertEquals(LeviathanAction.STUNNED, f.run.leviathanAction)
                f.manager.tick(f.player)
                assertEquals(LeviathanAction.STANDARD_VOLLEY, f.run.leviathanAction)
                assertEquals(end + 1, f.boss.actionDelay)
                assertEquals(0, phases.owner.size)
                f.attack()
                assertEquals(0, f.deferredHits().size)
                f.advance(1)
                f.attack()
                assertEquals(1, f.deferredHits().size)
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `wound retaliation replaces stun completion and chains the axis selected special after roar`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.LEVIATHAN)
        f.activateLeviathan()
        f.damageBoss(1, shadow = true)
        val phases = assertNotNull(f.run.leviathanCombatPhases)
        f.player.coords = f.boss.coords.translate(f.boss.size + 1, -1)
        f.damageBoss(1)
        assertEquals(1, f.run.stunCount)
        assertEquals(LeviathanAction.ROAR, f.run.leviathanAction)
        assertTrue(f.run.leviathanRetaliationPending)
        assertEquals(1, phases.owner.size)
        val selected = assertNotNull(f.run.leviathanNextSpecial)
        val roarEnd = f.run.specialEndCycle
        f.advance(roarEnd - f.clock.cycle)
        f.manager.tick(f.player)
        assertFalse(f.run.leviathanRetaliationPending)
        assertEquals(selected, f.run.activeSpecial)
        assertEquals(roarEnd, phases.enteredTick)
        assertEquals(
            roarEnd + Dt2Rules.leviathanSpecialContract(selected).duration,
            f.run.specialEndCycle,
        )
        assertEquals(
            1,
            phases.owner.size,
            "The newly chained completion must survive the old due batch",
        )
        assertEquals(0, f.run.specialIndex)
    }

    @Test
    fun `tied falling debris precedes smoke completion and preserves exact RNG and queued damage`() {
        for (difficulty in Dt2Difficulty.entries) {
            try {
                val f =
                    Dt2NativeCombatFixture(bossKind = Dt2Boss.LEVIATHAN, difficulty = difficulty)
                val oracle = Dt2SeededRng(42)
                f.activateLeviathan()
                assertTrue(f.manager.forcePhase(f.player, LeviathanSpecial.SMOKE_BLAST.name))
                val end = f.run.specialEndCycle
                f.run.pendingLeviathanDebris +=
                    Dt2EncounterManager.PendingLeviathanDebris(
                        f.player.coords,
                        end,
                        3,
                        false,
                        LocAngle.North,
                        null,
                        0,
                        f.run.generation,
                    )
                f.advance(end - f.clock.cycle)
                f.queueProcess.process()
                assertTrue(f.deferredHits().isEmpty())
                f.manager.tick(f.player)
                repeat(3) { oracle.nextInt(4) } // Landing camera axes precede the smoke explosion.
                val smoke =
                    oracle.nextInt(
                        Dt2Rules.leviathanSmokeMaximumHit(difficulty == Dt2Difficulty.AWAKENED) + 1
                    )
                assertEquals(listOf(3, smoke), f.deferredHits().map { it.second.builder.damage })
                assertTrue(f.run.pendingLeviathanDebris.isEmpty())
                assertTrue(f.run.currentSmokeDebris.isEmpty())
                assertEquals(LeviathanAction.STANDARD_VOLLEY, f.run.leviathanAction)
                assertEquals(LeviathanSpecial.LIGHTNING_BARRAGE, f.run.leviathanNextSpecial)
                assertEquals(oracle.nextInt(Int.MAX_VALUE), f.run.rng.nextInt(Int.MAX_VALUE))
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `native death and protected departure cancel phase completion and fresh entry owns a new queue`() {
        DukeRuntimeTest.cache() // Native entry uses the already established inventory bootstrap.
        for (death in listOf(false, true)) {
            try {
                Dt2NativeLifecycleFixture(Dt2Boss.LEVIATHAN).use { f ->
                    val run = f.enter()
                    run.state = Dt2State.ACTIVE
                    assertTrue(
                        f.manager.forcePhase(run.player, LeviathanSpecial.LIGHTNING_BARRAGE.name)
                    )
                    val phases = assertNotNull(run.leviathanCombatPhases)
                    assertEquals(1, phases.owner.size)
                    if (death) {
                        assertSame(run, f.manager.beginBossDeath(run.boss))
                        assertEquals(LeviathanAction.DYING, run.leviathanAction)
                        f.npcs.del(run.boss, Int.MAX_VALUE)
                        f.manager.finishBossDeath(run.boss, run)
                        assertEquals(LeviathanAction.RESPAWNING, run.leviathanAction)
                    } else assertTrue(f.leave(run.player))
                    assertTrue(phases.owner.isClosed)
                    assertEquals(0, phases.owner.size)
                    f.clock.cycle += 18
                    f.events.publish(GameLifecycle.LateCycle)
                    assertEquals(
                        0,
                        run.specialIndex,
                        "Retired completion cannot advance special history",
                    )
                    if (!death) {
                        val fresh = f.enter(run.player)
                        fresh.state = Dt2State.ACTIVE
                        assertTrue(
                            f.manager.forcePhase(run.player, LeviathanSpecial.SMOKE_BLAST.name)
                        )
                        assertNotSame(phases, fresh.leviathanCombatPhases)
                        assertFalse(assertNotNull(fresh.leviathanCombatPhases).owner.isClosed)
                    }
                }
            } finally {
                framework().clearInlineMocks()
            }
        }
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
