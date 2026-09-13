package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import java.nio.file.Path
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.route.RayCastValidator
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.canStep
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * M06 coordinate-only mapping in docs/bosses/nex/NEX_OSRS_AUDIT.md: retained capture3284 add
 * lines8686/8716/8745/8774 and capture808 lines3586/3615/3645/3676 agree on these four anchors.
 * Canonical revision240 SERVER terrain and locs are decoded through the existing Royal Titans
 * native map-loading route, into isolated collision; no global map or cached tile is modified.
 *
 * The existing eager roster, 500 guard HP and opening/phase readiness are characterization.
 * reachGuard/reachPhase provide existing setup; final Nex death and round replacement run natively.
 * These tests establish static map compatibility and placement, not normal staged-spawn timing.
 */
@OptIn(InternalApi::class)
class NexBodyguardPlacementIntegrationTest {
    @Test
    fun `captured stationary anchors retain native blocking under the initial public roster`() {
        val collision = loadCanonicalCollision()
        val f = NexMinionTestWorld(enterRoom = false, nativeCollision = collision)
        assertSame(collision, f.collision)
        val session = f.session()
        assertRoster(f, session)
        assertEquals("IDLE", f.encounters.of(f.npc(11278)).currentPhaseName)

        assertTrue(f.enter())
        assertEquals("SPAWNING", f.encounters.of(f.npc(11278)).currentPhaseName)
        assertRoster(f, session)
        f.tick(5)
        assertEquals("SMOKE", f.encounters.of(f.npc(11278)).currentPhaseName)
        assertRoster(f, session)

        f.reachGuard(11283)
        assertTrue(f.manager.canAttackMinion(f.npc(11283), f.player))
        for (id in 11284..11286) assertFalse(f.manager.canAttackMinion(f.npc(id), f.player))
    }

    @Test
    fun `completed native Nex round replaces bodyguards at the same canonical anchors`() {
        val collision = loadCanonicalCollision()
        val f = NexMinionTestWorld(nativeDeaths = true, nativeCollision = collision)
        val session = f.session()
        val oldBoss = f.npc(11278)
        val oldEncounter = f.encounters.of(oldBoss)
        val oldActors = f.npcs.toList()
        val playerCoords = f.player.coords
        assertRoster(f, session)

        f.reachPhase(NexState.ZAROS)
        oldBoss.hitpoints = 1
        f.damage(oldBoss, 1)
        var ticks = 0
        while (f.npc(11278) === oldBoss && ticks++ < 100) f.tick()

        val fresh = f.npc(11278)
        assertNotSame(oldBoss, fresh, "Native final death must finish and replace the round")
        assertTrue(oldEncounter.isEnded)
        assertTrue(oldActors.none { it.isSlotAssigned }, "Every old actor must retire")
        assertSame(session, f.session())
        assertSame(session, f.instances.sessionForPlayer(f.player))
        assertEquals(playerCoords, f.player.coords)
        assertEquals("SPAWNING", f.encounters.of(fresh).currentPhaseName)
        assertRoster(f, session)

        f.tick(5)
        assertEquals("SMOKE", f.encounters.of(fresh).currentPhaseName)
        assertRoster(f, session)
        f.reachGuard(11283)
        assertTrue(f.manager.canAttackMinion(f.npc(11283), f.player))
        for (id in 11284..11286) assertFalse(f.manager.canAttackMinion(f.npc(id), f.player))
    }

    private fun assertRoster(f: NexMinionTestWorld, session: InstanceSession) {
        val owned = f.instances.npcsForInstance(session.id)
        assertEquals(setOf(11278, 11283, 11284, 11285, 11286), owned.map { it.type.id }.toSet())
        assertEquals(5, owned.size)
        assertEquals(owned.toSet(), f.npcs.toSet())
        assertTrue(session.regionIds.isEmpty())
        for (actor in owned) assertEquals(session.id, f.instances.instanceForNpc(actor))
        assertEquals(3400, f.npc(11278).hitpoints)
        for (anchor in anchors) {
            val guard = f.npc(anchor.id)
            assertEquals(anchor.coords, guard.coords)
            assertEquals(anchor.coords, guard.spawnCoords)
            assertEquals(1, guard.size)
            assertEquals(1, guard.type.size)
            assertEquals(500, guard.baseHitpointsLvl)
            assertEquals(500, guard.hitpoints)
            assertEquals(
                CollisionFlag.BLOCK_WALK,
                f.collision[guard.x, guard.z, guard.level] and CollisionFlag.BLOCK_WALK,
            )
            assertTrue(guard.movementLocked)
            assertFalse(guard.respawns)
            assertFalse(f.manager.canAttackMinion(guard, f.player))
        }
    }

    private fun loadCanonicalCollision(): CollisionFlagMap {
        if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        val collision = CollisionFlagMap()
        val cache = Cache.load(Path.of(".data/cache/SERVER"))
        try {
            val square = MapSquareKey(45, 81)
            val tiles =
                MapTileDecoder.decode(InlineByteBuf(assertNotNull(cache.data(MAPS, 11601, 0))))
            val locs =
                MapLocListDecoder.decode(InlineByteBuf(assertNotNull(cache.data(MAPS, 11601, 1))))
            GameMapDecoder.putMaps(collision, square, tiles)
            GameMapDecoder.putLocs(GameMapBuilder(), collision, square, tiles, locs)
        } finally {
            cache.close()
        }

        // Check native data before constructing the fixture, which otherwise allocates test zones.
        val blocked = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR
        for (anchor in anchors) {
            for (tile in listOf(anchor.coords, anchor.interior)) {
                assertTrue(collision.isZoneValid(tile), "Native map zone missing at $tile")
                assertEquals(
                    if (tile == anchor.coords) CollisionFlag.BLOCK_WALK else 0,
                    collision[tile.x, tile.z, tile.level] and blocked,
                    "Preserve the native stationary anchor and adjacent floor at $tile",
                )
            }
            assertEquals(
                anchor.coords,
                anchor.interior.translate(anchor.direction.xOff, anchor.direction.zOff),
            )
            assertFalse(
                collision.canStep(anchor.interior, anchor.direction, size = 1),
                "Players must not walk onto the native blocked guard anchor ${anchor.coords}",
            )
            assertTrue(
                RayCastValidator(collision)
                    .hasLineOfSight(
                        anchor.coords,
                        anchor.interior,
                        extraFlag = CollisionFlag.BLOCK_PLAYERS,
                    ),
                "Native stationary spell line into the chamber must remain open",
            )
        }
        return collision
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    private data class Anchor(
        val id: Int,
        val coords: CoordGrid,
        val interior: CoordGrid,
        val direction: Direction,
    )

    private companion object {
        private val anchors =
            listOf(
                Anchor(11283, CoordGrid(2913, 5215), CoordGrid(2914, 5215), Direction.West),
                Anchor(11284, CoordGrid(2937, 5215), CoordGrid(2936, 5215), Direction.East),
                Anchor(11285, CoordGrid(2937, 5191), CoordGrid(2936, 5191), Direction.East),
                Anchor(11286, CoordGrid(2913, 5191), CoordGrid(2914, 5191), Direction.West),
            )
    }
}
