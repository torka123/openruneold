package org.rsmod.api.bosses.runtime

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcHitParameters
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.access.StandardNpcAccessContextFactory
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcCalculatedProtectionPrayerPlayerHitModifier
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.Hit
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Executes the interpreter and native NPC coroutine; repositories only record output. */
@OptIn(InternalApi::class)
class BossRuntimeRegressionTest {
    private fun timedFixture() =
        Fixture(
            phase = PhaseSpec("main", exitAfter = 4, nextPhase = "arrived"),
            extraPhases = mapOf("arrived" to PhaseSpec("arrived", entry = "arrive")),
            extraAbilities = mapOf("arrive" to Effect.Say("arrived")),
        )

    @Test
    fun `phase replacement removes old deadline but retains unrelated encounter work`() {
        val f = timedFixture()
        f.armPhaseClock()
        var hazard = false
        f.encounter.scheduleAt(f.deps.worldQueues, f.clock, 110) { hazard = true }
        assertEquals(2, f.deps.worldQueues.size)
        f.tick(2)
        f.encounter.transitionTo("arrived", f.clock.cycle)
        assertEquals(1, f.deps.worldQueues.size)
        f.tick(3)
        assertTrue(f.speech.isEmpty())
        f.tick(5)
        assertTrue(hazard)
        assertEquals(0, f.deps.worldQueues.size)
    }

    @Test
    fun `timed entry waits for native busy access and executes once`() {
        val f = timedFixture()
        f.armPhaseClock()
        f.run(Effect.Delay(6))
        f.tick(4)
        assertEquals("main", f.encounter.currentPhaseName)
        assertTrue(f.speech.isEmpty())
        f.tick(2)
        assertEquals(listOf(106 to "arrived"), f.speech)
        assertEquals(0, f.deps.worldQueues.size)
    }

    @Test
    fun `attack callbacks cannot double run the queued timed entry`() {
        val f = timedFixture()
        val events = EventBus()
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        BossCombat.register(context, f.spec, f.deps, "fixture")
        f.armPhaseClock()
        f.tick(3)
        f.clock.cycle = 104
        f.npc.launch { events.publish(f.access(this, events), AiPlayerEvents.Ap2(f.player, f.npc)) }
        assertTrue(f.speech.isEmpty())
        f.clock.cycle = 103
        f.tick(1)
        f.npc.launch { events.publish(f.access(this, events), AiPlayerEvents.Ap2(f.player, f.npc)) }
        assertEquals(listOf(104 to "arrived"), f.speech)
    }

    @Test
    fun `native source retirement and target logout cancel timed presentation`() {
        for (ending in listOf("delete", "respawn", "death", "logout", "scope")) {
            val f = timedFixture()
            val events = EventBus()
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            BossCombat.register(context, f.spec, f.deps, "fixture")
            val scope = org.rsmod.game.hit.HitScope()
            f.player.combatHitScope = scope
            f.armPhaseClock()
            f.tick(2)
            when (ending) {
                "delete" -> events.publish(NpcStateEvents.Delete(f.npc))
                "respawn" -> events.publish(NpcStateEvents.Respawn(f.npc))
                "death" -> f.npc.hitpoints = 0
                "logout" -> f.player.pendingLogout = true
                "scope" -> scope.close()
            }
            f.tick(4)
            assertTrue(f.speech.isEmpty(), ending)
            assertEquals(0, f.deps.worldQueues.size, ending)
        }
    }

    @Test
    fun `declared phase deadline progresses without another attack interaction`() {
        val f =
            Fixture(
                phase = PhaseSpec("main", exitAfter = 4, nextPhase = "arrived"),
                extraPhases = mapOf("arrived" to PhaseSpec("arrived", entry = "arrive")),
                extraAbilities = mapOf("arrive" to Effect.Say("arrived")),
            )
        f.armPhaseClock()
        f.player.coords = f.player.coords.translate(20, 0)
        f.tick(3)
        assertEquals("main", f.encounter.currentPhaseName)
        assertTrue(f.speech.isEmpty())
        f.tick(1)
        assertEquals("arrived", f.encounter.currentPhaseName)
        assertEquals(listOf(104 to "arrived"), f.speech)
        f.tick(5)
        assertEquals(1, f.speech.size)
    }

    @Test
    fun `retiring one summon detaches only that resource and ended state cannot be read`() {
        val f = Fixture()
        val second = Npc(f.npc.type, f.npc.coords)
        f.encounter.own(f.npc)
        f.encounter.own(second)
        f.encounter.state("hazard") { "value" }
        f.encounter.disown(f.npc)
        assertEquals(listOf(second), f.encounter.owned())
        assertEquals("value", f.encounter.stateOrNull<String>("hazard"))
        f.registry.remove(f.npc)
        assertNull(f.encounter.stateOrNull<String>("hazard"))
    }

