package org.rsmod.content.bosses.vorkath

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.Hit
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Native DSL dispatch characterization; specialized arena branches are recorded, not simulated. */
@OptIn(InternalApi::class)
class VorkathDslCharacterizationTest {
    @Test
    fun `native normal attacks follow six five tick launches then the special without another normal`() {
        val f = Fixture()
        repeat(51) { f.tick() }
        assertEquals(listOf(101, 106, 111, 116, 121, 126), f.launches)
        assertEquals(listOf(131), f.specials)
        assertEquals(6, f.projectiles().size)
        assertEquals("acid", f.registry.of(f.npc).currentPhaseName)
        val expected = VorkathProjectiles.RANGED.build(f.npc, f.player.coords, f.player)
        assertEquals(List(6) { expected }, f.projectiles())
        assertEquals(List(6) { expected.endTime / 30 + 1 }, f.hits().map { it.first })
        assertTrue(f.hits().all { it.second.isValidNow() })
        f.registry.remove(f.npc)
        assertTrue(f.hits().all { !it.second.isQueueArgumentValid() })
    }

    @Test
    fun `DSL selection preserves every weighted boundary with and without melee including underfoot`() {
        for ((tile, adjacent) in
            listOf(
                CoordGrid(2268, 4065) to true,
                CoordGrid(2272, 4057) to false,
                CoordGrid(2272, 4065) to false,
            )) {
            val weights = VorkathRules.standardWeights(adjacent)
            val expected = weights.flatMap { (attack, weight) -> List(weight) { attack.name } }
            for ((roll, attack) in expected.withIndex()) {
                val f = Fixture(roll)
                f.player.coords = tile
                val encounter = f.registry.of(f.npc)
                assertEquals(
                    attack,
                    encounter.selectAbility(encounter.currentPhase!!.selector, 101, f.player),
                    "$tile / $roll",
                )
            }
        }
    }

    private class Fixture(private val selection: Int = 0) {
        val clock = MapClock(100)
        val events = EventBus()
        val npc =
            Npc(assertNotNull(ServerCacheManager.getNpc(8061)), CoordGrid(2269, 4062)).apply {
                slotId = 1
                assignUid()
                hitpoints = 750
                baseHitpointsLvl = 750
                currentMapClock = 100
                processedMapClock = 100
                actionDelay = 101
                mode = dev.openrune.types.NpcMode.ApPlayer2
            }
        val player =
            Player().apply {
                slotId = 1
                uuid = 1L
                assignUid()
                coords = CoordGrid(2272, 4057)
                statMap.setBaseLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.hitpoints", 99)
                inv = invMap.getOrPut("inv.inv")
                worn = invMap.getOrPut("inv.worn")
            }
        val players = PlayerList().apply { this[1] = player }
        val random =
            object : GameRandom {
                override fun of(maxExclusive: Int) = selection.coerceAtMost(maxExclusive - 1)

                override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive

                override fun randomDouble() = 0.0
            }
        val registry = EncounterRegistry(random)
        val extensions = BossExtensionRegistry()
        val world = mock(WorldRepository::class.java)
        val accuracy =
            mock(AccuracyFormulae::class.java) { call ->
                if (call.method.name.startsWith("roll")) true else RETURNS_DEFAULTS.answer(call)
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
                NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world),
            )
        val launches = mutableListOf<Int>()
        val specials = mutableListOf<Int>()

        init {
            extensions.registerAttackParameters("vorkath.normal") { n, p, data ->
                VorkathAttackParameters.resolve(n, p, data as VorkathStandardAttack)
            }
            extensions.register("vorkath.special") { _, _, _, _ ->
                specials += clock.cycle
                registry.of(npc).transitionTo("acid", clock.cycle)
            }
            extensions.register("vorkath.fireball") { _, _, _, _ ->
                error("Not selected in this fixture")
            }
            BossCombat.register(
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()),
                vorkathCombatSpec(),
                deps,
                "vorkath-characterization",
            )
            registry.of(npc).transitionTo("active", clock.cycle)
        }

        fun tick() {
            val count = projectiles().size
            npc.currentMapClock = clock.cycle
            npc.processedMapClock = clock.cycle
            npc.launch {
                events.publish(
                    StandardNpcAccess(
                        npc,
                        this,
                        StandardNpcAccessContext(
                            { random },
                            { StandardNpcHitModifier(events) },
                            { StandardNpcHitProcessor(players, events, emptySet()) },
                        ),
                    ),
                    AiPlayerEvents.Ap2(player, npc),
                )
            }
            if (projectiles().size > count) launches += clock.cycle
            clock.tick()
        }

        fun projectiles() =
            mockingDetails(world)
                .invocations
                .filter { it.method.name == "projAnim" }
                .map { it.arguments[0] as ProjAnim }

        fun hits(): List<Pair<Int, Hit>> {
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
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
