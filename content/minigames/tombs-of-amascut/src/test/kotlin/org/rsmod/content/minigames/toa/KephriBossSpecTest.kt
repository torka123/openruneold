package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.mockito.Mockito.mock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.npc.access.StandardNpcAccessContextFactory
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Real BossCombat selection/commit/locks against the retained pre-migration cycle oracle. */
@OptIn(InternalApi::class)
class KephriBossSpecTest {
    @Test
    fun `native shield and final cycles preserve oracle actions deadlines counts and invocation rolls`() {
        for (shield in 0..KephriEncounter.FINAL_PHASE) {
            try {
                val f = Fixture()
                val initialDelay = if (shield == 0) 4 else 13
                f.phases.resume(shield, initialDelay)
                var due = 100 + initialDelay
                var untilSpecial =
                    when (shield) {
                        0 -> 3
                        1 -> 4
                        else -> 5
                    }
                var nextDung = true
                repeat(40) { index ->
                    val agileCount = listOf(0, 5, 6, 8)[index % 4]
                    val oracle = KephriAttackCycle.next(shield, untilSpecial, nextDung, agileCount)
                    untilSpecial = oracle.attacksUntilSpecial
                    nextDung = oracle.nextSpecialDung
                    f.attempt(due - 1, agileCount)
                    assertEquals(
                        index,
                        f.launches.size,
                        "Before deadline shield=$shield index=$index",
                    )
                    // Rejected targeting cannot consume an ordinary or forced opportunity.
                    f.allowed = false
                    f.attempt(due, agileCount)
                    assertEquals(index, f.launches.size)
                    assertEquals(index, f.phases.actionCount)
                    f.allowed = true
                    f.attempt(due, agileCount)
                    val launch = f.launches.last()
                    assertEquals(oracle.kind, launch.kind, "shield=$shield index=$index")
                    assertEquals(due, launch.tick)
                    assertEquals(index + 1, launch.count)
                    assertEquals(index + 1, f.phases.actionCount)
                    val expectedProfile =
                        KephriInvocationRules.resolve(f.invocations, 5) { bound ->
                            (((f.seed xor (index + 1).toLong()) and Long.MAX_VALUE) % bound).toInt()
                        }
                    assertEquals(expectedProfile, launch.profile)
                    due += oracle.nextDelay
                    assertEquals(due, f.npc.actionDelay)
                    assertEquals(oracle.nextDelay, f.registry.of(f.npc).expectedAttackRate())
                }
                assertEquals(
                    DefaultGameRandom(91L).of(1_000_000),
                    f.random.of(1_000_000),
                    "Fixed forced counts and prepared Agile choice consume no combat RNG",
                )
                assertEquals(0L, f.registry.of(f.npc).decisionDrawCount(KEPHRI_INCUBATION_STREAM))
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `Agile override retains the next logical dung slot after incubation emits dung`() {
        val f = Fixture()
        f.phases.activate()
        var due = 104
        repeat(16) { index ->
            f.attempt(due, if (index == 9) KephriEggs.MASS_INCUBATION_AGILE_LIMIT else 0)
            due = f.npc.actionDelay
        }
        assertEquals(
            listOf(3, 9, 15),
            f.launches.indices.filter { f.launches[it].kind != KephriAttackKind.BOMB },
        )
        assertEquals(
            List(3) { KephriAttackKind.DUNG_STRIKE },
            f.launches.filter { it.kind != KephriAttackKind.BOMB }.map { it.kind },
        )
        assertEquals(16, f.phases.actionCount)
    }

    @Test
    fun `native recovery reset cancels readiness and resumes a fresh four bomb shield cycle`() {
        val f = Fixture()
        f.phases.activate()
        f.attempt(104, 0)
        f.attempt(110, 0)
        f.phases.suspendCombat()
        assertEquals(KEPHRI_INACTIVE, f.registry.of(f.npc).currentPhaseName)
        assertEquals(Int.MAX_VALUE, f.npc.actionDelay)
        f.attempt(200, 0)
        assertEquals(2, f.launches.size)
        f.phases.resume(shield = 1)
        assertEquals(0, f.phases.actionCount)
        f.attempt(212, 0)
        assertEquals(2, f.launches.size)
        var due = 213
        repeat(5) {
            f.attempt(due, 0)
            due = f.npc.actionDelay
        }
        assertEquals(
            List(4) { KephriAttackKind.BOMB } + KephriAttackKind.DUNG_STRIKE,
            f.launches.drop(2).map { it.kind },
        )
        assertEquals((1..5).toList(), f.launches.drop(2).map { it.count })
        assertEquals(250, due)
    }

    @Test
    fun `final transition dung remains outside native ordinary count and actor cleanup retires owner`() {
        val f = Fixture()
        // The manager has already launched the immediate shield-break Dung when this starts.
        f.phases.resume(KephriEncounter.FINAL_PHASE)
        assertTrue(f.launches.isEmpty())
        assertEquals(0, f.phases.actionCount)
        f.attempt(112, 0)
        assertTrue(f.launches.isEmpty())
        f.attempt(113, 0)
        assertEquals(KephriAttackKind.BOMB, f.launches.single().kind)
        assertEquals(1, f.launches.single().count)
        val old = f.registry.of(f.npc)
        f.registry.remove(f.npc)
        assertTrue(old.isEnded)
        assertFailsWith<IllegalStateException> { f.phases.prepareOpportunity(0) }
        val fresh = KephriCombatPhases(f.registry.of(f.npc), f.clock, f.seed)
        assertEquals(0, fresh.actionCount)
        fresh.activate()
        assertEquals(117, f.npc.actionDelay)
    }

    @AfterTest fun releaseMocks() = framework().clearInlineMocks()

    private data class Launch(
        val tick: Int,
        val kind: KephriAttackKind,
        val count: Int,
        val profile: KephriInvocationProfile,
    )

    private class Fixture {
        val seed = 42L
        val invocations =
            setOf(
                ToaInvocation.LIVELY_LARVAE,
                ToaInvocation.BLOWING_MUD,
                ToaInvocation.AERIAL_ASSAULT,
                ToaInvocation.MEDIC,
                ToaInvocation.MORE_OVERLORDS,
            )
        val clock = MapClock(100)
        val random = DefaultGameRandom(91L)
        val events = EventBus()
        val player =
            Player().apply {
                slotId = 1
                uuid = 1
                assignUid()
                coords = CoordGrid(3545, 5406)
            }
        val players = PlayerList().apply { this[1] = player }
        val npc =
            Npc(
                    assertNotNull(ServerCacheManager.getNpc(KephriEncounter.ACTIVE)),
                    CoordGrid(3548, 5408),
                )
                .apply {
                    slotId = 1
                    assignUid()
                    infoProtocol = NoopNpcInfo
                    mode = NpcMode.None
                    ignoreCombatInteractions = true
                }
        val registry = EncounterRegistry(random)
        val launches = mutableListOf<Launch>()
        var allowed = true
        private val accuracy = mock(AccuracyFormulae::class.java)
        private val world = mock(WorldRepository::class.java)
        private val deps =
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
                BossExtensionRegistry(),
                NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world),
            )
        private val launcher =
            StandardNpcAccessLauncher(
                StandardNpcAccessContextFactory(
                    random,
                    StandardNpcHitModifier(events),
                    StandardNpcHitProcessor(players, events, emptySet()),
                )
            )
        val phases: KephriCombatPhases

        init {
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            registerKephriBossCombat(
                context,
                deps,
                canTarget = { source, target -> allowed && source === npc && target === player },
                launch = { _, _, kind, count ->
                    val profile =
                        KephriInvocationRules.resolve(invocations, 5) { bound ->
                            (((seed xor count.toLong()) and Long.MAX_VALUE) % bound).toInt()
                        }
                    launches += Launch(clock.cycle, kind, count, profile)
                },
                modifyHit = {},
            )
            phases = KephriCombatPhases(registry.of(npc), clock, seed)
        }

        fun attempt(tick: Int, agileCount: Int) {
            clock.cycle = tick
            npc.currentMapClock = tick
            npc.processedMapClock = tick
            phases.prepareOpportunity(agileCount)
            launcher.launch(npc) { events.publish(this, AiPlayerEvents.Ap2(player, npc)) }
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