    @Test
    fun `declarative cadence changes native action lock and next DSL deadline together`() {
        val f = Fixture(nativeCombat = true, random = Rolls(0))
        f.npc.mode = NpcMode.ApPlayer2
        val rate =
            Effect.Choose(
                Selector.WeightedRandom(WeightedRef("short", 1), WeightedRef("normal", 7)),
                mapOf("short" to Effect.SetAttackRate(3), "normal" to Effect.SetAttackRate(null)),
            )
        val spec =
            f.spec.copy(
                abilities =
                    mapOf(
                        "basic" to
                            Effect.Sequence(
                                listOf(
                                    rate,
                                    Effect.NpcAttack(
                                        CombatAttack.NpcMelee(
                                            org.rsmod.api.combat.commons.types.MeleeAttackType.Crush
                                        ),
                                        maxHit = 12,
                                    ),
                                )
                            )
                    ),
                phases =
                    mapOf(
                        "main" to PhaseSpec("main", selector = Selector.Rotation(listOf("basic")))
                    ),
            )
        val registry = EncounterRegistry(f.random)
        val deps =
            with(f.deps) {
                BossDeps(
                    random,
                    accuracy,
                    worldRepo,
                    npcRepo,
                    playerList,
                    mapClock,
                    worldQueues,
                    collision,
                    registry,
                    extensionRegistry,
                    npcCombat,
                )
            }
        val events = EventBus()
        var allowed = false
        BossCombat.register(
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()),
            spec,
            deps,
            canTarget = { _, _ -> allowed },
        )
        fun attack() {
            f.npc.launch {
                events.publish(f.access(this, events), AiPlayerEvents.Ap2(f.player, f.npc))
            }
        }
        attack()
        assertNull(
            registry.existing(f.npc),
            "Rejected targets must not advance/create an encounter",
        )
        allowed = true
        attack()
        assertEquals(103, f.npc.actionDelay)
        f.tick(2)
        attack()
        assertEquals(listOf(100), registry.of(f.npc).recentAbilityCommitTicks())
        f.tick(1)
        attack()
        assertEquals(listOf(100, 103), registry.of(f.npc).recentAbilityCommitTicks())
        assertEquals(listOf(3, 3), registry.of(f.npc).recentAttackTraces().map { it.expectedRate })
        registry.remove(f.npc)
        assertNull(registry.of(f.npc).attackRateOverride)
        assertFailsWith<IllegalArgumentException> { Effect.SetAttackRate(0) }
    }

    @Test
    fun `nested choice never consumes the phase forced attack counter`() {
        val f =
            Fixture(PhaseSpec("main", forceAbilities = listOf(ForcedAbility(0, "special", 1, 1))))
        val normal = Selector.Rotation(listOf("basic"))
        assertEquals("basic", f.encounter.selectAbility(normal, 100, f.player))
        val choice =
            Effect.Choose(
                Selector.Rotation(listOf("left", "right")),
                mapOf("left" to Effect.Say("left"), "right" to Effect.Say("right")),
            )
        f.run(choice)
        f.run(choice)
        assertEquals(listOf(100 to "left", 100 to "right"), f.speech)
        assertEquals("special", f.encounter.selectAbility(normal, 104, f.player))
        assertEquals("basic", f.encounter.selectAbility(normal, 108, f.player))
    }

    @Test
    fun `each nested choice owns its rotation and phase transition resets it`() {
        val f = Fixture()
        val first =
            Effect.Choose(
                Selector.Rotation(listOf("a", "b")),
                mapOf("a" to Effect.Say("a"), "b" to Effect.Say("b")),
            )
        val second =
            Effect.Choose(
                Selector.Rotation(listOf("x", "y")),
                mapOf("x" to Effect.Say("x"), "y" to Effect.Say("y")),
            )
        f.run(first)
        f.run(second)
        f.run(first)
        f.encounter.transitionTo("main", 100)
        f.run(first)
        assertEquals(listOf("a", "x", "b", "a"), f.speech.map { it.second })
        val spec = f.spec.copy(abilities = mapOf("basic" to first))
        assertTrue(org.rsmod.api.bosses.validation.SpecValidator.validate(spec).isEmpty())
    }

    @Test
    fun `animation delay reaches native sequence packet without suspending the attack`() {
        val f = Fixture()
        f.run(Effect.Sequence(listOf(Effect.Anim(seq(11966), 8), Effect.Say("after"))))
        assertEquals(listOf(11966 to 8), f.animations)
        assertEquals(listOf(100 to "after"), f.speech)
    }

    @Test
    fun `repeat waits only between iterations`() {
        val f = Fixture()
        f.run(Effect.Sequence(listOf(Effect.Repeat(3, Effect.Say("pulse"), 2), Effect.Say("done"))))
        f.tick(5)
        assertEquals(
            listOf(100 to "pulse", 102 to "pulse", 104 to "pulse", 104 to "done"),
            f.speech,
        )
    }

    @Test
    fun `parallel starts branches together and joins the longest branch`() {
        val f = Fixture()
        f.run(
            Effect.Sequence(
                listOf(
                    Effect.Parallel(
                        listOf(
                            Effect.Sequence(listOf(Effect.Delay(3), Effect.Say("slow"))),
                            Effect.Sequence(listOf(Effect.Delay(1), Effect.Say("fast"))),
                        )
                    ),
                    Effect.Say("joined"),
                )
            )
        )
        f.tick(5)
        assertEquals(listOf(101 to "fast", 103 to "slow", 103 to "joined"), f.speech)
    }

    @Test
    fun `parallel includes native delays in external handlers without sharing child deadlines`() {
        val f = Fixture()
        f.extensions.register("slow") { access, _, _, _ ->
            access.delay(4)
            access.say("slow")
        }
        f.extensions.register("fast") { access, _, _, _ ->
            access.delay(1)
            access.say("fast")
        }
        f.run(Effect.Parallel(listOf(Effect.External("slow"), Effect.External("fast"))))
        f.tick(6)
        assertEquals(listOf(101 to "fast", 104 to "slow"), f.speech)
    }

    @Test
    fun `removed encounter cannot resume effects after a delay`() {
        val f = Fixture()
        f.run(Effect.Sequence(listOf(Effect.Delay(3), Effect.Say("stale"))))
        f.registry.remove(f.npc)
        f.tick(5)
        assertTrue(f.speech.isEmpty())
    }

    @Test
    fun `parallel children are cancelled when parent coroutine is cancelled`() {
        val f = Fixture()
        var cleaned = 0
        f.extensions.register("wait") { access, _, _, _ ->
            try {
                access.delay(4)
                access.say("stale")
            } finally {
                cleaned++
            }
        }
        f.run(Effect.Parallel(listOf(Effect.External("wait"), Effect.External("wait"))))
        f.npc.cancelActiveCoroutine()
        f.tick(6)
        assertTrue(f.speech.isEmpty())
        assertEquals(2, cleaned)
    }

    @Test
    fun `encounter removal runs cleanup exactly once`() {
        val f = Fixture()
        var cleaned = 0
        f.encounter.onCleanup { cleaned++ }
        f.registry.remove(f.npc)
        f.registry.remove(f.npc)
        assertEquals(1, cleaned)
    }

    @Test
    fun `extension names cannot silently replace a different handler`() {
        val registry = BossExtensionRegistry()
        registry.register("test") { _, _, _, _ -> }
        assertFailsWith<IllegalArgumentException> { registry.register("test") { _, _, _, _ -> } }
    }

    @Test
    fun `rejected boss registrations do not replace either ownership index`() {
        val f = Fixture()
        val other = f.spec.copy(stats = BossStats(attackRate = 9))
        assertFailsWith<IllegalArgumentException> {
            f.registry.register(f.npc.type.id, other, "other")
        }
        assertFailsWith<IllegalArgumentException> { f.registry.register(99999, other, "fixture") }
        assertSame(f.spec, f.registry.registeredSpec(f.npc))
        assertSame(f.spec, f.registry.registeredBoss("fixture"))
        assertNull(f.registry.registeredBoss("other"))
    }

    @Test
    fun `native combat registration respects cadence phase threshold and deletion`() {
        val f = Fixture()
        val events = EventBus()
        val registry = EncounterRegistry(f.random)
        val deps =
            with(f.deps) {
                BossDeps(
                    random,
                    accuracy,
                    worldRepo,
                    npcRepo,
                    playerList,
                    mapClock,
                    worldQueues,
                    collision,
                    registry,
                    extensionRegistry,
                    npcCombat,
                )
            }
        val spec =
            f.spec.copy(
                abilities = mapOf("basic" to Effect.Say("basic"), "entry" to Effect.Say("phase")),
                phases =
                    linkedMapOf(
                        "main" to PhaseSpec("main", selector = Selector.Rotation(listOf("basic"))),
                        "second" to
                            PhaseSpec(
                                "second",
                                entryHp = 0.5,
                                entry = "entry",
                                attackRate = 7,
                                selector = Selector.Rotation(listOf("basic")),
                            ),
                    ),
            )
        BossCombat.register(
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()),
            spec,
            deps,
        )
        fun combatTick() {
            f.npc.launch {
                events.publish(f.access(this, events), AiPlayerEvents.Op2(f.player, f.npc))
            }
        }
        combatTick()
        assertEquals(100, registry.of(f.npc).phaseEnteredTick)
        f.tick(3)
        combatTick()
        assertEquals(listOf(100 to "basic"), f.speech)
        f.tick(1)
        combatTick()
        f.npc.hitpoints = 300
        f.tick(1)
        combatTick()
        assertEquals("second", registry.of(f.npc).currentPhaseName)
        f.tick(5)
        combatTick()
        f.tick(1)
        combatTick()
        assertEquals(
            listOf(100 to "basic", 104 to "basic", 105 to "phase", 111 to "basic"),
            f.speech,
        )
        val retired = registry.of(f.npc)
        var cleaned = 0
        retired.onCleanup { cleaned++ }
        events.publish(NpcStateEvents.Delete(f.npc))
        assertNull(registry.existing(f.npc))
        assertTrue(retired.isEnded)
        assertEquals(1, cleaned)
    }

    @Test
    fun `failed cleanup still retires registry and invokes remaining callbacks`() {
        val f = Fixture()
        var cleaned = false
        f.encounter.onCleanup { error("test failure") }
        f.encounter.onCleanup { cleaned = true }
        assertFailsWith<IllegalStateException> { f.registry.remove(f.npc) }
        assertTrue(cleaned)
        assertTrue(f.encounter.isEnded)
        assertNull(f.registry.existing(f.npc))
    }

    @Test
    fun `periodic forced ability remains due when attacks miss the exact deadline`() {
        val phase = PhaseSpec("main", forceAbilities = listOf(ForcedAbility(10, "special")))
        val f = Fixture(phase)
        f.encounter.transitionTo("main", 100)
        val selector = Selector.Rotation(listOf("basic"))
        assertEquals("basic", f.encounter.selectAbility(selector, 109))
        assertEquals("special", f.encounter.selectAbility(selector, 112))
        assertEquals("basic", f.encounter.selectAbility(selector, 112))
        assertEquals("special", f.encounter.selectAbility(selector, 124))
    }

    @Test
    fun `phase transition resets rotation and forced attack counter`() {
        val phase = PhaseSpec("main", forceAbilities = listOf(ForcedAbility(0, "special", 2, 2)))
        val f = Fixture(phase)
        val selector = Selector.Rotation(listOf("one", "two"))
        assertEquals("one", f.encounter.selectAbility(selector, 100))
        assertEquals("two", f.encounter.selectAbility(selector, 104))
        assertEquals("special", f.encounter.selectAbility(selector, 108))
        f.encounter.transitionTo("main", 110)
        assertEquals("one", f.encounter.selectAbility(selector, 114))
    }

    @Test
    fun `choose evaluates positional condition against current target`() {
        val f = Fixture()
        f.player.coords = f.npc.coords.translate(f.npc.size, 0)
        val choose =
            Effect.Choose(
                Selector.Conditional(listOf(Condition.WithinMeleeRange to "near"), "far"),
                mapOf("near" to Effect.Say("near"), "far" to Effect.Say("far")),
            )
        f.run(choose)
        assertEquals(listOf(100 to "near"), f.speech)
    }

    @Test
    fun `projectile launch graphic is emitted on caster at launch`() {
        val f = Fixture()
        f.run(
            Effect.Projectile(
                "spotanim.firewave_travel",
                travel = "projanim.magic_spell",
                launch = "spotanim.firewave_impact",
            )
        )
        assertEquals(1, f.spots.size)
        assertEquals(1, f.projectiles().size)
    }

    @Test
    fun `weighted boundaries use exactly the injected random draws`() {
        val random = Rolls(0, 1, 2, 4)
        val f = Fixture(random = random)
        val selector = Selector.WeightedRandom(WeightedRef("a", 2), WeightedRef("b", 3))
        assertEquals(
            listOf("a", "a", "b", "b"),
            (0..3).map { f.encounter.selectAbility(selector, 100 + it) },
        )
        assertEquals(listOf(5, 5, 5, 5), random.bounds)
    }

    @Test
    fun `range conditions use occupied footprints and exclude other planes`() {
        val f = Fixture()
        f.player.coords = f.npc.coords.translate(1, 1)
        assertTrue(f.encounter.evaluate(Condition.PlayerEnterRange(0), f.player))
        val adjacentOnly =
            Condition.WithinMeleeRange and Condition.Not(Condition.PlayerEnterRange(0))
        assertFalse(f.encounter.evaluate(adjacentOnly, f.player))
        f.player.coords = f.npc.coords.translate(f.npc.size, 1)
        assertTrue(f.encounter.evaluate(adjacentOnly, f.player))
        f.player.coords = f.npc.coords.translate(f.npc.size + 1, 1)
        assertFalse(f.encounter.evaluate(Condition.PlayerEnterRange(1), f.player))
        f.player.coords = f.npc.coords.copy(level = 1)
        assertFalse(f.encounter.evaluate(Condition.PlayerEnterRange(32), f.player))
        assertFalse(f.encounter.evaluate(Condition.WithinMeleeRange, f.player))
    }

    @Test
    fun `forced attack threshold uses injected random and resets after firing`() {
        val random = Rolls(2, 0)
        val phase = PhaseSpec("main", forceAbilities = listOf(ForcedAbility(0, "special", 2, 4)))
        val f = Fixture(phase, random)
        val selector = Selector.Rotation(listOf("basic"))
        assertEquals(
            listOf("basic", "basic", "basic", "basic", "special", "basic", "basic", "special"),
            (0..7).map { f.encounter.selectAbility(selector, 100 + it) },
        )
    }

    @Test
    fun `removal cancels suspended external branches and unlinks world work immediately`() {
        val f = Fixture()
        var finalized = 0
        f.extensions.register("wait") { access, _, _, _ ->
            try {
                access.delay(20)
                error("stale external")
            } finally {
                finalized++
            }
        }
        f.encounter.schedule(f.deps.worldQueues, 50) { error("stale world task") }
        f.encounter.scheduleAt(f.deps.worldQueues, f.clock, f.clock.cycle + 50) {
            error("stale absolute world task")
        }
        f.run(Effect.Parallel(listOf(Effect.External("wait"), Effect.External("wait"))))
        f.registry.remove(f.npc)
        assertEquals(2, finalized)
        assertEquals(0, f.deps.worldQueues.size)
        assertTrue(f.encounter.isEnded)
        assertFalse(f.encounter.forceNextAbility("basic"))
        f.tick(60)
    }

    @Test
    fun `native queued projectile hit preserves travel plus hit delay and retires before damage`() {
        val f = Fixture()
        f.run(
            Effect.Sequence(
                listOf(
                    Effect.Delay(2),
                    Effect.Projectile(
                        "spotanim.firewave_travel",
                        travel = "projanim.magic_spell",
                        impact = "spotanim.firewave_impact",
                        hit =
                            Effect.Hit(
                                damage = DamageExpr.Fixed(20),
                                type = HitType.Typeless,
                                accuracy = AccuracyMode.Always,
                                delay = 1,
                            ),
                    ),
                )
            )
        )
        f.tick(1)
        assertTrue(f.projectiles().isEmpty())
        f.tick(1)
        val projectile = f.projectiles().single()
        val queue =
            assertNotNull(f.player.queueList.iterator()).let { iterator ->
                try {
                    assertNotNull(iterator.next())
                } finally {
                    iterator.cleanUp()
                }
            }
        val hit = queue.args as Hit
        assertEquals(projectile.serverCycles + 1, queue.remainingCycles)
        assertEquals(20, hit.damage)
        assertTrue(hit.isValidNow())
        f.registry.remove(f.npc)
        assertFalse(hit.isValidNow())
        assertFalse(hit.isQueueArgumentValid())
    }

    @Test
    fun `target selectors honor native damage prayer and owner boundaries`() {
        val f = Fixture()
        val other =
            Player().apply {
                slotId = 2
                uuid = 2L
                assignUid()
                coords = f.player.coords.translate(1, 0)
                statMap.setCurrentLevel("stat.hitpoints", 80)
                statMap.setCurrentLevel("stat.prayer", 5)
            }
        f.players[2] = other
        f.npc.damageContributions.record(other, 40)
        f.npc.damageContributions.record(f.player, 10)
        val chosen = mutableListOf<Long>()
        f.extensions.register("target") { _, _, player, _ -> chosen += assertNotNull(player.uuid) }
        f.run(Effect.OnEach(TargetExpr.HighestDamageDealer, Effect.External("target")))
        f.run(Effect.OnEach(TargetExpr.LowestPrayer, Effect.External("target")))
        f.run(
            Effect.OnEach(
                TargetExpr.TopN(2, TargetExpr.HighestDamageDealer),
                Effect.External("target"),
            )
        )
        assertEquals(listOf(2L, 2L, 2L, 1L), chosen)
        chosen.clear()
        other.coords = other.coords.copy(level = 1)
        f.run(Effect.OnEach(TargetExpr.HighestDamageDealer, Effect.External("target")))
        assertEquals(listOf(1L), chosen)
    }

    private class Rolls(vararg values: Int) : GameRandom {
        private val draws = ArrayDeque(values.toList())
        val bounds = mutableListOf<Int>()

        override fun of(maxExclusive: Int): Int {
            bounds += maxExclusive
            return (draws.removeFirstOrNull() ?: 0).also { require(it in 0 until maxExclusive) }
        }

        override fun of(minInclusive: Int, maxInclusive: Int): Int =
            minInclusive + of(maxInclusive - minInclusive + 1)

        override fun randomDouble(): Double = error("Not used by integer selection")
    }

    @Test
    fun `deleting an actor created as a dormant type retires its active form encounter`() {
        val f = Fixture()
        val events = EventBus()
        BossCombat.register(
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()),
            f.spec,
            f.deps,
            "fixture",
        )
        val actor =
            Npc(
                    assertNotNull(
                        ServerCacheManager.getNpc(
                            "npc.slayer_kraken_boss_whirlpool".asRSCM(RSCMType.NPC)
                        )
                    ),
                    f.npc.coords,
                )
                .apply {
                    slotId = 2
                    assignUid()
                    transmog(f.npc.type, Int.MAX_VALUE)
                }
        val encounter = f.registry.of(actor)
        events.publish(NpcStateEvents.Delete(actor))
        assertTrue(encounter.isEnded)
        assertNull(f.registry.existing(actor))
    }

    @Test
    fun `native action lock does not consume an ability or start its cooldown`() {
        val f = Fixture()
        val events = EventBus()
        val spec =
            f.spec.copy(
                abilities = mapOf("basic" to Effect.Say("attack")),
                phases =
                    mapOf(
                        "main" to PhaseSpec("main", selector = Selector.Rotation(listOf("basic")))
                    ),
            )
        val registry = EncounterRegistry(f.random)
        val deps =
            with(f.deps) {
                BossDeps(
                    random,
                    accuracy,
                    worldRepo,
                    npcRepo,
                    playerList,
                    mapClock,
                    worldQueues,
                    collision,
                    registry,
                    extensionRegistry,
                    npcCombat,
                )
            }
        BossCombat.register(
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()),
            spec,
            deps,
        )
        f.npc.actionDelay = 101
        fun attack() {
            f.npc.launch {
                events.publish(f.access(this, events), AiPlayerEvents.Ap2(f.player, f.npc))
            }
        }
        attack()
        assertTrue(registry.of(f.npc).recentAbilityCommitTicks().isEmpty())
        f.tick(1)
        attack()
        assertEquals(listOf(101 to "attack"), f.speech)
    }

    @Test
    fun `ordinary attack effect preserves native attack output metadata prayer and travel`() {
        val direct = Fixture(random = Rolls(17), nativeCombat = true)
        val interpreted = Fixture(random = Rolls(17), nativeCombat = true)
        val projectile =
            NpcProjectile(
                "spotanim.firewave_travel",
                assertNotNull(
                    ServerCacheManager.getProjectile(
                        "projanim.magic_spell".asRSCM(RSCMType.PROJANIM)
                    )
                ),
            )
        for (f in listOf(direct, interpreted)) {
            f.npc.mode = NpcMode.ApPlayer2
            org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(
                f.player,
                "varbit.prayer_protectfrommagic",
                1,
            )
        }
        direct.npc.launch {
            direct.deps.npcCombat.attack(
                direct.access(this, EventBus()),
                direct.player,
                CombatAttack.NpcMagic(28),
                NpcAttackOverride(
                    maxHit = 28,
                    projectile = projectile,
                    attackRate = 4,
                    hitModifier = NpcBypassProtectionPrayerPlayerHitModifier,
                ),
            )
        }
        interpreted.run(
            Effect.NpcAttack(
                CombatAttack.NpcMagic(28),
                maxHit = 28,
                projectile = projectile,
                protectionPrayer = ProtectionPrayer.Bypass,
            )
        )
        assertEquals(direct.projectiles(), interpreted.projectiles())
        assertEquals(direct.animations, interpreted.animations)
        assertEquals(104, interpreted.npc.actionDelay)
        assertEquals(100, interpreted.npc.vars["varn.lastattack"])
        assertEquals(100, interpreted.player.vars["varp.lastcombat"])
        assertEquals(
            direct.queuedHits().map { it.first to it.second.damage },
            interpreted.queuedHits().map { it.first to it.second.damage },
        )
        val (delay, hit) = interpreted.queuedHits().single()
        assertEquals(17, hit.damage)
        assertEquals(interpreted.projectiles().single().serverCycles, delay)
        assertTrue(hit.isQueueArgumentValid())
        interpreted.registry.remove(interpreted.npc)
        assertFalse(hit.isQueueArgumentValid())
    }

    @Test
    fun `native attack miss does not consume a damage roll`() {
        val random = Rolls()
        val f = Fixture(random = random, nativeCombat = true, accurate = false)
        f.npc.mode = NpcMode.ApPlayer2
        f.run(Effect.NpcAttack(CombatAttack.NpcMagic(28)))
        assertEquals(0, f.queuedHits().single().second.damage)
        assertTrue(random.bounds.isEmpty())
    }

    @Test
    fun `attack parameter resolver preserves native overrides but cannot escape encounter cadence or cancellation`() {
        val f = Fixture(random = Rolls(17), nativeCombat = true)
        f.npc.mode = NpcMode.ApPlayer2
        var impacts = 0
        val scope = org.rsmod.game.hit.HitScope()
        val context =
            org.rsmod.game.hit.HitContext.of(
                org.rsmod.game.hit.HitAttribution(mechanic = "fixture"),
                scope,
            )
        f.extensions.registerAttackParameters("snapshot") { npc, target, data ->
            assertSame(f.npc, npc)
            assertSame(f.player, target)
            assertEquals(28, data)
            NpcAttackOverride(
                maxHit = data as Int,
                attackRate = 999,
                impactDelay = 3,
                damageTransform = { it / 2 },
                hitContext = context,
                onImpact = { _, _ -> impacts++ },
            )
        }
        f.run(
            Effect.NpcAttack(CombatAttack.NpcMagic(1), parameters = "snapshot", parameterData = 28)
        )
        val (delay, hit) = f.queuedHits().single()
        assertEquals(3, delay)
        assertEquals(8, hit.damage)
        assertEquals(104, f.npc.actionDelay)
        assertEquals("fixture", hit.context.attribution.mechanic)
        hit.dispatchImpact(f.player, hit.damage)
        assertEquals(1, impacts)
        assertTrue(hit.isQueueArgumentValid())
        scope.close()
        assertFalse(hit.isQueueArgumentValid())
        assertFailsWith<IllegalArgumentException> {
            f.extensions.registerAttackParameters("snapshot") { _, _, _ -> NpcAttackOverride() }
        }
        val another = Fixture(random = Rolls(17), nativeCombat = true)
        another.npc.mode = NpcMode.ApPlayer2
        another.extensions.registerAttackParameters("snapshot") { _, _, _ ->
            NpcAttackOverride(maxHit = 28)
        }
        another.run(Effect.NpcAttack(CombatAttack.NpcMagic(1), parameters = "snapshot"))
        val retiring = another.queuedHits().single().second
        another.registry.remove(another.npc)
        assertFalse(retiring.isQueueArgumentValid())
    }

    @Test
    fun `native volley rolls hits independently without double arming the attack gate`() {
        for (attack in
            listOf(
                CombatAttack.NpcMagic(28),
                CombatAttack.NpcRanged(
                    org.rsmod.api.combat.commons.types.RangedAttackType.Standard
                ),
                CombatAttack.NpcMelee(org.rsmod.api.combat.commons.types.MeleeAttackType.Crush),
            )) {
            val f =
                Fixture(
                    random = Rolls(17),
                    nativeCombat = true,
                    accuracyResults = listOf(true, false),
                )
            f.npc.mode = NpcMode.ApPlayer2
            var launches = 0
            f.extensions.registerAttackParameters("volley") { _, _, _ ->
                NpcAttackOverride(
                    hitCount = 2,
                    maxHit = 28,
                    impactDelay = 3,
                    onAttack = { launches++ },
                )
            }
            f.run(Effect.NpcAttack(attack, parameters = "volley"))
            assertEquals(listOf(17, 0), f.queuedHits().map { it.second.damage })
            assertEquals(listOf(3, 3), f.queuedHits().map { it.first })
            assertEquals(1, launches)
            assertEquals(2, f.accuracyRolls)
            assertEquals(104, f.npc.actionDelay)
            f.run(Effect.NpcAttack(attack, parameters = "volley"))
            assertEquals(2, f.queuedHits().size)
            assertEquals(1, launches)
            f.registry.remove(f.npc)
            assertTrue(f.queuedHits().all { !it.second.isQueueArgumentValid() })
        }
    }

    @Test
    fun `declarative volley emits every projectile and parameter snapshot can reduce count`() {
        val f = Fixture(random = Rolls(7, 13), nativeCombat = true)
        f.npc.mode = NpcMode.ApPlayer2
        val projectile =
            NpcProjectile(
                "spotanim.firewave_travel",
                assertNotNull(
                    ServerCacheManager.getProjectile(
                        "projanim.magic_spell".asRSCM(RSCMType.PROJANIM)
                    )
                ),
            )
        f.run(Effect.NpcAttack(CombatAttack.NpcMagic(28), projectile = projectile, hitCount = 2))
        assertEquals(listOf(7, 13), f.queuedHits().map { it.second.damage })
        assertEquals(2, f.projectiles().size)
        assertTrue(f.queuedHits().all { it.first == f.projectiles().first().serverCycles })
        val single = Fixture(random = Rolls(8), nativeCombat = true)
        single.npc.mode = NpcMode.ApPlayer2
        single.extensions.registerAttackParameters("single") { _, _, _ ->
            NpcAttackOverride(hitCount = 1)
        }
        single.run(Effect.NpcAttack(CombatAttack.NpcMagic(28), hitCount = 2, parameters = "single"))
        assertEquals(1, single.queuedHits().size)
        assertFailsWith<IllegalArgumentException> { NpcAttackOverride(hitCount = 0) }
        assertFailsWith<IllegalArgumentException> {
            Effect.NpcAttack(CombatAttack.NpcMagic(28), hitCount = -1)
        }
    }

    @Test
    fun `first forced threshold differs from repeat threshold and resets with phase`() {
        val phase =
            PhaseSpec(
                "main",
                forceAbilities = listOf(ForcedAbility(0, "special", 9, 9, firstAttackCount = 3)),
            )
        val f = Fixture(phase = phase)
        val selector = Selector.Rotation(listOf("basic"))
        val result = (1..24).map { f.encounter.selectAbility(selector, 100 + it) }
        assertEquals(
            listOf(4, 14, 24),
            result.mapIndexedNotNull { index, name -> if (name == "special") index + 1 else null },
        )
        f.encounter.transitionTo("main", 130)
        assertEquals(
            listOf("basic", "basic", "basic", "special"),
            (1..4).map { f.encounter.selectAbility(selector, 130 + it) },
        )
    }

    @Test
    fun `heterogeneous native components preserve damage policy modifiers and impact offsets`() {
        for (attack in
            listOf(
                CombatAttack.NpcMagic(28),
                CombatAttack.NpcRanged(
                    org.rsmod.api.combat.commons.types.RangedAttackType.Standard
                ),
                CombatAttack.NpcMelee(org.rsmod.api.combat.commons.types.MeleeAttackType.Crush),
            )) {
            val f = Fixture(random = Rolls(8, 3), nativeCombat = true)
            f.npc.mode = NpcMode.ApPlayer2
            val seenAccuracy = mutableListOf<Boolean>()
            val components =
                listOf(
                    NpcHitParameters(
                        modifier = NpcCalculatedProtectionPrayerPlayerHitModifier(true, 50),
                        damageRoll = { _, accurate, _ ->
                            seenAccuracy += accurate
                            if (accurate) 9 else 0
                        },
                    ),
                    NpcHitParameters(
                        impactOffset = 1,
                        damageRoll = { maximum, accurate, rng ->
                            seenAccuracy += accurate
                            val first = if (accurate) rng.of(maximum + 1) else 0
                            rng.of(first + 1)
                        },
                    ),
                )
            f.run(Effect.NpcAttack(attack, maxHit = 28, impactDelay = 3, hits = components))
            assertEquals(listOf(4, 3), f.queuedHits().map { it.second.damage })
            assertEquals(listOf(3, 4), f.queuedHits().map { it.first })
            assertEquals(listOf(true, true), seenAccuracy)
            assertEquals(104, f.npc.actionDelay)
            assertEquals(2, f.accuracyRolls)
        }
    }

    @Test
    fun `parameter component list overrides declarative components and receives misses`() {
        val f = Fixture(random = Rolls(0), nativeCombat = true, accurate = false)
        f.npc.mode = NpcMode.ApPlayer2
        var sawMiss = false
        f.extensions.registerAttackParameters("components") { _, _, _ ->
            NpcAttackOverride(
                hits =
                    listOf(
                        NpcHitParameters(
                            impactOffset = 2,
                            damageRoll = { _, accurate, rng ->
                                sawMiss = !accurate
                                rng.of(1)
                            },
                        )
                    )
            )
        }
        f.run(
            Effect.NpcAttack(
                CombatAttack.NpcMagic(28),
                impactDelay = 2,
                parameters = "components",
                hits = listOf(NpcHitParameters(), NpcHitParameters()),
            )
        )
        assertTrue(sawMiss)
        assertEquals(listOf(4), f.queuedHits().map { it.first })
        assertEquals(listOf(0), f.queuedHits().map { it.second.damage })
        assertFailsWith<IllegalArgumentException> { NpcHitParameters(impactOffset = -1) }
        assertFailsWith<IllegalArgumentException> { NpcAttackOverride(hits = emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            Effect.NpcAttack(CombatAttack.NpcMagic(28), hits = emptyList())
        }
    }

    @Test
    fun `ending encounter from the first component prevents later projectiles and hits`() {
        val f = Fixture(random = Rolls(7, 9), nativeCombat = true)
        f.npc.mode = NpcMode.ApPlayer2
        var components = 0
        f.extensions.registerAttackParameters("retire") { _, _, _ ->
            NpcAttackOverride(
                hitCount = 2,
                onSuccessfulAttack = { _, _ ->
                    components++
                    f.registry.remove(f.npc)
                },
            )
        }
        f.run(Effect.NpcAttack(CombatAttack.NpcMagic(28), parameters = "retire"))
        assertEquals(1, components)
        assertEquals(1, f.queuedHits().size)
        assertTrue(f.queuedHits().all { !it.second.isQueueArgumentValid() })
    }

    @Test
    fun `explicit forced commit rearms periodic cadence from that actual commit`() {
        val f =
            Fixture(phase = PhaseSpec("main", forceAbilities = listOf(ForcedAbility(42, "basic"))))
        f.encounter.phaseEnteredTick = 100
        assertTrue(f.encounter.forceNextAbility("basic"))
        val forced = assertNotNull(f.encounter.consumeForcedAbility())
        f.encounter.recordAbilityCommit(200, forced, 6)
        assertEquals(
            "ordinary",
            f.encounter.selectAbility(Selector.Rotation(listOf("ordinary")), 206),
        )
        assertEquals("basic", f.encounter.selectAbility(Selector.Rotation(listOf("ordinary")), 242))
    }

    @Test
    fun `phase ability ordinal includes forced commits and resets at transitions`() {
        val f = Fixture()
        f.encounter.recordAbilityCommit(100, "basic", 4)
        assertEquals(1, f.encounter.phaseAbilityCount)
        assertTrue(f.encounter.evaluate(Condition.AbilityCountModulo(2, 1)))
        f.encounter.recordAbilityCommit(104, "forced", 4)
        assertEquals(2, f.encounter.phaseAbilityCount)
        assertTrue(f.encounter.evaluate(Condition.AbilityCountModulo(2, 0)))
        f.encounter.transitionTo("main", 110)
        assertEquals(0, f.encounter.phaseAbilityCount)
        assertFailsWith<IllegalArgumentException> { Condition.AbilityCountModulo(0, 0) }
        assertFailsWith<IllegalArgumentException> { Condition.AbilityCountModulo(2, 2) }
    }

    @Test
    fun `registered nonattack ability arms the native action lock from DSL cadence`() {
        val f = Fixture(phase = PhaseSpec("main", selector = Selector.Rotation(listOf("basic"))))
        val events = EventBus()
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        BossCombat.register(context, f.spec, f.deps, "fixture")
        f.npc.launch { events.publish(f.access(this, events), AiPlayerEvents.Ap2(f.player, f.npc)) }
        assertEquals(100, f.encounter.lastAbilityTick)
        assertEquals(104, f.npc.actionDelay)
    }

    @Test
    fun `source death policy preserves only already launched hits until native encounter teardown`() {
        for (survives in listOf(false, true)) {
            val f = Fixture(random = Rolls(8), nativeCombat = true)
            f.npc.mode = NpcMode.ApPlayer2
            f.run(Effect.NpcAttack(CombatAttack.NpcMagic(28), survivesSourceDeath = survives))
            val hit = f.queuedHits().single().second
            assertTrue(hit.isValidNow())
            f.npc.hitpoints = 0
            assertEquals(survives, hit.isValidNow())
            f.tick(5)
            f.run(Effect.NpcAttack(CombatAttack.NpcMagic(28), survivesSourceDeath = survives))
            assertEquals(1, f.queuedHits().size, "Death must never permit another launch")
            if (survives) {
                f.player.coords = f.player.coords.translate(100, 100)
                assertFalse(hit.isValidNow(), "Target departure still invalidates the hit")
                f.player.coords = CoordGrid(2911, 9566)
                assertTrue(hit.isValidNow())
            }
            f.registry.remove(f.npc)
            assertFalse(hit.isValidNow())
            assertFalse(hit.isQueueArgumentValid())
        }
    }

    @Test
    fun `encounter selection RNG can be injected once without reseeding another lookup`() {
        val f = Fixture(random = Rolls(0))
        val registry = EncounterRegistry(f.random)
        registry.register(f.npc.type.id, f.spec, "seeded")
        val selected = registry.of(f.npc, Rolls(1, 1))
        val choice = Selector.WeightedRandom(WeightedRef("a", 1), WeightedRef("b", 1))
        assertEquals("b", selected.selectAbility(choice, 100))
        assertSame(selected, registry.of(f.npc, Rolls(0)))
        assertEquals("b", selected.selectAbility(choice, 104))
        registry.remove(f.npc)
        assertTrue(selected.isEnded)
        val next = registry.of(f.npc)
        assertNotSame(selected, next)
        assertEquals("a", next.selectAbility(choice, 108))
    }

    private class Fixture(
        phase: PhaseSpec = PhaseSpec("main"),
        val random: GameRandom = DefaultGameRandom(42L),
        nativeCombat: Boolean = false,
        accurate: Boolean = true,
        accuracyResults: List<Boolean>? = null,
        extraPhases: Map<String, PhaseSpec> = emptyMap(),
        extraAbilities: Map<String, Effect> = emptyMap(),
    ) {
        val clock = MapClock().apply { cycle = 100 }
        val speech = mutableListOf<Pair<Int, String>>()
        val animations = mutableListOf<Pair<Int, Int>>()
        val spots = mutableListOf<Int>()
        val npc =
            Npc(assertNotNull(ServerCacheManager.getNpc(12596)), CoordGrid(2906, 9566)).apply {
                slotId = 1
                assignUid()
                currentMapClock = 100
                processedMapClock = 100
                hitpoints = 600
                infoProtocol =
                    object : NpcInfoProtocol by NoopNpcInfo {
                        override fun setSay(text: String) {
                            speech += clock.cycle to text
                        }

                        override fun setSequence(seq: Int, delay: Int) {
                            animations += seq to delay
                        }

                        override fun setSpotanim(
                            spotanim: Int,
                            delay: Int,
                            height: Int,
                            slot: Int,
                        ) {
                            spots += spotanim
                        }
                    }
            }
        val player =
            Player().apply {
                slotId = 1
                uuid = 1L
                assignUid()
                coords = CoordGrid(2911, 9566)
                statMap.setBaseLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.prayer", 99)
                inv = invMap.getOrPut("inv.inv")
                worn = invMap.getOrPut("inv.worn")
            }
        val players = PlayerList().apply { this[1] = player }
        val spec =
            BossSpec(
                listOf(npc.type.internalName),
                BossStats(),
                mapOf("basic" to Effect.NoOp) + extraAbilities,
                mapOf("main" to phase) + extraPhases,
                emptyList(),
            )
        val registry = EncounterRegistry(random).apply { register(npc.type.id, spec, "fixture") }
        val encounter = registry.of(npc)
        val extensions = BossExtensionRegistry()
        val world = mock(WorldRepository::class.java)
        var accuracyRolls = 0
        val accuracy =
            mock(AccuracyFormulae::class.java) { invocation ->
                if (invocation.method.name.startsWith("roll")) {
                    val index = accuracyRolls++
                    accuracyResults?.get(index.coerceAtMost(accuracyResults.lastIndex)) ?: accurate
                } else Answers.RETURNS_DEFAULTS.answer(invocation)
            }
        val deps =
            BossDeps(
                random,
                accuracy,
                world,
                mock(NpcRepository::class.java),
                players,
                clock,
                WorldQueueList(),
                CollisionFlagMap(),
                registry,
                extensions,
                if (nativeCombat) NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world)
                else mock(NvPCombat::class.java),
            )

        fun run(effect: Effect) {
            val events = EventBus()
            npc.launch {
                EffectInterpreter(npc, player, spec, encounter, deps)
                    .run(access(this, events), effect)
            }
        }

        fun armPhaseClock() {
            encounter.transitionTo("main", clock.cycle)
            val events = EventBus()
            val factory =
                mock(StandardNpcAccessContextFactory::class.java) { call ->
                    if (call.method.name == "create")
                        StandardNpcAccessContext(
                            { random },
                            { StandardNpcHitModifier(events) },
                            { StandardNpcHitProcessor(players, events, emptySet()) },
                        )
                    else Answers.RETURNS_DEFAULTS.answer(call)
                }
            BossPhaseClock(deps, StandardNpcAccessLauncher(factory)).bind(npc, player)
        }

        fun access(coroutine: GameCoroutine, events: EventBus): StandardNpcAccess =
            StandardNpcAccess(
                npc,
                coroutine,
                StandardNpcAccessContext(
                    { random },
                    { StandardNpcHitModifier(events) },
                    { StandardNpcHitProcessor(players, events, emptySet()) },
                ),
            )

        fun tick(count: Int) {
            repeat(count) {
                clock.cycle++
                npc.currentMapClock = clock.cycle
                npc.processedMapClock = clock.cycle
                npc.advanceActiveCoroutine()
                val iterator = deps.worldQueues.iterator()
                while (iterator.hasNext()) {
                    val queue = iterator.next()
                    queue.remainingCycles--
                    if (queue.remainingCycles <= 0 && queue.ready()) {
                        iterator.remove()
                        queue.action()
                    }
                }
                iterator.cleanUp()
            }
        }

        fun projectiles(): List<ProjAnim> =
            mockingDetails(world)
                .invocations
                .filter { it.method.name == "projAnim" }
                .map { it.arguments[0] as ProjAnim }

        fun queuedHits(): List<Pair<Int, Hit>> {
            val iterator = player.queueList.iterator() ?: return emptyList()
            return try {
                buildList {
                    while (iterator.hasNext()) {
                        val queue = iterator.next()
                        (queue.args as? Hit)?.let { add(queue.remainingCycles to it) }
                    }
                }
            } finally {
                iterator.cleanUp()
            }
        }
    }

    companion object {
        private fun seq(id: Int): String = RSCM.getReverseMapping(RSCMType.SEQ, id)

        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }

    @org.junit.jupiter.api.AfterEach
    fun releaseNativeFixtures() {
        // Inline mock answers reference complete fixtures, including the native collision map.
        org.mockito.Mockito.framework().clearInlineMocks()
    }
}
