package org.rsmod.api.bosses.runtime

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossRuntimeRegressionTest.Fixture
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.random.GameRandom
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

/** Native AI acceptance owns decision commits; the selector does not borrow combat randomness. */
@OptIn(InternalApi::class)
class BossSeededDecisionTest {
    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    @Test
    fun `native launch commits after callbacks and combat RNG while rejection retains prior results`() {
        val random = CombatDraws()
        val h = Harness(seeded(), random = random)
        var reject = false
        val order = mutableListOf<String>()
        h.parameters = {
            NpcAttackOverride(
                hitValidity = HitValidity { !reject },
                onAttack = {
                    order +=
                        "launch:${h.encounter.decisionDrawCount("primary")}:${h.encounter.phaseAbilityCount}"
                    assertEquals(
                        if (h.encounter.phaseAbilityCount == 0) null else "b",
                        h.encounter.decisionResult("primary")?.choice,
                    )
                },
            )
        }
        random.onDraw = {
            order +=
                "damage:${h.encounter.decisionDrawCount("primary")}:${h.encounter.phaseAbilityCount}"
        }
        h.onCommitted = {
            order += "commit:${it.decisionDrawCount("primary")}:${it.phaseAbilityCount}"
        }
        h.attack()
        assertEquals(listOf("launch:0:0", "damage:0:0", "commit:1:1"), order)
        assertEquals(DecisionResult("b", "b", true), h.encounter.decisionResult("primary"))
        assertEquals(104, h.f.npc.actionDelay)
        assertEquals(1, h.f.queuedHits().size)
        repeat(3) { h.attack() }
        assertEquals(3, order.size)
        assertEquals(1L, h.encounter.decisionDrawCount("primary"))

        h.f.tick(4)
        reject = true
        val previous = h.encounter.decisionResult("primary")
        h.attack()
        assertEquals(previous, h.encounter.decisionResult("primary"))
        assertEquals(1L, h.encounter.decisionDrawCount("primary"))
        assertEquals(listOf(100), h.encounter.recentAbilityCommitTicks())
        assertEquals(1, h.f.queuedHits().size)
        assertEquals(3, order.size)
        assertEquals(104, h.f.npc.actionDelay)

        reject = false
        h.attack()
        assertEquals(listOf("launch:1:1", "damage:1:1", "commit:2:2"), order.takeLast(3))
        assertEquals(2L, h.encounter.decisionDrawCount("primary"))
        assertEquals(listOf(100, 104), h.encounter.recentAbilityCommitTicks())
        assertEquals(2, h.f.queuedHits().size)
    }

    @Test
    fun `misses accept the turn but invalid target and native mode rejection do not`() {
        val h = Harness(seeded(), accurate = false)
        h.f.npc.mode = null
        h.attack()
        assertEquals(0L, h.encounter.decisionDrawCount("primary"))
        assertTrue(h.encounter.recentAbilityCommitTicks().isEmpty())
        h.f.npc.mode = NpcMode.ApPlayer2
        h.f.player.statMap.setCurrentLevel("stat.hitpoints", 0)
        h.attack()
        assertEquals(0L, h.encounter.decisionDrawCount("primary"))
        h.f.player.statMap.setCurrentLevel("stat.hitpoints", 99)
        h.f.npc.mode = NpcMode.ApPlayer2
        h.attack()
        assertEquals(1L, h.encounter.decisionDrawCount("primary"))
        assertEquals(0, h.f.queuedHits().single().second.damage)
        assertEquals(1, h.commits)
    }

