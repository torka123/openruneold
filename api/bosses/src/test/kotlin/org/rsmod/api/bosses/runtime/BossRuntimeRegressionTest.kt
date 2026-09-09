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
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
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

    private class Fixture(
        phase: PhaseSpec = PhaseSpec("main"),
        val random: GameRandom = DefaultGameRandom(42L),
        nativeCombat: Boolean = false,
        accurate: Boolean = true,
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
                mapOf("basic" to Effect.NoOp),
                mapOf("main" to phase),
                emptyList(),
            )
        val registry = EncounterRegistry(random).apply { register(npc.type.id, spec, "fixture") }
        val encounter = registry.of(npc)
        val extensions = BossExtensionRegistry()
        val world = mock(WorldRepository::class.java)
        val accuracy =
            mock(AccuracyFormulae::class.java) { invocation ->
                if (invocation.method.name.startsWith("roll")) accurate
                else Answers.RETURNS_DEFAULTS.answer(invocation)
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
}
