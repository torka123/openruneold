package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.rsmod.api.game.process.npc.NpcQueueProcessor
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.npc.access.StandardNpcAccessContextFactory
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onResolveNpcHit
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.*
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Native queue/processor contract required by S02; phase policies here are test probes, not OSRS
 * formulas.
 */
class NexNativeHitQueueTest {
    @Test
    fun `two queued hits see a phase change only after the first actual impact`() {
        val f = Fixture()
        var protected = false
        val states = mutableListOf<Boolean>()
        f.resolve {
            states += protected
            damage = if (protected) 0 else damage.coerceAtMost(npc.hitpoints - 50)
        }
        with(f.scripts) {
            onNpcHit(f.npc.type) {
                f.impacts += hit.damage
                if (npc.hitpoints == 50) protected = true
            }
        }
        f.queue(60)
        f.queue(60)
        assertFalse(protected)
        assertEquals(100, f.npc.hitpoints)
        f.tick()
        assertTrue(protected)
        assertEquals(listOf(false, true), states)
        assertEquals(listOf(50, 0), f.impacts)
        assertEquals(50, f.npc.damageContributions.totalDamage())
        assertEquals(0, f.deaths)
    }

    @Test
    fun `two simultaneous lethal hits dispatch one native death event`() {
        val f = Fixture()
        with(f.scripts) { onNpcHit(f.npc.type) { f.impacts += hit.damage } }
        f.queue(100)
        f.queue(100)
        f.tick()
        f.tick()
        assertEquals(listOf(100, 0), f.impacts)
        assertEquals(1, f.deaths)
        assertEquals(100, f.npc.damageContributions.totalDamage())
        assertTrue(f.npc.queueList.isEmpty)
    }

    @Test
    fun `invalid generation removes queued hits without invoking damage rules`() {
        val f = Fixture()
        var resolves = 0
        f.resolve { resolves++ }
        val scope = HitScope()
        f.queue(60, HitContext.of(HitAttribution(HitOrigin.Burn, 900L), scope))
        scope.close()
        f.tick()
        assertEquals(0, resolves)
        assertEquals(100, f.npc.hitpoints)
        assertTrue(f.npc.damageContributions.isEmpty && f.npc.queueList.isEmpty)
        assertEquals(0, f.deaths)
    }

    private class Fixture {
        private val events = EventBus()
        val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        val npc =
            Npc(assertNotNull(ServerCacheManager.getNpc(11278)), CoordGrid(2924, 5202)).apply {
                hitpoints = 100
                baseHitpointsLvl = 100
                currentMapClock = 100
            }
        val impacts = mutableListOf<Int>()
        var deaths = 0
        private val modifier = StandardNpcHitModifier(events)
        private val processor = StandardNpcHitProcessor(PlayerList(), events, emptySet())
        private val queues =
            NpcQueueProcessor(
                events,
                StandardNpcAccessLauncher(
                    StandardNpcAccessContextFactory(DefaultGameRandom(42L), modifier, processor)
                ),
            )

        init {
            with(NpcHitScript()) { scripts.startup() }
            with(scripts) { onNpcQueue(npc.type, "queue.death") { deaths++ } }
        }

        fun resolve(action: NpcHitEvents.Resolve.() -> Unit) =
            with(scripts) { onResolveNpcHit(npc.type, action) }

        fun queue(
            damage: Int,
            context: HitContext = HitContext.of(HitAttribution(HitOrigin.Direct, 900L)),
        ) =
            npc.queueHit(
                delay = 1,
                type = HitType.Magic,
                damage = damage,
                modifier = modifier,
                context = context,
            )

        fun tick() {
            npc.currentMapClock++
            queues.process(npc)
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
