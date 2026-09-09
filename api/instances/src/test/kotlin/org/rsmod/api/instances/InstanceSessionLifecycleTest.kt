package org.rsmod.api.instances

import dev.openrune.ServerCacheManager
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

    private class Fixture {
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
                    ),
                    0,
                )
        var allocations = 0

        init {
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
