package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * M06 initial facing: retained capture3284 add/face rows8686-87,8716-17,8745-46,8774-75 and
 * capture808 rows3586-87,3615-16,3645-46,3676-77 agree on these four directions and angles. The
 * explicit captured angles are asserted independently of Direction.angle. These Grid Master
 * captures do not establish normal spawn intervals or staged casting effects.
 *
 * The existing fixture drives real NPC post-tick; a delegating NpcInfoProtocol records its facing
 * output. Replacement Create callbacks observe prepared ownership, stats and presentation before
 * the production NetworkScript would allocate an avatar. Encoded add-header bytes are not tested.
 * Eager creation, 500 HP and five-cycle readiness remain characterization; reachPhase only sets up
 * the final phase, while final death and the completed-round handoff execute natively.
 */
@OptIn(InternalApi::class)
class NexBodyguardFacingIntegrationTest {
    @Test
    fun `initial eager guards face inward once through native post-tick before readiness`() {
        val f = NexMinionTestWorld(enterRoom = false)
        val session = f.session()
        val boss = f.npc(11278)
        val guards =
            captures.map { capture ->
                val npc = f.npc(capture.id)
                assertPrepared(f, session, npc, capture)
                observe(f, npc, capture)
            }
        assertEquals("IDLE", f.encounters.of(boss).currentPhaseName)
        assertRoster(f, session, guards)
        assertTrue(guards.all { it.facings.isEmpty() })

        assertTrue(f.enter())
        val openingCycle = f.clock.cycle
        assertEquals("SPAWNING", f.encounters.of(boss).currentPhaseName)
        assertRoster(f, session, guards)
        f.tick()
        assertEmittedOnce(guards, openingCycle)
        f.tick(3)
        assertEquals(openingCycle + 4, f.clock.cycle)
        assertEquals("SPAWNING", f.encounters.of(boss).currentPhaseName)
        assertRoster(f, session, guards)
        assertEmittedOnce(guards, openingCycle)
        f.tick()
        assertEquals("SMOKE", f.encounters.of(boss).currentPhaseName)
        assertFalse(boss.movementLocked)
        assertRoster(f, session, guards)
        f.tick(3)
        assertEmittedOnce(guards, openingCycle)
        assertRoster(f, session, guards)
    }

    @Test
    fun `completed native round prepares captured guard facing before Create and emits it once`() {
        val f = NexMinionTestWorld(nativeDeaths = true)
        val session = f.session()
        val oldBoss = f.npc(11278)
        val oldEncounter = f.encounters.of(oldBoss)
        val oldActors = f.npcs.toList()
        val playerCoords = f.player.coords
        val created = mutableListOf<Npc>()
        val guards = mutableListOf<ObservedGuard>()
        f.events.subscribeUnbound(NpcStateEvents.Create::class.java) {
            if (npc.type.id == 11278 || npc.type.id in 11283..11286) {
                created += npc
                assertEquals(session.id, f.instances.instanceForNpc(npc))
            }
            captures
                .firstOrNull { it.id == npc.type.id }
                ?.let { capture ->
                    assertPrepared(f, session, npc, capture)
                    guards += observe(f, npc, capture)
                }
        }

        f.reachPhase(NexState.ZAROS)
        oldBoss.hitpoints = 1
        f.damage(oldBoss, 1)
        var ticks = 0
        while (f.npc(11278) === oldBoss && ticks++ < 100) f.tick()

        val boss = f.npc(11278)
        val openingCycle = f.clock.cycle
        assertNotSame(oldBoss, boss, "Native final death must complete the round")
        assertTrue(oldEncounter.isEnded)
        assertTrue(oldActors.none { it.isSlotAssigned })
        assertSame(session, f.session())
        assertSame(session, f.instances.sessionForPlayer(f.player))
        assertEquals(playerCoords, f.player.coords)
        assertEquals(setOf(11278, 11283, 11284, 11285, 11286), created.map { it.type.id }.toSet())
        assertEquals(5, created.size, "The existing complete roster is created eagerly")
        assertEquals(4, guards.size)
        assertTrue(guards.all { it.facings.isEmpty() })
        assertEquals("SPAWNING", f.encounters.of(boss).currentPhaseName)
        assertRoster(f, session, guards)
        f.tick()
        assertEmittedOnce(guards, openingCycle)
        f.tick(3)
        assertEquals("SPAWNING", f.encounters.of(boss).currentPhaseName)
        assertRoster(f, session, guards)
        f.tick()
        assertEquals(openingCycle + 5, f.clock.cycle)
        assertEquals("SMOKE", f.encounters.of(boss).currentPhaseName)
        assertFalse(boss.movementLocked)
        f.tick(3)
        assertEmittedOnce(guards, openingCycle)
        assertRoster(f, session, guards)
        assertEquals(5, created.size, "Readiness does not create or replace another actor")
    }

