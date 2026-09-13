package org.rsmod.api.death

import dev.openrune.ParamMap
import dev.openrune.ServerCacheManager
import dev.openrune.types.ItemServerType
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid

/**
 * Exercises public spawnDrops, native hero selection and decoded cache params. Revision-240 Nex
 * guards explicitly store dropped_remains=-1 (native-cache-inspection.log:35-38); upstream
 * 61139e76's NpcDeath instead falls back to bones for every typed null. Only that sentinel changes.
 * Ground registration is a recording boundary; death animation and table delivery are tested by
 * their native integration suites.
 */
@OptIn(InternalApi::class)
class NpcDeathRemainsTest {
    @Test
    fun `missing remains retain bones fallback and the native winning hero`() {
        val f = Fixture(remains = null)
        assertFalse(f.npc.type.hasParam(params.dropped_remains.raw))
        f.spawn()

        val spawned = f.spawned.single()
        assertEquals(526, spawned.obj.type)
        assertEquals(1, spawned.obj.count)
        assertSame(f.player, spawned.owner)
        assertEquals(f.dropCoords, spawned.obj.coords)
        assertEquals(constants.lootdrop_duration, spawned.duration)
        assertEquals(listOf("drop:0", "spawn", "kill:0"), f.order)
        val drop = f.dropContexts.single()
        assertSame(f.player, drop.hero)
        assertEquals(526, drop.dropType.id)
        assertEquals(f.dropCoords, drop.dropCoords)
        val kill = f.killContexts.single()
        assertSame(f.player, kill.hero)
        assertSame(f.npc, kill.npc)
        assertEquals(f.dropCoords, kill.dropCoords)
        assertTrue(kill.lootTrackerEventId > 0)
    }

    @Test
    fun `explicit no remains skips remains hooks but continues native kill events`() {
        val f = Fixture(remains = -1)
        assertTrue(f.npc.type.hasParam(params.dropped_remains.raw))
        assertNull(f.npc.paramOrNull(params.dropped_remains))
        f.spawn()

        assertTrue(f.spawned.isEmpty())
        assertTrue(f.dropContexts.isEmpty())
        assertEquals(listOf("kill:0"), f.order)
        val first = f.killContexts.single()
        assertSame(f.player, first.hero)
        assertSame(f.npc, first.npc)
        assertEquals(f.dropCoords, first.dropCoords)

        // Separate public deliveries still share the native event allocator; this is not a claim
        // that spawnDrops itself deduplicates repeated calls for one death.
        val next = f.newNpc(remains = null)
        f.spawn(next)
        val second = f.killContexts.last()
        assertEquals(2, f.killContexts.size)
        assertSame(next, second.npc)
        assertTrue(first.lootTrackerEventId > 0)
        assertTrue(second.lootTrackerEventId > 0)
        assertNotEquals(first.lootTrackerEventId, second.lootTrackerEventId)
        assertEquals(526, f.spawned.single().obj.type)
    }

    @Test
    fun `valid configured remains preserve their type and personal duration`() {
        val f = Fixture(remains = 532)
        f.player.lootDropDuration = 321
        f.spawn()

        val spawned = f.spawned.single()
        assertEquals(532, spawned.obj.type)
        assertSame(f.player, spawned.owner)
        assertEquals(321, spawned.duration)
        assertEquals(532, f.dropContexts.single().dropType.id)
        assertEquals(321, f.dropContexts.single().duration)
        assertSame(f.npc, f.killContexts.single().npc)
    }

    @Test
    fun `invalid positive item references retain the existing bones fallback`() {
        assertNull(ServerCacheManager.getItem(Int.MAX_VALUE))
        val f = Fixture(remains = Int.MAX_VALUE)
        assertTrue(f.npc.type.hasParam(params.dropped_remains.raw))
        assertNull(f.npc.paramOrNull(params.dropped_remains))
        f.spawn()

        assertEquals(526, f.spawned.single().obj.type)
        assertEquals(526, f.dropContexts.single().dropType.id)
        assertEquals(1, f.killContexts.size)
    }

    @Test
    fun `drop suppression retains kill hooks while reward suppression skips all delivery`() {
        for (remains in listOf(null, -1, 532)) {
            val drops = Fixture(remains)
            drops.npc.suppressDrops = true
            drops.spawn()
            assertTrue(drops.spawned.isEmpty())
            assertTrue(drops.dropContexts.isEmpty())
            assertSame(drops.npc, drops.killContexts.single().npc)
            assertEquals(listOf("kill:0"), drops.order)

            val rewards = Fixture(remains)
            rewards.npc.suppressRewards = true
            rewards.spawn()
            assertTrue(rewards.spawned.isEmpty())
            assertTrue(rewards.dropContexts.isEmpty())
            assertTrue(rewards.killContexts.isEmpty())
            assertTrue(rewards.order.isEmpty())
        }
    }

