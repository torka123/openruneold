package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.CollisionFlag
import org.rsmod.routefinder.loc.LocLayerConstants

/**
 * M14/M27 registered Shadow Smash and native marker ownership regression. Retained capture808
 * L5128-30 and L5611-12 records the marker/removal packets used by the mapping in
 * docs/bosses/nex/NEX_OSRS_AUDIT.md. The three-cycle marker/callback, following hit cycle, 0..50
 * damage and current target selection remain characterization, not OSRS timing certification. Real
 * loc storage, expiry and collision run; repeated special calls are ownership fault setup.
 */
@OptIn(InternalApi::class)
class NexShadowMarkerIntegrationTest {
    @Test
    fun `registered duo smash uses nonblocking native markers and preserves callback and impact`() {
        val f = world()
        val partner = assertNotNull(f.partner)
        val boss = f.npc(11278)
        assertTrue(f.encounters.of(boss).forceNextAbility("SHADOW.special"))
        f.attack(boss)
        assertEquals("SHADOW.special", f.encounters.of(boss).lastAbilityName)
        val markers = markers(f)
        assertEquals(setOf(FIRST_TILE, SECOND_TILE), markers.map { it.coords }.toSet())
        assertTrue(markers.all { it.id == 42942 })
        assertNonblocking(f, markers)
        f.clock.tick()
        repeat(2) {
            f.tick()
            assertTrue(f.hits().isEmpty())
            assertTrue(f.hits(partner).isEmpty())
            markers.forEach { assertNotNull(find(f, it)) }
        }
        f.tick()
        assertGone(f, markers)
        for (target in listOf(f.player, partner)) {
            val (delay, hit) = f.hits(target).single()
            assertEquals(1, delay)
            assertEquals(HitType.Typeless, hit.type)
            assertEquals(50, hit.damage)
            assertEquals(99, target.hitpoints)
            assertSame(boss, hit.resolveNpcSource(f.npcs))
        }
        f.processPlayerHits(1)
        assertEquals(49, f.player.hitpoints)
        assertEquals(49, partner.hitpoints)
        f.tick(5)
        assertTrue(f.hits().isEmpty())
        assertTrue(f.hits(partner).isEmpty())
    }

    @Test
    fun `failed placement cannot acquire or delete an identical preexisting native marker`() {
        val f = world()
        val preexisting = marker(FIRST_TILE)
        assertTrue(f.locs.add(preexisting, 30))
        f.rejectLocAddAt = FIRST_TILE
        f.registeredSpecial()
        f.clock.tick()
        f.rejectLocAddAt = null
        assertNotNull(find(f, preexisting))
        val ownedPartnerMarker = marker(SECOND_TILE)
        assertNotNull(find(f, ownedPartnerMarker))
        val boss = f.npc(11278)
        val encounter = f.encounters.of(boss)
        f.repository.del(boss, Int.MAX_VALUE)
        assertTrue(encounter.isEnded)
        assertNotNull(find(f, preexisting))
        f.tick(6)
        assertNotNull(find(f, preexisting), "Failed add must not claim a preexisting identical loc")
        assertNull(find(f, ownedPartnerMarker))
        assertNonblocking(f, listOf(preexisting, ownedPartnerMarker))
        assertTrue(f.hits().isEmpty())
        assertTrue(f.hits(assertNotNull(f.partner)).isEmpty())
    }

    @Test
    fun `natural expiry releases ownership before an identical external replacement and retirement`() {
        val f = world()
        f.registeredSpecial()
        val markers = markers(f)
        f.clock.tick()
        f.tick(3)
        assertGone(f, markers)
        for (marker in markers) assertTrue(f.locs.add(marker, 30))
        val boss = f.npc(11278)
        val encounter = f.encounters.of(boss)
        f.repository.del(boss, Int.MAX_VALUE)
        assertTrue(encounter.isEnded)
        f.tick(6)
        for (marker in markers) {
            assertNotNull(find(f, marker), "Expired ownership deleted an external replacement")
        }
        assertNonblocking(f, markers)
        f.processPlayerHits(1)
        assertEquals(99, f.player.hitpoints)
        assertEquals(99, assertNotNull(f.partner).hitpoints)
    }

    @Test
    fun `native source deletion clears live markers immediately and cannot delete later replacements`() {
        val f = world()
        f.registeredSpecial()
        val markers = markers(f)
        f.clock.tick()
        val boss = f.npc(11278)
        val encounter = f.encounters.of(boss)
        f.repository.del(boss, Int.MAX_VALUE)
        assertTrue(encounter.isEnded)
        assertGone(f, markers)
        assertTrue(f.worldQueues.isEmpty)
        for (marker in markers) assertTrue(f.locs.add(marker, 30))
        f.tick(8)
        for (marker in markers) {
            assertNotNull(
                find(f, marker),
                "Old cleanup or timers deleted a later native replacement",
            )
        }
        assertNonblocking(f, markers)
        assertEquals(5, f.npcs.count())
        assertTrue(f.hits().isEmpty())
        assertTrue(f.hits(assertNotNull(f.partner)).isEmpty())
    }

    @Test
    fun `same tile repeated casts retain the latest native marker expiry`() {
        val f = world()
        f.registeredSpecial()
        val markers = markers(f)
        f.clock.tick()
        f.tick()
        f.registeredSpecial() // Preserve the existing intervening darkness selection.
        val secondIssued = f.clock.cycle
        f.registeredSpecial()
        f.clock.tick()
        f.tick(2) // Old expiry has passed, but the replacement's native post-tick has not.
        assertEquals(secondIssued + 3, f.clock.cycle)
        for (marker in markers) {
            assertNotNull(find(f, marker), "Old timer deleted the latest same-value marker")
            assertEquals(1, f.locs.findAll(marker.coords).count { it.id == 42942 })
        }
        assertNonblocking(f, markers)
        f.tick()
        assertGone(f, markers)
    }

    private fun world(): NexMinionTestWorld =
        NexMinionTestWorld(duo = true, nativeLocs = true).apply {
            reachPhase(NexState.SHADOW)
            player.coords = FIRST_TILE
            assertNotNull(partner).coords = SECOND_TILE
            player.queueList.clear()
            partner?.queueList?.clear()
        }

    private fun markers(f: NexMinionTestWorld): List<LocInfo> =
        listOf(FIRST_TILE, SECOND_TILE).map { tile ->
            assertNotNull(find(f, marker(tile)), "Registered smash did not add the native marker")
        }

    private fun marker(tile: CoordGrid): LocInfo {
        val shape = LocShape.CentrepieceStraight
        return LocInfo(
            LocLayerConstants.of(shape.id),
            tile,
            LocEntity(42942, shape.id, LocAngle.North.id),
        )
    }

    private fun find(f: NexMinionTestWorld, loc: LocInfo): LocInfo? =
        f.locs.findExact(loc.coords, assertNotNull(ServerCacheManager.getObject(loc.id)))

    private fun assertNonblocking(f: NexMinionTestWorld, markers: List<LocInfo>) {
        for (loc in markers) {
            val coords = loc.coords
            assertEquals(0, f.collision[coords.x, coords.z, coords.level] and CollisionFlag.LOC)
        }
    }

    private fun assertGone(f: NexMinionTestWorld, markers: List<LocInfo>) {
        markers.forEach { assertNull(find(f, it), "Native marker remains at ${it.coords}") }
        assertNonblocking(f, markers)
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        private val FIRST_TILE = CoordGrid(2915, 5200)
        private val SECOND_TILE = CoordGrid(2933, 5200)

        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
