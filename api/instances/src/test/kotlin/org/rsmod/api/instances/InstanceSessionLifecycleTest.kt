package org.rsmod.api.instances

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Native manager and session/index/event behavior; only physical region allocation is recorded. */
@OptIn(InternalApi::class)
class InstanceSessionLifecycleTest {
    @Test
    fun `native exit teleport death logout and disconnect release session state and NPC index`() {
        val endings =
            listOf<(Fixture, Player, InstanceSession) -> Unit>(
                { f, p, s -> f.manager.leave(p, s, 1) },
                { f, p, _ -> f.manager.leaveForExternalTeleport(p, CoordGrid(3200, 3200), 1) },
                { f, p, _ -> f.manager.handleDeath(p, 1) },
                { f, p, _ ->
                    f.manager.handleLogout(p, 1)
                    f.manager.tickReclaim(2)
                },
                { f, p, _ ->
                    assertTrue(f.manager.handleDisconnect(p, 1))
                    f.manager.tickReclaim(2)
                },
            )
        for (end in endings) {
            val f = Fixture()
            val (player, session) = f.enter(1)
            val key = InstanceStateKey<Any>("mechanic")
            session.state(key) { Any() }
            val npc = f.attach(session)
            var cleaned = 0
            session.onCleanup { cleaned++ }
            end(f, player, session)
            assertNull(f.manager.sessionForPlayer(player))
            assertNull(f.manager.sessionForId(session.id))
            assertNull(f.manager.instanceForNpc(npc))
            assertTrue(f.manager.npcsForInstance(session.id).isEmpty())
            assertNull(session.stateOrNull(key))
            assertTrue(session.isEnded)
            assertTrue(session.occupants.isEmpty())
            verify(f.npcs).del(npc, Int.MAX_VALUE)
            assertFalse(f.manager.terminate(session))
            assertEquals(1, cleaned)
            assertIs<InstanceManager.Result.Failed>(f.manager.join(player, session, 3))
        }
    }

    @Test
    fun `two instances and reentry have distinct session identity and content state`() {
        val f = Fixture()
        val (firstPlayer, first) = f.enter(1)
        val (secondPlayer, second) = f.enter(2)
        val key = InstanceStateKey<Any>("mechanic")
        val secondState = second.state(key) { Any() }
        f.manager.leave(firstPlayer, first, 1)
        assertSame(second, f.manager.sessionForPlayer(secondPlayer))
        assertSame(secondState, second.stateOrNull(key))
        val third = f.enterExisting(firstPlayer)
        assertNotEquals(first.id, third.id)
        assertNull(third.stateOrNull(key))
        f.manager.terminate(third)
        f.manager.terminate(second)
        assertNull(f.manager.firstSession())
    }

    @Test
    fun `failing content cleanup still runs other callbacks and releases native ownership`() {
        val f = Fixture()
        val (player, session) = f.enter(1)
        val npc = f.attach(session)
        var second = 0
        session.onCleanup { error("fixture resource failure") }
        session.onCleanup { second++ }
        assertFailsWith<IllegalStateException> { f.manager.terminate(session) }
        assertEquals(1, second)
        assertTrue(session.isEnded)
        assertNull(f.manager.sessionForPlayer(player))
        assertNull(f.manager.sessionForId(session.id))
        assertNull(f.manager.instanceForNpc(npc))
        verify(f.npcs).del(npc, Int.MAX_VALUE)
        assertFalse(f.manager.terminate(session))
        assertEquals(1, second)
    }

    @Test
    fun `reset releases old resources unregister skips finished resources and end cannot revive them`() {
        val f = Fixture()
        val (_, session) = f.enter(1)
        var cleaned = 0
        val unregister = session.onCleanup { error("finished resource") }
        unregister()
        session.onCleanup { cleaned++ }
        session.resetState()
        assertEquals(1, cleaned)
        session.onCleanup { cleaned++ }
        f.manager.terminate(session)
        assertEquals(2, cleaned)
        assertFailsWith<IllegalStateException> { session.onCleanup {} }
    }

    @Test
    fun `paired actor reset retains session and members without leaking old actors or helpers`() {
        val f = Fixture(spawnActors = true)
        val (player, session) = f.enter(1)
        val key = InstanceStateKey<Any>("paired-mechanic")
        val state = session.state(key) { Any() }
        var cleaned = 0
        session.onCleanup { cleaned++ }
        repeat(3) { cycle ->
            val old = f.manager.npcsForInstance(session.id) + f.attach(session)
            assertTrue(f.manager.resetNpcs(session, cycle + 1))
            assertEquals(2, f.manager.npcsForInstance(session.id).size)
            old.forEach {
                assertNull(f.manager.instanceForNpc(it))
                verify(f.npcs).del(it, Int.MAX_VALUE)
            }
            f.manager.npcsForInstance(session.id).forEach {
                assertEquals(session.id, f.manager.instanceForNpc(it))
                assertTrue(old.none { oldNpc -> oldNpc === it })
            }
            assertSame(session, f.manager.sessionForPlayer(player))
            assertEquals(setOf(1L), session.occupants)
            assertSame(state, session.stateOrNull(key))
            assertEquals(0, cleaned)
        }
        f.manager.terminate(session)
        assertEquals(1, cleaned)
    }

