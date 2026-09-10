package org.rsmod.api.bosses.runtime

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossRuntimeRegressionTest.Fixture
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.bosses.validation.SpecValidator
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.random.GameRandom
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
class BossContinuationSelectorTest {
    private val selector =
        Selector.RepeatSelected(
            delegate = Selector.WeightedRandom(WeightedRef("basic", 1), WeightedRef("magic", 2)),
            repeatedAbility = "basic",
            extraSelections = 2,
            eligible = Condition.WithinMeleeRange,
            ineligibleAbility = "magic",
        )

    private fun fixture(random: GameRandom = Draws(0, 2)) =
        Fixture(
                phase =
                    PhaseSpec(
                        "main",
                        selector = selector,
                        forceAbilities = listOf(ForcedAbility(0, "special", 5, 5)),
                    ),
                random = random,
                extraAbilities = mapOf("magic" to Effect.NoOp, "special" to Effect.NoOp),
            )
            .also { it.player.coords = it.npc.coords.translateX(-1) }

    @Test
    fun `three ordinary selections use one draw and then select afresh`() {
        val random = Draws(0, 2)
        val f = fixture(random)
        assertEquals(
            listOf("basic", "basic", "basic", "magic"),
            (0..3).map { f.encounter.selectAbility(selector, 100 + 4 * it, f.player) },
        )
        assertEquals(listOf(3, 3), random.bounds)
    }

    @Test
    fun `ineligible opportunity clears continuation without consuming random`() {
        val random = Draws(0, 2)
        val f = fixture(random)
        assertEquals("basic", f.encounter.selectAbility(selector, 100, f.player))
        f.player.coords = f.npc.coords.translateX(-4)
        assertEquals("magic", f.encounter.selectAbility(selector, 104, f.player))
        assertEquals(listOf(3), random.bounds)
        f.player.coords = f.npc.coords.translateX(-1)
        assertEquals("magic", f.encounter.selectAbility(selector, 108, f.player))
        assertEquals(listOf(3, 3), random.bounds)
    }

    @Test
    fun `target replacement starts a new chain and does not inherit old attempts`() {
        val random = Draws(0, 0)
        val f = fixture(random)
        assertEquals("basic", f.encounter.selectAbility(selector, 100, f.player))
        val partner = f.partner()
        repeat(3) {
            assertEquals("basic", f.encounter.selectAbility(selector, 104 + it * 4, partner))
        }
        assertEquals(listOf(3, 3), random.bounds)
    }

    @Test
    fun `motion between ordinary opportunities does not itself cancel a chain`() {
        val random = Draws(0)
        val f = fixture(random)
        assertEquals("basic", f.encounter.selectAbility(selector, 100, f.player))
        f.player.coords = f.npc.coords.translateX(-4)
        f.tick(2)
        f.player.coords = f.npc.coords.translateX(-1)
        assertEquals("basic", f.encounter.selectAbility(selector, 104, f.player))
        assertEquals(listOf(3), random.bounds)
    }

