package org.rsmod.api.bosses.runtime

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Executes the interpreter and native NPC coroutine; repositories only record output. */
@OptIn(InternalApi::class)
class BossRuntimeRegressionTest {
    @Test fun `animation delay reaches native sequence packet without suspending the attack`() {
        val f = Fixture()
        f.run(Effect.Sequence(listOf(Effect.Anim(seq(11966), 8), Effect.Say("after"))))
        assertEquals(listOf(11966 to 8), f.animations)
        assertEquals(listOf(100 to "after"), f.speech)
    }

    @Test fun `repeat waits only between iterations`() {
        val f = Fixture()
        f.run(Effect.Sequence(listOf(Effect.Repeat(3, Effect.Say("pulse"), 2), Effect.Say("done"))))
        f.tick(5)
        assertEquals(listOf(100 to "pulse", 102 to "pulse", 104 to "pulse", 104 to "done"), f.speech)
    }

    @Test fun `parallel starts branches together and joins the longest branch`() {
        val f = Fixture()
        f.run(Effect.Sequence(listOf(Effect.Parallel(listOf(
            Effect.Sequence(listOf(Effect.Delay(3), Effect.Say("slow"))),
            Effect.Sequence(listOf(Effect.Delay(1), Effect.Say("fast"))),
        )), Effect.Say("joined"))))
        f.tick(5)
        assertEquals(listOf(101 to "fast", 103 to "slow", 103 to "joined"), f.speech)
    }

    @Test fun `parallel includes native delays in external handlers without sharing child deadlines`() {
        val f = Fixture()
        f.extensions.register("slow") { access, _, _, _ -> access.delay(4); access.say("slow") }
        f.extensions.register("fast") { access, _, _, _ -> access.delay(1); access.say("fast") }
        f.run(Effect.Parallel(listOf(Effect.External("slow"), Effect.External("fast"))))
        f.tick(6)
        assertEquals(listOf(101 to "fast", 104 to "slow"), f.speech)
    }

    @Test fun `removed encounter cannot resume effects after a delay`() {
        val f = Fixture()
        f.run(Effect.Sequence(listOf(Effect.Delay(3), Effect.Say("stale"))))
        f.registry.remove(f.npc)
        f.tick(5)
        assertTrue(f.speech.isEmpty())
    }

    @Test fun `parallel children are cancelled when parent coroutine is cancelled`() {
        val f = Fixture()
        var cleaned = 0
        f.extensions.register("wait") { access, _, _, _ ->
            try { access.delay(4); access.say("stale") } finally { cleaned++ }
        }
        f.run(Effect.Parallel(listOf(Effect.External("wait"), Effect.External("wait"))))
        f.npc.cancelActiveCoroutine()
        f.tick(6)
        assertTrue(f.speech.isEmpty())
        assertEquals(2, cleaned)
    }

    @Test fun `encounter removal runs cleanup exactly once`() {
        val f = Fixture()
        var cleaned = 0
        f.encounter.onCleanup { cleaned++ }
        f.registry.remove(f.npc)
        f.registry.remove(f.npc)
        assertEquals(1, cleaned)
    }

    @Test fun `extension names cannot silently replace a different handler`() {
        val registry = BossExtensionRegistry()
        registry.register("test") { _, _, _, _ -> }
        assertFailsWith<IllegalArgumentException> { registry.register("test") { _, _, _, _ -> } }
    }

    @Test fun `periodic forced ability remains due when attacks miss the exact deadline`() {
        val phase = PhaseSpec("main", forceAbilities = listOf(ForcedAbility(10, "special")))
        val f = Fixture(phase)
        f.encounter.transitionTo("main", 100)
        val selector = Selector.Rotation(listOf("basic"))
        assertEquals("basic", f.encounter.selectAbility(selector, 109))
        assertEquals("special", f.encounter.selectAbility(selector, 112))
        assertEquals("basic", f.encounter.selectAbility(selector, 112))
        assertEquals("special", f.encounter.selectAbility(selector, 124))
    }