    @Test
    fun `nested external branch requires acknowledgement and commits it only once`() {
        val h =
            Harness(
                Selector.Rotation(listOf("turn")),
                abilities = mapOf("turn" to nested(seeded(), Effect.External("custom"))),
            )
        var accepted = false
        var calls = 0
        h.f.extensions.register("custom") { _, _, _, _ ->
            calls++
            assertEquals(0L, h.encounter.decisionDrawCount("primary"))
            assertNull(h.encounter.decisionResult("primary"))
            if (accepted) repeat(2) { h.encounter.markDecisionLaunchAccepted() }
        }
        h.attack()
        assertEquals(1, calls)
        assertEquals(0, h.commits)
        assertEquals(0L, h.encounter.decisionDrawCount("primary"))
        assertTrue(h.encounter.recentAbilityCommitTicks().isEmpty())
        accepted = true
        h.attack()
        assertEquals(2, calls)
        assertEquals(1, h.commits)
        assertEquals(1L, h.encounter.decisionDrawCount("primary"))
        assertEquals(DecisionResult("b", "b", true), h.encounter.decisionResult("primary"))
    }

    @Test
    fun `exception or native deletion after acknowledgement discards staged draws and commit callback`() {
        for (delete in listOf(false, true)) {
            try {
                val h =
                    Harness(
                        Selector.Rotation(listOf("turn")),
                        abilities = mapOf("turn" to nested(seeded(), Effect.External("fail"))),
                    )
                val original = h.encounter
                h.f.extensions.register("fail") { _, _, _, _ ->
                    original.markDecisionLaunchAccepted()
                    if (delete) h.events.publish(NpcStateEvents.Delete(h.f.npc))
                    else error("fixture failure after acceptance")
                }
                if (delete) h.attack() else assertFailsWith<IllegalStateException> { h.attack() }
                assertEquals(0L, original.decisionDrawCount("primary"))
                assertNull(original.decisionResult("primary"))
                assertTrue(original.recentAbilityCommitTicks().isEmpty())
                assertEquals(0, h.commits)
                if (delete) {
                    assertTrue(original.isEnded)
                    assertNull(h.f.registry.existing(h.f.npc))
                }
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `salted streams match fixed SplitMix oracle and conditional previous-choice weights`() {
        val selector =
            Selector.SeededWeighted(
                stream = "primary",
                salt = PRIMARY_SALT,
                entries = listOf(WeightedRef("rock"), WeightedRef("standard", 3)),
                entriesAfterPrevious =
                    mapOf("rock" to listOf(WeightedRef("rock"), WeightedRef("standard", 7))),
            )
        val random = CombatDraws()
        val h =
            Harness(
                selector,
                abilities =
                    mapOf(
                        "rock" to Effect.External("accept"),
                        "standard" to Effect.External("accept"),
                    ),
                random = random,
            )
        val actual =
            List(20) {
                h.attack()
                assertEquals((it + 1).toLong(), h.encounter.decisionDrawCount("primary"))
                assertNotNull(h.encounter.decisionResult("primary")).choice.also { h.f.tick(4) }
            }
        // Independently calculated from seed 99 xor 0x13579BDF, SplitMix64 and (mixed ushr 1) %
        // bound.
        assertEquals(
            listOf(
                "standard",
                "standard",
                "rock",
                "standard",
                "rock",
                "standard",
                "standard",
                "standard",
                "rock",
                "standard",
                "rock",
                "standard",
                "standard",
                "standard",
                "standard",
                "standard",
                "standard",
                "standard",
                "standard",
                "rock",
            ),
            actual,
        )
        assertTrue(random.bounds.isEmpty())
    }

    @Test
    fun `nested streams advance independently and accepted substitution clears only result visibility`() {
        val primary = nested(seeded(), Effect.External("accept"))
        val style = nested(seeded("style", STYLE_SALT), Effect.External("accept"))
        val random = CombatDraws()
        val h =
            Harness(
                Selector.Rotation(listOf("both")),
                abilities =
                    mapOf(
                        "both" to Effect.Sequence(listOf(primary, style)),
                        "primary-only" to primary,
                        "style-only" to style,
                        "substitute" to Effect.External("accept"),
                    ),
                random = random,
            )
        h.attack()
        assertEquals(DecisionResult("b", "b", true), h.encounter.decisionResult("primary"))
        assertEquals(DecisionResult("a", "a", true), h.encounter.decisionResult("style"))
        assertEquals(1, h.commits)
        h.f.tick(4)
        assertTrue(h.encounter.forceNextAbility("primary-only"))
        h.attack()
        assertEquals(DecisionResult("b", "b", true), h.encounter.decisionResult("primary"))
        assertNull(h.encounter.decisionResult("style"))
        assertEquals(2L, h.encounter.decisionDrawCount("primary"))
        assertEquals(1L, h.encounter.decisionDrawCount("style"))
        h.f.tick(4)
        assertTrue(h.encounter.forceNextAbility("style-only"))
        h.attack()
        assertNull(h.encounter.decisionResult("primary"))
        assertEquals(DecisionResult("b", "b", true), h.encounter.decisionResult("style"))
        h.f.tick(4)
        assertTrue(h.encounter.forceNextAbility("substitute"))
        h.attack()
        assertNull(h.encounter.decisionResult("primary"))
        assertNull(h.encounter.decisionResult("style"))
        assertEquals(2L, h.encounter.decisionDrawCount("primary"))
        assertEquals(2L, h.encounter.decisionDrawCount("style"))
        assertTrue(random.bounds.isEmpty())
    }

    @Test
    fun `prepared override before or after draw has exact accounting and expires after its tick`() {
        for (drawFirst in listOf(false, true)) {
            try {
                val h =
                    Harness(
                        seeded(prepared = PreparedChoice("forced", drawFirst)),
                        abilities =
                            mapOf(
                                "a" to Effect.External("accept"),
                                "b" to Effect.External("accept"),
                            ),
                    )
                h.encounter.prepareDecisionInputs(h.f.clock.cycle, mapOf("forced" to "a"))
                h.attack()
                assertEquals(
                    DecisionResult("a", if (drawFirst) "b" else null, drawFirst),
                    h.encounter.decisionResult("primary"),
                )
                assertEquals(if (drawFirst) 1L else 0L, h.encounter.decisionDrawCount("primary"))
                h.f.tick(4)
                // A supplied choice for the previous tick cannot override a new opportunity.
                h.encounter.prepareDecisionInputs(h.f.clock.cycle - 1, mapOf("forced" to "a"))
                h.attack()
                assertEquals(DecisionResult("b", "b", true), h.encounter.decisionResult("primary"))
                assertEquals(if (drawFirst) 2L else 1L, h.encounter.decisionDrawCount("primary"))
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `scheduled draw shares the stream without consuming inputs cadence or ordinary results`() {
        val selector = seeded(prepared = PreparedChoice("forced", drawBeforeOverride = true))
        val h = Harness(selector)
        h.parameters = {
            NpcAttackOverride(
                onAttack = {
                    assertFailsWith<IllegalStateException> {
                        h.encounter.selectScheduledDecision(selector, h.f.clock.cycle, h.f.player)
                    }
                }
            )
        }
        h.encounter.prepareDecisionInputs(h.f.clock.cycle, mapOf("forced" to "a"))
        h.attack()
        val ordinary = DecisionResult("a", "b", true)
        assertEquals(ordinary, h.encounter.decisionResult("primary"))
        assertEquals(1L, h.encounter.decisionDrawCount("primary"))
        val scheduled = h.encounter.selectScheduledDecision(selector, h.f.clock.cycle, h.f.player)
        assertEquals(DecisionResult("b", "b", true), scheduled)
        assertEquals(2L, h.encounter.decisionDrawCount("primary"))
        assertEquals(ordinary, h.encounter.decisionResult("primary"))
        assertEquals(1, h.encounter.phaseAbilityCount)
        assertEquals(listOf(100), h.encounter.recentAbilityCommitTicks())
        assertEquals(104, h.f.npc.actionDelay)
        assertEquals(1, h.commits)
        h.f.tick(4)
        h.attack()
        assertEquals(DecisionResult("a", "a", true), h.encounter.decisionResult("primary"))
        assertEquals(3L, h.encounter.decisionDrawCount("primary"))
        assertEquals(listOf(100, 104), h.encounter.recentAbilityCommitTicks())
        assertEquals(2, h.commits)
    }

    @Test
    fun `seed streams persist across phase changes and native replacement starts fresh`() {
        val selector = seeded()
        val h =
            Harness(
                selector,
                abilities =
                    mapOf("a" to Effect.External("accept"), "b" to Effect.External("accept")),
                extraPhases = mapOf("later" to PhaseSpec("later", selector = selector)),
            )
        h.attack()
        val original = h.encounter
        assertEquals(1L, original.decisionDrawCount("primary"))
        h.f.tick(4)
        original.transitionTo("later", h.f.clock.cycle)
        assertEquals(1L, original.decisionDrawCount("primary"))
        h.attack()
        assertEquals(2L, original.decisionDrawCount("primary"))
        assertEquals(1, original.phaseAbilityCount)
        h.events.publish(NpcStateEvents.Delete(h.f.npc))
        assertTrue(original.isEnded)
        val replacement = h.encounter
        assertNotSame(original, replacement)
        assertEquals(0L, replacement.decisionDrawCount("primary"))
        assertNull(replacement.decisionResult("primary"))
        replacement.initializeDecisionSeed(SEED)
        h.f.tick(4)
        h.attack()
        assertEquals(1L, replacement.decisionDrawCount("primary"))
        assertEquals(DecisionResult("b", "b", true), replacement.decisionResult("primary"))
    }

    @Test
    fun `accepted effect transitioning phase commits the stream without counting in the new phase`() {
        val selector = seeded()
        val effect = Effect.Sequence(listOf(nativeAttack(), Effect.TransitionTo("later")))
        val h =
            Harness(
                selector,
                abilities = mapOf("a" to effect, "b" to effect),
                extraPhases = mapOf("later" to PhaseSpec("later", selector = selector)),
            )
        h.onCommitted = {
            assertEquals("later", it.currentPhaseName)
            assertEquals(0, it.phaseAbilityCount)
            assertEquals(1L, it.decisionDrawCount("primary"))
        }
        h.attack()
        assertEquals("later", h.encounter.currentPhaseName)
        assertEquals(0, h.encounter.phaseAbilityCount)
        assertEquals(DecisionResult("b", "b", true), h.encounter.decisionResult("primary"))
        assertEquals(listOf(100), h.encounter.recentAbilityCommitTicks())
        assertEquals("b", h.encounter.recentAttackTraces().single().ability)
        assertEquals(104, h.f.npc.actionDelay)
        assertEquals(1, h.commits)
        assertEquals(1, h.f.queuedHits().size)
    }

    @Test
    fun `rejection restores attack override and prior trace while accepted invalidated components still commit`() {
        val h =
            Harness(
                seeded(),
                abilities =
                    mapOf(
                        "a" to nativeAttack(),
                        "b" to nativeAttack(),
                        "reject" to
                            Effect.Sequence(
                                listOf(Effect.SetAttackRate(9), nested(seeded(), nativeAttack()))
                            ),
                    ),
            )
        var valid = true
        var invalidateAfterLaunch = false
        h.parameters = {
            NpcAttackOverride(
                hitValidity = HitValidity { valid },
                onAttack = { if (invalidateAfterLaunch) valid = false },
            )
        }
        h.attack()
        val previousTraces = h.encounter.recentAttackTraces()
        val previousResult = h.encounter.decisionResult("primary")
        assertEquals(4, previousTraces.single().expectedRate)
        h.f.tick(4)
        valid = false
        assertTrue(h.encounter.forceNextAbility("reject"))
        h.attack()
        assertNull(h.encounter.attackRateOverride)
        assertEquals(4, h.encounter.expectedAttackRate())
        assertEquals(previousTraces, h.encounter.recentAttackTraces())
        assertEquals(previousResult, h.encounter.decisionResult("primary"))
        assertEquals(1L, h.encounter.decisionDrawCount("primary"))
        assertEquals(104, h.f.npc.actionDelay)
        assertEquals(1, h.f.accuracyRolls)

        valid = true
        invalidateAfterLaunch = true
        h.attack()
        assertEquals(2L, h.encounter.decisionDrawCount("primary"))
        assertEquals(2, h.encounter.phaseAbilityCount)
        assertEquals(listOf(100, 104), h.encounter.recentAbilityCommitTicks())
        assertEquals(108, h.f.npc.actionDelay)
        assertEquals(2, h.commits)
        assertEquals(1, h.f.accuracyRolls)
        assertEquals(1, h.f.queuedHits().size)
    }

    private class Harness(
        selector: Selector,
        abilities: Map<String, Effect> = mapOf("a" to nativeAttack(), "b" to nativeAttack()),
        random: GameRandom = CombatDraws(),
        accurate: Boolean = true,
        extraPhases: Map<String, PhaseSpec> = emptyMap(),
    ) {
        val f =
            Fixture(
                phase = PhaseSpec("main", selector = selector),
                random = random,
                nativeCombat = true,
                accurate = accurate,
                extraAbilities = abilities,
                extraPhases = extraPhases,
            )
        val events = EventBus()
        val encounter: BossEncounter
            get() = f.registry.of(f.npc)

        var parameters: () -> NpcAttackOverride = { NpcAttackOverride() }
        var onCommitted: (BossEncounter) -> Unit = {}
        var commits = 0
            private set

        init {
            f.npc.mode = NpcMode.ApPlayer2
            f.extensions.registerAttackParameters("parameters") { _, _, _ -> parameters() }
            f.extensions.register("accept") { _, _, _, _ -> encounter.markDecisionLaunchAccepted() }
            BossCombat.register(
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()),
                f.spec,
                f.deps,
                "fixture",
                onAbilityCommitted = { _, _, value ->
                    commits++
                    onCommitted(value)
                },
            )
            encounter.initializeDecisionSeed(SEED)
        }

        fun attack() {
            f.npc.launch {
                events.publish(f.access(this, events), AiPlayerEvents.Ap2(f.player, f.npc))
            }
        }
    }

    private class CombatDraws : GameRandom {
        val bounds = mutableListOf<Int>()
        var onDraw: () -> Unit = {}

        override fun of(maxExclusive: Int): Int {
            require(maxExclusive > 0)
            bounds += maxExclusive
            onDraw()
            return 0
        }

        override fun of(minInclusive: Int, maxInclusive: Int): Int {
            require(minInclusive <= maxInclusive)
            bounds += maxInclusive - minInclusive + 1
            onDraw()
            return minInclusive
        }

        override fun randomDouble(): Double = error("Seeded decisions must not use combat RNG")
    }

    companion object {
        private const val SEED = 99L
        private const val PRIMARY_SALT = 0x13579BDFL
        private const val STYLE_SALT = 0x2468ACE0L

        private fun seeded(
            stream: String = "primary",
            salt: Long = PRIMARY_SALT,
            prepared: PreparedChoice? = null,
        ) =
            Selector.SeededWeighted(
                stream = stream,
                salt = salt,
                entries = listOf(WeightedRef("a"), WeightedRef("b")),
                preparedChoice = prepared,
            )

        private fun nativeAttack() =
            Effect.NpcAttack(
                CombatAttack.NpcMagic(28),
                maxHit = 28,
                impactDelay = 3,
                parameters = "parameters",
            )

        private fun nested(selector: Selector, effect: Effect) =
            Effect.Sequence(
                listOf(Effect.NoOp, Effect.Choose(selector, mapOf("a" to effect, "b" to effect)))
            )

        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
