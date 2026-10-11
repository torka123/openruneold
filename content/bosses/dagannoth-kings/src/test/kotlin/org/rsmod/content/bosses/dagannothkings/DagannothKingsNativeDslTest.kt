package org.rsmod.content.bosses.dagannothkings

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.NpcMode
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Answers
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
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
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Native AI, shared DSL/interpreter and real NvPCombat; retained public-lair constants. */
@OptIn(InternalApi::class)
class DagannothKingsNativeDslTest {
    @Test
    fun `all three kings retain style maximum damage native cadence and one launch per opportunity`() {
        for ((type, style, max) in
            listOf(
                Triple(DagannothKingsAssets.PRIME, HitType.Magic, 50),
                Triple(DagannothKingsAssets.SUPREME, HitType.Ranged, 30),
                Triple(DagannothKingsAssets.REX, HitType.Melee, 26),
            )) {
            try {
                val f = Fixture(type)
                f.attack()
                val hit = f.hits(f.player).single().second
                assertEquals(style, hit.type)
                assertEquals(max, hit.damage)
                assertEquals(104, f.npc.actionDelay)
                assertEquals(if (style == HitType.Melee) 0 else 1, f.projectileCount())
                assertEquals(
                    if (style == HitType.Magic) listOf(2, 1) else listOf(1),
                    f.accuracyTargets,
                )
                repeat(4) { f.attack() }
                assertEquals(1, f.hits(f.player).size)
                f.at(104)
                f.attack()
                assertEquals(2, f.hits(f.player).size)
                assertEquals(108, f.npc.actionDelay)
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @Test
    fun `Prime splash keeps native access randomness one tick delivery and fired hit lifetime`() {
        val f = Fixture(DagannothKingsAssets.PRIME)
        f.attack()
        val main = f.hits(f.player).single().second
        val (delay, splash) = f.hits(f.partner).single()
        assertEquals(1, delay)
        assertEquals(HitType.Magic, splash.type)
        assertEquals(50, splash.damage)
        assertTrue(f.scriptRandom.bounds.isEmpty())
        assertEquals(listOf(51, 51), f.accessRandom.bounds)
        assertTrue(f.selectionRandom.bounds.isEmpty())
        assertTrue(main.isValidNow())
        assertTrue(splash.isValidNow())
        f.npc.hitpoints = 0
        assertTrue(main.isValidNow())
        assertTrue(splash.isValidNow())
        f.events.publish(NpcStateEvents.Delete(f.npc))
        assertFalse(main.isValidNow())
        assertFalse(splash.isValidNow())
        assertNull(f.registry.existing(f.npc))
    }

    @Test
    fun `native respawn retires the previous effect and arms a fresh king program`() {
        val f = Fixture(DagannothKingsAssets.SUPREME)
        f.attack()
        val previous = f.registry.of(f.npc)
        val pending = f.hits(f.player).single().second
        f.events.publish(NpcStateEvents.Respawn(f.npc))
        assertTrue(previous.isEnded)
        assertFalse(pending.isValidNow())
        f.at(104)
        f.attack()
        assertNotSame(previous, f.registry.of(f.npc))
        assertEquals(listOf(104), f.registry.of(f.npc).recentAbilityCommitTicks())
    }

    @Test
    fun `departed target cannot receive the old king projectile`() {
        val f = Fixture(DagannothKingsAssets.PRIME)
        f.attack()
        val pending = f.hits(f.player).single().second
        f.player.coords = f.player.coords.translateX(100)
        assertFalse(pending.isValidNow())
    }

    private class MaximumRandom : GameRandom {
        val bounds = mutableListOf<Int>()

        override fun of(maxExclusive: Int): Int = (maxExclusive - 1).also { bounds += maxExclusive }

        override fun of(minInclusive: Int, maxInclusive: Int): Int =
            maxInclusive.also { bounds += maxInclusive - minInclusive + 1 }

        override fun randomDouble(): Double = 0.99
    }

    private class Fixture(type: String) {
        val clock = MapClock().apply { cycle = 100 }
        val player = player(1, CoordGrid(2901, 4445))
        val partner = player(2, player.coords.translateX(1))
        val players =
            PlayerList().apply {
                this[1] = player
                this[2] = partner
            }
        val npc =
            Npc(assertNotNull(ServerCacheManager.getNpc(type.asRSCM())), CoordGrid(2900, 4445))
                .apply {
                    slotId = 1
                    assignUid()
                    currentMapClock = 100
                    processedMapClock = 100
                    mode = NpcMode.ApPlayer2
                }
        val events = EventBus()
        val selectionRandom = MaximumRandom()
        val accessRandom = MaximumRandom()
        val scriptRandom = MaximumRandom()
        val registry = EncounterRegistry(selectionRandom)
        val accuracyTargets = mutableListOf<Int>()
        val accuracy =
            mock(AccuracyFormulae::class.java) { call ->
                if (call.method.name.startsWith("roll")) {
                    accuracyTargets += (call.arguments[1] as Player).slotId
                    true
                } else Answers.RETURNS_DEFAULTS.answer(call)
            }
        val world = mock(WorldRepository::class.java)
        val native = NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world)
        val deps =
            BossDeps(
                selectionRandom,
                accuracy,
                world,
                mock(NpcRepository::class.java),
                players,
                clock,
                WorldQueueList(),
                CollisionFlagMap(),
                registry,
                BossExtensionRegistry(),
                native,
            )

        init {
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(
                DagannothKingsCombat(
                    deps,
                    native,
                    accuracy,
                    players,
                    scriptRandom,
                    mock(NpcDeath::class.java),
                    world,
                )
            ) {
                context.startup()
            }
        }

        fun attack() {
            npc.launch {
                val access =
                    StandardNpcAccess(
                        npc,
                        this,
                        StandardNpcAccessContext(
                            { accessRandom },
                            { StandardNpcHitModifier(events) },
                            { StandardNpcHitProcessor(players, events, emptySet()) },
                        ),
                    )
                events.publish(access, AiPlayerEvents.Ap2(player, npc))
            }
        }

        fun at(cycle: Int) {
            clock.cycle = cycle
            npc.currentMapClock = cycle
            npc.processedMapClock = cycle
        }

        fun projectileCount() =
            mockingDetails(world).invocations.count { it.method.name == "projAnim" }

        fun hits(player: Player): List<Pair<Int, Hit>> {
            val iterator = player.queueList.iterator() ?: return emptyList()
            return try {
                buildList {
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        (entry.args as? Hit)?.let { add(entry.remainingCycles to it) }
                    }
                }
            } finally {
                iterator.cleanUp()
            }
        }

        private fun player(slot: Int, tile: CoordGrid) =
            Player().apply {
                slotId = slot
                uuid = slot.toLong()
                assignUid()
                coords = tile
                statMap.setBaseLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.prayer", 99)
                inv = invMap.getOrPut("inv.inv")
                worn = invMap.getOrPut("inv.worn")
            }
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
