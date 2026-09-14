package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.mockito.Mockito.mock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.npc.access.StandardNpcAccessContextFactory
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Native selection and accepted commits against the retained phase-two basic/special rules. */
@OptIn(InternalApi::class)
class WardenPhaseTwoBossSpecTest {
    @Test
    fun `all fourteen history residues preserve seventh specials parity exact base distance and rates`() {
        for (offset in 0 until 14) for (rate in listOf(7, 8, 4)) {
            Fixture().use { f ->
                f.spawn(ElidinisWarden.MAGIC_FORM, offset, rate, delay = 5)
                var due = 105
                repeat(28) { index ->
                    val distance = listOf(0, 1, 2, 3, 6)[index % 5]
                    val count = offset + index + 1
                    f.attempt(due - 1, distance)
                    assertEquals(index, f.launches.size)
                    f.attempt(due, distance)
                    val actual = f.launches.last()
                    assertEquals(
                        expected(count, distance),
                        actual.kind,
                        "offset=$offset count=$count distance=$distance",
                    )
                    assertEquals(count, actual.count)
                    assertEquals(
                        count - 1,
                        actual.previousCount,
                        "Payload observes earlier native commits",
                    )
                    assertEquals(count, f.mirroredCount)
                    assertEquals(index + 1, f.phases.encounter.phaseAbilityCount)
                    assertEquals(due + rate, f.npc.actionDelay)
                    assertEquals(rate, f.phases.encounter.expectedAttackRate())
                    f.attempt(due, distance, op = true)
                    assertEquals(index + 1, f.launches.size)
                    due += rate
                }
                assertEquals(
                    0,
                    f.random.draws,
                    "Prepared geometry and native modulo selection add no RNG",
                )
                assertEquals(
                    0L,
                    f.phases.encounter.decisionDrawCount(WARDEN_PHASE_TWO_DISTANCE_STREAM),
                )
            }
        }
    }

    @Test
    fun `recreated native body continues accepted count and special order at every core return residue`() {
        for (residue in 0 until 14) {
            Fixture().use { f ->
                f.spawn(ElidinisWarden.MAGIC_FORM, 28, 7, delay = 5)
                var due = 105
                repeat(residue) {
                    f.attempt(due, 6)
                    due += 7
                }
                val previousCount = f.mirroredCount
                val oldNpc = f.npc
                val old = f.phases
                f.npcs.del(oldNpc, Int.MAX_VALUE)
                assertTrue(old.encounter.isEnded)
                assertFalse(oldNpc.isSlotAssigned)
                val returnedAt = f.clock.cycle
                val launchCount = f.launches.size
                f.spawn(ElidinisWarden.RANGED_FORM, previousCount, 7, delay = 4)
                assertNotSame(oldNpc, f.npc)
                assertNotSame(old.encounter, f.phases.encounter)
                assertEquals(0, f.phases.encounter.phaseAbilityCount)
                assertEquals(previousCount, f.phases.actionCount)
                f.attempt(returnedAt + 3, 6)
                assertEquals(launchCount, f.launches.size)
                f.attempt(returnedAt + 4, 6)
                assertEquals(expected(previousCount + 1, 6), f.launches.last().kind)
                assertEquals(previousCount + 1, f.mirroredCount)
                assertEquals(listOf(returnedAt + 4), f.phases.encounter.recentAbilityCommitTicks())
                assertFailsWith<IllegalStateException> { old.dispatch(f.player) {} }
            }
        }
    }

    @Test
    fun `all body forms require the exact synchronous owned target and rejected gates preserve history`() {
        for (form in WARDEN_PHASE_TWO_FORMS) {
            Fixture().use { f ->
                f.spawn(form, 6, 4, delay = 5)
                f.attempt(105, 2, owned = false)
                assertTrue(f.launches.isEmpty())
                f.allowed = false
                f.attempt(105, 2)
                assertTrue(f.launches.isEmpty())
                assertEquals(6, f.mirroredCount)
                assertEquals(105, f.npc.actionDelay)
                assertTrue(f.phases.encounter.recentAbilityCommitTicks().isEmpty())
                assertFalse(f.phases.ownsDispatch(f.player))
                val stranger = Player()
                f.phases.dispatch(f.player) { assertFalse(f.phases.ownsDispatch(stranger)) }
                assertFailsWith<IllegalStateException> {
                    f.phases.dispatch(f.player) { error("interrupted before launch") }
                }
                assertFalse(f.phases.ownsDispatch(f.player))
                f.allowed = true
                f.attempt(105, 2)
                assertEquals(WardenPhaseTwoAttackKind.SPECIAL, f.launches.single().kind)
                assertEquals(7, f.mirroredCount)
                assertEquals(109, f.npc.actionDelay)
                assertEquals(0, f.random.draws)
            }
        }
    }