    private fun assertPrepared(
        f: NexMinionTestWorld,
        session: InstanceSession,
        npc: Npc,
        capture: CapturedGuard,
    ) {
        assertSame(npc, f.npcs[npc.slotId])
        assertEquals(session.id, f.instances.instanceForNpc(npc))
        assertEquals(capture.coords, npc.coords)
        assertEquals(capture.coords, npc.spawnCoords)
        assertEquals(1, npc.size)
        assertEquals(500, npc.baseHitpointsLvl)
        assertEquals(500, npc.hitpoints)
        assertEquals(capture.direction, npc.respawnDir)
        assertEquals(capture.angle, npc.pendingFaceAngle.intValue)
    }

    private fun assertRoster(
        f: NexMinionTestWorld,
        session: InstanceSession,
        guards: List<ObservedGuard>,
    ) {
        assertTrue(session.regionIds.isEmpty())
        assertEquals(5, f.npcs.count())
        assertEquals(f.npcs.toSet(), f.instances.npcsForInstance(session.id).toSet())
        for (guard in guards) {
            val npc = guard.npc
            assertSame(npc, f.npc(guard.capture.id))
            assertEquals(session.id, f.instances.instanceForNpc(npc))
            assertEquals(guard.capture.coords, npc.coords)
            assertEquals(guard.capture.direction, npc.respawnDir)
            assertEquals(500, npc.hitpoints)
            assertTrue(npc.movementLocked)
            assertFalse(npc.respawns)
            assertFalse(f.manager.canAttackMinion(npc, f.player))
        }
    }

    private fun assertEmittedOnce(guards: List<ObservedGuard>, cycle: Int) {
        for (guard in guards) {
            assertEquals(listOf(Facing(cycle, guard.capture.angle, false)), guard.facings)
            assertEquals(EntityFaceAngle.NULL, guard.npc.pendingFaceAngle)
        }
    }

    private fun observe(f: NexMinionTestWorld, npc: Npc, capture: CapturedGuard): ObservedGuard {
        val observed = ObservedGuard(npc, capture)
        npc.infoProtocol =
            object : NpcInfoProtocol by npc.infoProtocol {
                override fun setFaceAngle(angle: Int, instant: Boolean) {
                    observed.facings += Facing(f.clock.cycle, angle, instant)
                }
            }
        return observed
    }

    private class ObservedGuard(val npc: Npc, val capture: CapturedGuard) {
        val facings = mutableListOf<Facing>()
    }

    private data class Facing(val cycle: Int, val angle: Int, val instant: Boolean)

    private data class CapturedGuard(
        val id: Int,
        val coords: CoordGrid,
        val direction: Direction,
        val angle: Int,
    )

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        private val captures =
            listOf(
                CapturedGuard(11283, CoordGrid(2913, 5215), Direction.SouthEast, 1792),
                CapturedGuard(11284, CoordGrid(2937, 5215), Direction.SouthWest, 256),
                CapturedGuard(11285, CoordGrid(2937, 5191), Direction.NorthWest, 768),
                CapturedGuard(11286, CoordGrid(2913, 5191), Direction.NorthEast, 1280),
            )

        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