    @Test
    fun `missing hero or unavailable winning hero produces no remains or kill hooks`() {
        for (remains in listOf(null, -1)) {
            val noHero = Fixture(remains, withHero = false)
            noHero.spawn()
            assertTrue(noHero.spawned.isEmpty())
            assertTrue(noHero.dropContexts.isEmpty())
            assertTrue(noHero.killContexts.isEmpty())

            val unavailable = Fixture(remains)
            unavailable.players.remove(unavailable.player.slotId)
            assertSame(unavailable.runnerUp, unavailable.players.single())
            unavailable.spawn()
            assertTrue(unavailable.spawned.isEmpty())
            assertTrue(unavailable.dropContexts.isEmpty())
            assertTrue(unavailable.killContexts.isEmpty())
        }
    }

    @Test
    fun `consumed remains stop later drop hooks but preserve every kill hook`() {
        val f = Fixture(remains = 532, dropResults = listOf(false, true, false), killHookCount = 2)
        f.spawn()

        assertTrue(f.spawned.isEmpty())
        assertEquals(listOf("drop:0", "drop:1", "kill:0", "kill:1"), f.order)
        assertEquals(2, f.dropContexts.size)
        assertSame(f.dropContexts[0], f.dropContexts[1])
        assertEquals(532, f.dropContexts[0].dropType.id)
        assertEquals(2, f.killContexts.size)
        assertSame(f.killContexts[0], f.killContexts[1])
        assertSame(f.npc, f.killContexts[0].npc)
        assertSame(f.player, f.killContexts[0].hero)
        assertEquals(f.dropCoords, f.killContexts[0].dropCoords)
    }

    @AfterEach
    fun releaseMocks() {
        framework().clearInlineMocks()
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }

    private data class Spawn(val obj: Obj, val duration: Int, val owner: Player)

    private class Fixture(
        remains: Int?,
        withHero: Boolean = true,
        dropResults: List<Boolean> = listOf(false),
        killHookCount: Int = 1,
    ) {
        val player = player(slot = 1, id = 11L)
        val runnerUp = player(slot = 2, id = 22L)
        val players =
            PlayerList().apply {
                this[player.slotId] = player
                this[runnerUp.slotId] = runnerUp
            }
        val npc = newNpc(remains, withHero)
        val dropCoords = CoordGrid(2911, 9567)
        val spawned = mutableListOf<Spawn>()
        val dropContexts = mutableListOf<NpcDeathDropContext>()
        val killContexts = mutableListOf<NpcDeathKillContext>()
        val order = mutableListOf<String>()
        private val events = EventBus()
        private val objects =
            mock(ObjRepository::class.java) { invocation ->
                if (
                    invocation.method.name.startsWith("add-") &&
                        invocation.arguments.firstOrNull() is ItemServerType
                ) {
                    val type = invocation.getArgument<ItemServerType>(0)
                    val coords = CoordGrid(invocation.getArgument<Int>(1))
                    val owner = invocation.getArgument<Player>(3)
                    val obj = Obj.fromOwner(owner, coords, type, invocation.getArgument(4))
                    spawned += Spawn(obj, invocation.getArgument(2), owner)
                    order += "spawn"
                    obj
                } else RETURNS_DEFAULTS.answer(invocation)
            }
        private val death =
            NpcDeath(
                mock(NpcRepository::class.java),
                players,
                objects,
                dropResults
                    .mapIndexed { index, consumed ->
                        NpcDeathDropHook { context ->
                            dropContexts += context
                            order += "drop:$index"
                            consumed
                        }
                    }
                    .toCollection(linkedSetOf()),
                List(killHookCount) { index ->
                        NpcDeathKillHook { context ->
                            killContexts += context
                            order += "kill:$index"
                        }
                    }
                    .toCollection(linkedSetOf()),
            )

        fun newNpc(remains: Int?, withHero: Boolean = true): Npc {
            val base = assertNotNull(ServerCacheManager.getNpc(11283))
            val raw = assertNotNull(base.paramMap).primitiveMap.toMutableMap()
            raw.remove(params.dropped_remains.id)
            if (remains != null) raw[params.dropped_remains.id] = remains
            val type = base.copy(paramsRaw = raw).apply { paramMap = ParamMap(raw) }
            return Npc(type, CoordGrid(2910, 9571)).apply {
                if (withHero) {
                    heroPoints(runnerUp, 3)
                    heroPoints(player, 10)
                }
            }
        }

        fun spawn(source: Npc = npc) {
            source.launch {
                death.spawnDrops(
                    StandardNpcAccess(
                        source,
                        this,
                        StandardNpcAccessContext(
                            { DefaultGameRandom(1L) },
                            { StandardNpcHitModifier(events) },
                            { StandardNpcHitProcessor(players, events, emptySet()) },
                        ),
                    ),
                    dropCoords,
                )
            }
            assertNull(source.activeCoroutine)
        }

        private fun player(slot: Int, id: Long): Player =
            Player().apply {
                slotId = slot
                uuid = id
                observerUUID = id
                accountHash = id
                assignUid()
            }
    }
}