    @Test fun `phase transition resets rotation and forced attack counter`() {
        val phase = PhaseSpec("main", forceAbilities = listOf(ForcedAbility(0, "special", 2, 2)))
        val f = Fixture(phase)
        val selector = Selector.Rotation(listOf("one", "two"))
        assertEquals("one", f.encounter.selectAbility(selector, 100))
        assertEquals("two", f.encounter.selectAbility(selector, 104))
        assertEquals("special", f.encounter.selectAbility(selector, 108))
        f.encounter.transitionTo("main", 110)
        assertEquals("one", f.encounter.selectAbility(selector, 114))
    }

    @Test fun `choose evaluates positional condition against current target`() {
        val f = Fixture()
        f.player.coords = f.npc.coords.translate(f.npc.size, 0)
        val choose = Effect.Choose(Selector.Conditional(
            listOf(Condition.WithinMeleeRange to "near"), "far"),
            mapOf("near" to Effect.Say("near"), "far" to Effect.Say("far")))
        f.run(choose)
        assertEquals(listOf(100 to "near"), f.speech)
    }

    @Test fun `projectile launch graphic is emitted on caster at launch`() {
        val f = Fixture()
        f.run(Effect.Projectile("spotanim.firewave_travel", travel = "projanim.magic_spell",
            launch = "spotanim.firewave_impact"))
        assertEquals(1, f.spots.size)
        assertEquals(1, f.projectiles().size)
    }

    private class Fixture(phase: PhaseSpec = PhaseSpec("main")) {
        val clock = MapClock().apply { cycle = 100 }
        val speech = mutableListOf<Pair<Int, String>>()
        val animations = mutableListOf<Pair<Int, Int>>()
        val spots = mutableListOf<Int>()
        val npc = Npc(assertNotNull(ServerCacheManager.getNpc(12596)), CoordGrid(2906, 9566)).apply {
            slotId = 1
            assignUid()
            currentMapClock = 100
            processedMapClock = 100
            hitpoints = 600
            infoProtocol = object : NpcInfoProtocol by NoopNpcInfo {
                override fun setSay(text: String) { speech += clock.cycle to text }
                override fun setSequence(seq: Int, delay: Int) { animations += seq to delay }
                override fun setSpotanim(spotanim: Int, delay: Int, height: Int, slot: Int) { spots += spotanim }
            }
        }
        val player = Player().apply {
            slotId = 1
            uuid = 1L
            assignUid()
            coords = CoordGrid(2911, 9566)
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
        }
        val players = PlayerList().apply { this[1] = player }
        val spec = BossSpec(listOf(npc.type.internalName), BossStats(), mapOf("basic" to Effect.NoOp),
            mapOf("main" to phase), emptyList())
        val registry = EncounterRegistry().apply { register(npc.type.id, spec, "fixture") }
        val encounter = registry.of(npc)
        val extensions = BossExtensionRegistry()
        val world = mock(WorldRepository::class.java)
        val random = DefaultGameRandom(42L)
        val deps = BossDeps(random, mock(AccuracyFormulae::class.java), world,
            mock(NpcRepository::class.java), players, clock, WorldQueueList(), CollisionFlagMap(), registry, extensions)

        fun run(effect: Effect) {
            val events = EventBus()
            val accessContext = StandardNpcAccessContext({ random }, { StandardNpcHitModifier(events) },
                { StandardNpcHitProcessor(players, events, emptySet()) })
            npc.launch {
                EffectInterpreter(npc, player, spec, encounter, deps)
                    .run(StandardNpcAccess(npc, this, accessContext), effect)
            }
        }

        fun tick(count: Int) { repeat(count) { clock.cycle++; npc.currentMapClock = clock.cycle; npc.processedMapClock = clock.cycle; npc.advanceActiveCoroutine() } }
        fun projectiles(): List<ProjAnim> = mockingDetails(world).invocations
            .filter { it.method.name == "projAnim" }.map { it.arguments[0] as ProjAnim }
    }

    companion object {
        private fun seq(id: Int): String = RSCM.getReverseMapping(RSCMType.SEQ, id)
        @BeforeAll @JvmStatic fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
