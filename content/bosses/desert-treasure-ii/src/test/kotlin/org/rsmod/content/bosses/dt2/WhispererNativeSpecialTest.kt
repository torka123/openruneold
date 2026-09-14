package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

/** Native manager boundaries against retained special order, timeout and cleanup behavior. */
@OptIn(InternalApi::class)
class WhispererNativeSpecialTest {
    @Test
    fun `real special starts keep the scheduled slot across forced output and melee transitions`() {
        for (difficulty in Dt2Difficulty.entries) for (seed in listOf(42L, 43L)) {
            try {
                val f =
                    Dt2NativeCombatFixture(
                        bossKind = Dt2Boss.WHISPERER,
                        difficulty = difficulty,
                        seed = seed,
                    )
                f.activateWhisperer()
                repeat(6) { slot ->
                    val requested =
                        when (slot) {
                            0 -> WhispererSpecial.SOUL_SIPHON.name.lowercase()
                            2 -> WhispererSpecial.CORRUPTED_SEEDS.name
                            4 -> "invalid"
                            else -> null
                        }
                    val expected =
                        when (slot) {
                            0 -> WhispererSpecial.SOUL_SIPHON
                            2 -> WhispererSpecial.CORRUPTED_SEEDS
                            else -> Dt2Rules.whispererSpecial(slot, seed.toInt())
                        }
                    assertTrue(f.manager.forcePhase(f.player, requested))
                    val phases = assertNotNull(f.run.whispererCombatPhases)
                    assertEquals(
                        expected,
                        f.run.whispererSpecial,
                        "$difficulty seed=$seed slot=$slot",
                    )
                    assertEquals(slot + 1, phases.startedSpecials)
                    assertEquals(
                        whispererSpecialPhase(expected),
                        f.registry.of(f.boss).currentPhaseName,
                    )
                    val start = f.clock.cycle
                    assertEquals(start, phases.enteredTick)
                    f.run.specialProgress = f.run.specialRequired
                    f.manager.tick(f.player)
                    assertEquals(Dt2State.ACTIVE, f.run.state)
                    assertNull(f.run.whispererSpecial)
                    assertEquals(start + 2, f.boss.actionDelay)
                    f.advance(13)
                    f.manager.tick(f.player)
                    assertEquals(Dt2Assets.WHISPERER_ACTIVE_ID, f.boss.visType.id)
                    assertEquals(slot + 1, phases.startedSpecials)
                }
                assertEquals(
                    Dt2SeededRng(seed).nextInt(1000),
                    f.run.rng.nextInt(1000),
                    "Special selection cannot consume the shared hazard/shot RNG",
                )
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `manager completes only at its boundary and final-tick success beats timeout penalties`() {
        for (difficulty in Dt2Difficulty.entries) for (special in
            WhispererSpecial.entries) for (success in listOf(false, true)) {
            try {
                val f =
                    Dt2NativeCombatFixture(bossKind = Dt2Boss.WHISPERER, difficulty = difficulty)
                f.activateWhisperer()
                assertTrue(f.manager.forcePhase(f.player, special.name))
                val phases = assertNotNull(f.run.whispererCombatPhases)
                f.boss.hitpoints -= 100
                val beforeHp = f.boss.hitpoints
                val deadline = 100 + WHISPERER_SPECIAL_DURATIONS.getValue(special)
                f.advance(deadline - 101)
                f.manager.tick(f.player)
                assertEquals(Dt2State.SPECIAL, f.run.state)
                assertTrue(f.deferredHits().isEmpty())
                f.advance(1)
                f.queueProcess.process()
                assertEquals(Dt2State.SPECIAL, f.run.state)
                if (success && special == WhispererSpecial.CORRUPTED_SEEDS) {
                    // The last healthy seed is processed at the very same LateCycle as timeout.
                    f.run.specialProgress = f.run.specialRequired - 1
                    f.run.shadowRealm = true
                    f.player.coords = f.run.whispererSeeds.values.first { it.healthy }.tile
                } else if (success) {
                    f.run.specialProgress = f.run.specialRequired
                }
                f.manager.tick(f.player)
                assertEquals(Dt2State.ACTIVE, f.run.state)
                assertEquals(Dt2Assets.WHISPERER_MELEE_ID, f.boss.visType.id)
                assertEquals(deadline + 2, f.boss.actionDelay)
                assertEquals(0, phases.completionOwner.size)
                assertEquals(1, phases.formOwner.size)
                val expectedDamage =
                    if (success) emptyList()
                    else
                        when (special) {
                            WhispererSpecial.CORRUPTED_SEEDS -> listOf(75)
                            WhispererSpecial.SOUL_SIPHON -> listOf(50)
                            WhispererSpecial.SCREECH -> emptyList()
                        }
                assertEquals(expectedDamage, f.deferredHits().map { it.second.builder.damage })
                assertEquals(
                    beforeHp + if (!success && special == WhispererSpecial.SOUL_SIPHON) 100 else 0,
                    f.boss.hitpoints,
                )
                f.manager.tick(f.player)
                assertEquals(
                    expectedDamage,
                    f.deferredHits().map { it.second.builder.damage },
                    "Completion cannot repeat",
                )
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `melee expiry during screech retains its pulse origin and completion deadline`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.WHISPERER)
        f.activateWhisperer()
        f.manager.forcePhase(f.player, WhispererSpecial.SOUL_SIPHON.name)
        f.run.specialProgress = f.run.specialRequired
        f.manager.tick(f.player)
        f.advance(3)
        f.manager.forcePhase(f.player, WhispererSpecial.SCREECH.name)
        val phases = assertNotNull(f.run.whispererCombatPhases)
        assertEquals(103, phases.enteredTick)
        f.advance(10)
        f.manager.tick(f.player)
        assertEquals(Dt2Assets.WHISPERER_ACTIVE_ID, f.boss.visType.id)
        assertEquals(WhispererSpecial.SCREECH, f.run.whispererSpecial)
        assertEquals(103, phases.enteredTick)
        assertTrue(f.deferredHits().isEmpty())
        for (pulse in listOf(16, 22, 28)) {
            f.advance(103 + pulse - f.clock.cycle)
            f.manager.tick(f.player)
            assertEquals(
                List(listOf(16, 22, 28).indexOf(pulse) + 1) { 45 },
                f.deferredHits().map { it.second.builder.damage },
            )
            assertEquals(Dt2State.SPECIAL, f.run.state)
        }
        assertEquals(0, f.run.sanity)
        assertEquals(131, f.run.whispererInsanityStartCycle)
        f.advance(2)
        f.manager.tick(f.player)
        assertEquals(Dt2State.ACTIVE, f.run.state)
        assertEquals(135, f.boss.actionDelay)
        // The third uncovered pulse exhausts sanity; its native +2 insanity hit precedes
        // special completion at tick 133. This is an independent runtime effect, not a repeat.
        assertEquals(listOf(45, 45, 45, 10), f.deferredHits().map { it.second.builder.damage })
    }

    @Test
    fun `soul completion uses native helper membership and duplicate deaths cannot repeat progress`() {
        val f = Dt2NativeCombatFixture(bossKind = Dt2Boss.WHISPERER)
        f.activateWhisperer()
        f.manager.forcePhase(f.player, WhispererSpecial.SOUL_SIPHON.name)
        val group = f.run.whispererSoulGroups.values.first()
        val souls = f.run.whispererSoulGroups.filterValues { it == group }.keys.take(group.required)
        assertEquals(group.required, souls.size)
        for (soul in souls) {
            f.manager.helperDied(soul)
            val progress = f.run.specialProgress
            val kills = f.run.whispererSoulKills.toMap()
            f.manager.helperDied(soul)
            assertEquals(progress, f.run.specialProgress)
            assertEquals(kills, f.run.whispererSoulKills)
        }
        assertEquals(f.run.specialRequired, f.run.specialProgress)
        f.manager.tick(f.player)
        assertEquals(Dt2State.ACTIVE, f.run.state)
        assertEquals(102, f.boss.actionDelay)
        assertEquals(0, assertNotNull(f.run.whispererCombatPhases).completionOwner.size)
    }

    @Test
    fun `native session retirement cancels the special and original melee window before fresh entry`() {
        for (logout in listOf(false, true)) {
            try {
                Dt2NativeLifecycleFixture(Dt2Boss.WHISPERER).use { f ->
                    val run = f.enter()
                    assertTrue(f.manager.activateWhisperer(run.player, run.boss))
                    f.clock.cycle = 104
                    f.events.publish(GameLifecycle.LateCycle)
                    f.manager.forcePhase(run.player, WhispererSpecial.SOUL_SIPHON.name)
                    run.specialProgress = run.specialRequired
                    f.events.publish(GameLifecycle.LateCycle)
                    f.manager.forcePhase(run.player, WhispererSpecial.SCREECH.name)
                    val old = assertNotNull(run.whispererCombatPhases)
                    assertEquals(1, old.formOwner.size)
                    assertEquals(1, old.completionOwner.size)
                    if (logout) f.events.publish(SessionStateEvent.Logout(run.player))
                    else assertTrue(f.leave(run.player))
                    f.instances.tickReclaim(f.clock.cycle + 1)
                    assertTrue(old.shotOwner.isClosed)
                    assertTrue(old.completionOwner.isClosed)
                    assertTrue(old.formOwner.isClosed)
                    assertEquals(0, old.completionOwner.size)
                    val fresh = f.enter(run.player)
                    assertTrue(f.manager.activateWhisperer(fresh.player, fresh.boss))
                    f.clock.cycle = 108
                    f.events.publish(GameLifecycle.LateCycle)
                    val current = assertNotNull(fresh.whispererCombatPhases)
                    assertNotSame(old, current)
                    assertEquals(0, current.startedSpecials)
                    f.clock.cycle = 140
                    old.expireMeleeForm()
                    old.completeSpecial()
                    f.events.publish(GameLifecycle.LateCycle)
                    assertEquals(Dt2Assets.WHISPERER_ACTIVE_ID, fresh.boss.visType.id)
                    assertEquals(Dt2State.ACTIVE, fresh.state)
                    assertEquals(0, current.startedSpecials)
                }
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @AfterTest fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            val context = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { context.startup() }
        }
    }
}
