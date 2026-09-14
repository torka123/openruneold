package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.map.CoordGrid

/** Checkpoint 9b5815be6 phase edges and native BossEncounter clock/commit/cleanup contracts. */
class BabaCombatPhasesTest {
    @Test
    fun `only the native normal phase selects ordinary melee and all phases retain six ticks`() {
        val f = BabaNativePhaseFixture()
        assertEquals(BabaState.WAITING, f.state.state)
        f.state.startIntro()
        for (phase in BABA_NATIVE_PHASES.keys) {
            f.state.state = phase
            assertEquals(phase, f.state.state)
            assertEquals(BABA_NATIVE_PHASES[phase], f.encounter.currentPhaseName)
            assertEquals(6, f.encounter.expectedAttackRate())
            assertEquals(
                if (phase == BabaState.NORMAL_COMBAT) "melee" else null,
                f.encounter.selectAbility(assertNotNull(f.encounter.currentPhase).selector, 100),
            )
        }
        assertEquals(
            BabaState.entries.toSet() -
                setOf(BabaState.WAITING, BabaState.RESETTING, BabaState.DEFEATED),
            BABA_NATIVE_PHASES.keys,
        )
    }

    @Test
    fun `native phase entry resets the committed auto budget without changing outstanding cadence or owners`() {
        val f = BabaNativePhaseFixture()
        f.state.startIntro()
        f.state.startNormalCombat()
        f.encounter.recordAbilityCommit(100, "melee", 6)
        assertEquals(1, f.state.attackCounter)
        f.encounter.recordAbilityCommit(106, "melee", 6)
        assertEquals(BabaTuning.SPECIAL_AFTER_AUTOS.value, f.state.attackCounter)
        f.npc.actionDelay = 112
        var cleanupCount = 0
        f.encounter.onCleanup { cleanupCount++ }
        val owner = f.encounter.state("baba.phase-owner-test") { Any() }

        for (phase in listOf(BabaState.ROCKFALL, BabaState.ROCK_THROW, BabaState.NORMAL_COMBAT)) {
            f.clock.cycle++
            f.state.state = phase
            assertEquals(0, f.state.attackCounter)
            assertEquals(f.clock.cycle, f.encounter.phaseEnteredTick)
            assertEquals(106, f.encounter.lastAbilityTick)
            assertEquals(112, f.npc.actionDelay)
            assertSame(owner, f.encounter.stateOrNull<Any>("baba.phase-owner-test"))
            assertEquals(0, cleanupCount)
            assertFalse(f.encounter.isEnded)
        }
        f.encounter.recordAbilityCommit(112, "melee", 6)
        assertEquals(1, f.state.attackCounter)
        f.registry.remove(f.npc)
        assertEquals(1, cleanupCount)
    }

    @Test
    fun `rolling rows retain one native phase origin while a replacement special reenters`() {
        val f = BabaNativePhaseFixture(seed = 19)
        f.state.startIntro()
        f.state.startNormalCombat()
        f.state.queueCrossedThresholds(300, 100, 300)
        assertEquals(1, f.state.beginNextRollingPhase())
        assertEquals(BabaState.ROLLING_STONES_PREP, f.state.state)
        f.clock.cycle = 103
        f.state.beginRollingSet(boulderdash = false, now = 103)
        assertEquals(103, f.encounter.phaseEnteredTick)
        f.clock.cycle = 111
        f.state.beginRollingSet(boulderdash = false, now = 111)
        assertEquals(103, f.encounter.phaseEnteredTick)
        assertEquals(2, f.state.rollingSet)
        assertEquals(119, f.state.nextBoulderAt)
        f.state.finishRollingPhase()
        assertEquals(BabaState.ROLLING_STONES_RETURN, f.state.state)
        assertEquals(111, f.encounter.phaseEnteredTick)
        f.state.startNormalCombat()
        assertEquals(0, f.state.attackCounter)
        f.state.state = BabaState.SHOCKWAVE
        f.clock.cycle = 112
        f.state.state = BabaState.SHOCKWAVE
        assertEquals(112, f.encounter.phaseEnteredTick)
    }

    @Test
    fun `native removal retires active phase before post deletion defeat and fresh reentry`() {
        val f = BabaNativePhaseFixture()
        f.state.startIntro()
        f.state.startNormalCombat()
        f.encounter.recordAbilityCommit(100, "melee", 6)
        val token = f.state.token()
        f.registry.remove(f.npc)
        assertTrue(f.encounter.isEnded)
        assertEquals(BabaState.RESETTING, f.state.state)
        assertFalse(f.state.isCurrent(token))
        assertEquals(0, f.state.attackCounter)
        assertFailsWith<IllegalStateException> { f.state.state = BabaState.NORMAL_COMBAT }
        f.state.markDefeated()
        assertEquals(BabaState.DEFEATED, f.state.state)
        f.state.reset()
        val fresh = f.registry.of(f.npc, ToaSpecialSelectionRandom(f.state::nextInt))
        f.state.bindNative(fresh, f.clock)
        f.state.startIntro()
        f.state.startNormalCombat()
        assertNotSame(f.encounter, fresh)
        assertEquals(0, f.state.attackCounter)
        assertFalse(f.state.isCurrent(token))
    }

    @Test
    fun `reset detaches old native cleanup and requires a real native owner for active phases`() {
        val unbound = BabaEncounter(UUID(0, 3), 7)
        assertFailsWith<IllegalStateException> { unbound.startIntro() }
        assertEquals(BabaState.WAITING, unbound.state)
        val old = BabaNativePhaseFixture()
        old.state.startIntro()
        old.state.reset()
        val fresh = BabaNativePhaseFixture(UUID(0, 2))
        old.state.bindNative(fresh.encounter, fresh.clock)
        old.state.startIntro()
        old.state.startNormalCombat()
        old.registry.remove(old.npc)
        assertEquals(BabaState.NORMAL_COMBAT, old.state.state)
        assertFalse(fresh.encounter.isEnded)
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}

/** Real native phase owner for the focused geometry tests; no alternate test-only phase state. */
@OptIn(InternalApi::class)
internal class BabaNativePhaseFixture(id: UUID = UUID(0, 1), seed: Long = 42) {
    val clock = MapClock().apply { cycle = 100 }
    val registry = EncounterRegistry(DefaultGameRandom(51))
    val npc =
        Npc(assertNotNull(ServerCacheManager.getNpc(BabaAssets.BABA)), CoordGrid(3500, 9500))
            .apply {
                slotId = 1
                assignUid()
                infoProtocol = NoopNpcInfo
            }
    val state = BabaEncounter(id, seed)
    val encounter: BossEncounter

    init {
        registry.register(BabaAssets.BABA, toaBabaBossSpec(), "toa-baba-phase-test")
        encounter = registry.of(npc, ToaSpecialSelectionRandom(state::nextInt))
        encounter.initializeDecisionSeed(0)
        state.bindNative(encounter, clock)
    }
}