    @Test
    fun `actor reset rejects stale identity expired room and destroyed session without touching other rooms`() {
        val f = Fixture(spawnActors = true)
        val (_, first) = f.enter(1)
        val (other, second) = f.enter(2)
        val secondActors = f.manager.npcsForInstance(second.id)
        val forged =
            InstanceSession(
                first.id,
                first.regionIds,
                first.owner,
                first.key,
                first.spec,
                first.placement,
                first.access,
            )
        assertFalse(f.manager.resetNpcs(forged, 1))
        assertTrue(f.manager.resetNpcs(first, 1))
        assertEquals(secondActors, f.manager.npcsForInstance(second.id))
        assertSame(second, f.manager.sessionForPlayer(other))
        first.enterGrace(2)
        assertFalse(f.manager.resetNpcs(first, 2))
        f.manager.terminate(first)
        assertFalse(f.manager.resetNpcs(first, 3))
        second.markArenaExpired()
        assertFalse(f.manager.resetNpcs(second, 3))
    }

    @Test
    fun `actor reset does not spawn into a reclaimed region`() {
        val f = Fixture(spawnActors = true)
        val (_, session) = f.enter(1)
        val actors = f.manager.npcsForInstance(session.id)
        `when`(f.regions.isValid(any() ?: Region(CoordGrid.ZERO, CoordGrid.ZERO, 0, 0)))
            .thenReturn(false)
        assertFalse(f.manager.resetNpcs(session, 1))
        assertEquals(actors, f.manager.npcsForInstance(session.id))
    }

    private class Fixture(spawnActors: Boolean = false) {
        val regions = mock(RegionRepository::class.java)
        val npcs = mock(NpcRepository::class.java)
        val players = PlayerList()
        val events = EventBus()
        val manager =
            InstanceManager(
                regions,
                npcs,
                players,
                events,
                InstanceAreaResolver(),
                MapClock(),
                CollisionFlagMap(),
            )
        val spec =
            InstanceSettings(destroyWhenEmpty = true)
                .withArea(
                    InstanceArea.copyRegions(
                        listOf(9116),
                        enterCoord = RegionLocal(0, 35, 156, 40, 38),
                        exitCoord = CoordGrid(2280, 10016),
                        npcSpawns =
                            if (spawnActors)
                                listOf(7852, 7851).mapIndexed { index, id ->
                                    InstanceNpc(
                                        RSCM.getReverseMapping(RSCMType.NPC, id),
                                        RegionLocal(0, 35, 156, 30 + index * 8, 30),
                                    )
                                }
                            else emptyList(),
                    ),
                    0,
                )
        var allocations = 0

        init {
            var nextNpc = 10
            doAnswer {
                    val npc = it.getArgument<Npc>(0)
                    npc.slotId = nextNpc++
                    npc.assignUid()
                    Unit
                }
                .`when`(npcs)
                .add(
                    any() ?: Npc(assertNotNull(ServerCacheManager.getNpc(7852)), CoordGrid.ZERO),
                    eq(Int.MAX_VALUE),
                )
            doAnswer {
                    val base = CoordGrid(6400 + allocations * 64, 6400)
                    val region = Region(base, base.translate(64, 64), ++allocations, allocations)
                    for (x in 0..7) for (z in 0..7) {
                        region.registerZone(
                            RegionZoneCopy(ZoneKey(35 * 8 + x, 156 * 8 + z, 0), 0, null),
                            ZoneKey.from(base.translate(x * 8, z * 8)),
                        )
                    }
                    region
                }
                .`when`(regions)
                .add(
                    any()
                        ?: (InstanceAreaResolver().resolve(spec.area)
                                as InstanceAreaResolver.Result.Ready)
                            .placement
                            .regionTemplate
                )
            `when`(regions.isValid(any() ?: Region(CoordGrid.ZERO, CoordGrid.ZERO, 0, 0)))
                .thenReturn(true)
        }

        fun enter(id: Int): Pair<Player, InstanceSession> {
            val player =
                Player().apply {
                    slotId = id
                    uuid = id.toLong()
                    assignUid()
                }
            players[id] = player
            return player to enterExisting(player)
        }

        fun enterExisting(player: Player): InstanceSession {
            val result =
                assertIs<InstanceManager.Result.Created>(
                    manager.create(player, "lifecycle-test", spec, InstanceAccess.Private, 0)
                )
            player.coords = result.enter
            manager.finalizeEntry(player, result.session, 0)
            return result.session
        }

        fun attach(session: InstanceSession): Npc =
            Npc(assertNotNull(ServerCacheManager.getNpc(12596)), CoordGrid(6400, 6400)).apply {
                slotId = 1
                assignUid()
                manager.attachNpc(session.id, this)
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