    @Test
    fun `native combat counts fallback and continues chain across forced special`() {
        val random = Draws(0)
        val f = fixture(random)
        val events = EventBus()
        BossCombat.register(
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()),
            f.spec,
            f.deps,
            "fixture",
        )
        fun attack() =
            f.npc.launch {
                events.publish(f.access(this, events), AiPlayerEvents.Ap2(f.player, f.npc))
            }
        f.player.coords = f.npc.coords.translateX(-4)
        repeat(4) {
            attack()
            f.tick(4)
        }
        assertTrue(random.bounds.isEmpty())
        f.player.coords = f.npc.coords.translateX(-1)
        repeat(4) {
            attack()
            val commits = f.encounter.phaseAbilityCount
            attack() // Native action lock must not select or decrement a second time.
            assertEquals(commits, f.encounter.phaseAbilityCount)
            f.tick(4)
        }
        assertEquals(
            listOf("magic", "magic", "magic", "magic", "basic", "special", "basic", "basic"),
            f.encounter.recentAttackTraces().map { it.ability },
        )
        assertEquals((0..7).map { 100 + 4 * it }, f.encounter.recentAbilityCommitTicks())
        assertEquals(listOf(3), random.bounds)
    }

    @Test
    fun `phase reset and native deletion retire continuation and old selector`() {
        val random = Draws(0, 2, 0, 2)
        val f = fixture(random)
        assertEquals("basic", f.encounter.selectAbility(selector, 100, f.player))
        f.encounter.transitionTo("main", 104)
        assertEquals("magic", f.encounter.selectAbility(selector, 104, f.player))
        assertEquals("basic", f.encounter.selectAbility(selector, 108, f.player))
        val events = EventBus()
        BossCombat.register(
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()),
            f.spec,
            f.deps,
            "fixture",
        )
        events.publish(NpcStateEvents.Delete(f.npc))
        assertTrue(f.encounter.isEnded)
        assertNull(f.encounter.selectAbility(selector, 112, f.player))
        assertEquals("magic", f.registry.of(f.npc).selectAbility(selector, 112, f.player))
    }

    @Test
    fun `two encounter identities never share repeat state`() {
        val a = fixture(Draws(0))
        val b = fixture(Draws(2))
        assertEquals("basic", a.encounter.selectAbility(selector, 100, a.player))
        assertEquals("magic", b.encounter.selectAbility(selector, 100, b.player))
        assertEquals("basic", a.encounter.selectAbility(selector, 104, a.player))
    }

    @Test
    fun `delegate rotation and nested choice keep independent state`() {
        val f = fixture()
        val repeat = selector.copy(delegate = Selector.Rotation(listOf("basic", "magic")))
        val a = Effect.Choose(repeat, mapOf("basic" to Effect.NoOp, "magic" to Effect.NoOp))
        val b = Effect.Choose(repeat, a.branches)
        assertEquals(
            listOf("basic", "basic", "basic", "magic"),
            (0..3).map { f.encounter.selectBranch(a, 100 + it * 4, f.player) },
        )
        assertEquals("basic", f.encounter.selectBranch(b, 116, f.player))
        f.encounter.transitionTo("main", 120)
        assertEquals("basic", f.encounter.selectBranch(a, 120, f.player))
        f.registry.remove(f.npc)
        assertNull(f.encounter.selectBranch(b, 124, f.player))
    }

    @Test
    fun `invalid continuation specification fails validation`() {
        assertFailsWith<IllegalArgumentException> { selector.copy(extraSelections = -1) }
        val f = fixture()
        val bad =
            selector.copy(
                repeatedAbility = "missing-repeat",
                ineligibleAbility = "missing-fallback",
                delegate = Selector.Rotation(listOf("missing-delegate")),
            )
        val errors =
            SpecValidator.validate(
                f.spec.copy(phases = mapOf("main" to PhaseSpec("main", selector = bad)))
            )
        assertEquals(3, errors.size)
    }

    @Test
    fun `null target zero repeats and explicitly unbound continuation honor their declarations`() {
        val random = Draws(0, 2, 0)
        val f = fixture(random)
        assertEquals("magic", f.encounter.selectAbility(selector, 100, null))
        assertTrue(random.bounds.isEmpty())
        val zero = selector.copy(extraSelections = 0)
        assertEquals("basic", f.encounter.selectAbility(zero, 104, f.player))
        assertEquals("magic", f.encounter.selectAbility(zero, 108, f.player))
        val unbound = selector.copy(sameTarget = false)
        assertEquals("basic", f.encounter.selectAbility(unbound, 112, f.player))
        assertEquals("basic", f.encounter.selectAbility(unbound, 116, f.partner()))
        assertEquals(listOf(3, 3, 3), random.bounds)
    }

    @Test
    fun `native respawn replaces encounter and does not retain the former actor chain`() {
        val random = Draws(0, 2)
        val f = fixture(random)
        assertEquals("basic", f.encounter.selectAbility(selector, 100, f.player))
        val events = EventBus()
        BossCombat.register(
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()),
            f.spec,
            f.deps,
            "fixture",
        )
        events.publish(NpcStateEvents.Respawn(f.npc))
        val fresh = f.registry.of(f.npc)
        assertNotSame(f.encounter, fresh)
        assertTrue(f.encounter.isEnded)
        assertEquals("magic", fresh.selectAbility(selector, 104, f.player))
        assertEquals(listOf(3, 3), random.bounds)
    }

    private class Draws(vararg values: Int) : GameRandom {
        private val values = ArrayDeque(values.toList())
        val bounds = mutableListOf<Int>()

        override fun of(maxExclusive: Int): Int {
            bounds += maxExclusive
            return values.removeFirst().also { require(it in 0 until maxExclusive) }
        }

        override fun of(minInclusive: Int, maxInclusive: Int): Int = error("Unexpected range draw")

        override fun randomDouble(): Double = error("Unexpected floating draw")
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