    @Test
    fun `special substitution does not reuse the prior prepared distance and native deletion discards commit`() {
        Fixture().use { f ->
            f.spawn(TumekenWarden.RANGED_FORM, 5, 8, delay = 5)
            f.attempt(105, 2)
            assertEquals(WardenPhaseTwoAttackKind.MELEE, f.launches.single().kind)
            assertEquals(
                WARDEN_PHASE_TWO_NEAR,
                f.phases.encounter.decisionResult(WARDEN_PHASE_TWO_DISTANCE_STREAM)?.choice,
            )
            f.attempt(113, 6)
            assertEquals(WardenPhaseTwoAttackKind.SPECIAL, f.launches.last().kind)
            assertNull(f.phases.encounter.decisionResult(WARDEN_PHASE_TWO_DISTANCE_STREAM))
            val old = f.phases
            f.deleteDuringLaunch = true
            f.attempt(121, 6)
            assertTrue(old.encounter.isEnded)
            assertEquals(7, f.mirroredCount, "No accepted callback after native actor deletion")
            assertEquals(2, old.encounter.phaseAbilityCount)
            assertFalse(old.ownsDispatch(f.player))
        }
    }

    private fun expected(count: Int, distance: Int): WardenPhaseTwoAttackKind =
        if (count % WardensRuleset.BASELINE.phaseTwoAttacksBeforeSpecial == 0)
            WardenPhaseTwoAttackKind.SPECIAL
        else
            when (WardenPhaseTwoMechanics.basicAttack(distance, count).attack) {
                WardenBasicAttack.MELEE -> WardenPhaseTwoAttackKind.MELEE
                WardenBasicAttack.MAGIC -> WardenPhaseTwoAttackKind.MAGIC
                WardenBasicAttack.RANGED -> WardenPhaseTwoAttackKind.RANGED
            }

    private data class Launch(
        val kind: WardenPhaseTwoAttackKind,
        val count: Int,
        val previousCount: Int,
    )

    private class CountingRandom : GameRandom {
        var draws = 0

        override fun of(maxExclusive: Int): Int = 0.also { draws++ }

        override fun of(minInclusive: Int, maxInclusive: Int): Int = minInclusive.also { draws++ }

        override fun randomDouble(): Double = 0.0.also { draws++ }
    }

    private class Fixture : AutoCloseable {
        val clock = MapClock(100)
        val events = EventBus()
        val collision = CollisionFlagMap()
        val npcList = NpcList()
        val players = PlayerList()
        val npcs = NpcRepository(clock, NpcRegistry(npcList, collision, events), npcList)
        val player =
            Player().apply {
                slotId = 1
                uuid = 1
                assignUid()
                coords = CoordGrid(3506, 5400)
                statMap.setBaseLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.hitpoints", 99)
            }
        val random = CountingRandom()
        val registry = EncounterRegistry(random)
        val launches = mutableListOf<Launch>()
        var mirroredCount = 0
        var allowed = true
        var deleteDuringLaunch = false
        lateinit var npc: Npc
        lateinit var phases: WardenPhaseTwoCombatPhases
        private val launcher =
            StandardNpcAccessLauncher(
                StandardNpcAccessContextFactory(
                    random,
                    StandardNpcHitModifier(events),
                    StandardNpcHitProcessor(players, events, emptySet()),
                )
            )

        init {
            players[1] = player
            val accuracy = mock(AccuracyFormulae::class.java)
            val world = mock(WorldRepository::class.java)
            val deps =
                BossDeps(
                    random,
                    accuracy,
                    world,
                    npcs,
                    players,
                    clock,
                    WorldQueueList(),
                    collision,
                    registry,
                    BossExtensionRegistry(),
                    NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world),
                )
            registerWardenPhaseTwoBossCombat(
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()),
                deps,
                canTarget = { source, target ->
                    allowed && source === npc && phases.ownsDispatch(target)
                },
                launch = { source, _, kind, count ->
                    launches += Launch(kind, count, phases.actionCount)
                    if (deleteDuringLaunch) npcs.del(source, Int.MAX_VALUE)
                },
                modifyHit = {},
                onAbilityCommitted = { _, _, _ -> mirroredCount = phases.actionCount },
            )
        }

        fun spawn(form: Int, previous: Int, rate: Int, delay: Int) {
            for (x in listOf(3496, 3504, 3512)) for (z in listOf(5392, 5400, 5408)) {
                collision.allocateIfAbsent(x, z, 0)
            }
            npc =
                Npc(assertNotNull(ServerCacheManager.getNpc(form)), CoordGrid(3500, 5400)).apply {
                    mode = NpcMode.None
                    ignoreCombatInteractions = true
                }
            npcs.add(npc, Int.MAX_VALUE)
            val encounter = registry.of(npc)
            phases =
                encounter.state(WARDEN_PHASE_TWO_COMBAT) {
                    WardenPhaseTwoCombatPhases(encounter, clock, previous, rate)
                }
            phases.start(delay)
            mirroredCount = previous
        }

        fun attempt(tick: Int, distance: Int, owned: Boolean = true, op: Boolean = false) {
            clock.cycle = tick
            npc.currentMapClock = tick
            npc.processedMapClock = tick
            player.coords = npc.coords.translateX(distance)
            phases.prepareOpportunity(player)
            val launch = {
                launcher.launch(npc) {
                    if (op) events.publish(this, AiPlayerEvents.Op2(player, npc))
                    else events.publish(this, AiPlayerEvents.Ap2(player, npc))
                }
            }
            if (owned) phases.dispatch(player, launch) else launch()
        }

        override fun close() {
            npcList.toList().forEach { npcs.del(it, Int.MAX_VALUE) }
        }
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}
