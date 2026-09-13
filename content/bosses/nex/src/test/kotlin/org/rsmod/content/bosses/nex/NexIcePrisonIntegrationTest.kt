package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.CollisionFlag
import org.rsmod.routefinder.loc.LocLayerConstants

/**
 * Registered prison/click handlers, real loc storage/timers/collision and native hit processing.
 * Five callback ticks, the following hit tick, 0..75 damage and the mixed eight-tile perimeter are
 * existing E07 characterizations. Synthetic overlapping/recast calls do not certify attack cadence.
 * Retained evidence: C-GM808 t1941:L49885-93 shows selected-only deletion, with the remaining ring
 * expiring at t1945:L49928-34; C-GM806 t364:L10126-31 shows self-rescue rejection.
 */
@OptIn(InternalApi::class)
class NexIcePrisonIntegrationTest {
    @Test
    fun `ordinary DSL prison expires once through native loc timers and queued player impact`() {
        val f = world()
        val boss = f.npc(11278)
        assertTrue(f.encounters.of(boss).forceNextAbility("ICE.special"))
        f.attack(boss, f.player) // Existing first ice special is containment.
        f.tick(12)
        assertTrue(f.hits().isEmpty())
        assertTrue(f.encounters.of(boss).forceNextAbility("ICE.special"))
        f.attack(boss, f.player)
        assertEquals("ICE.special", f.encounters.of(boss).lastAbilityName)
        val ring = ring(f, f.player.coords)
        assertEquals(8, ring.size)
        assertEquals(setOf(42943, 42944), ring.map { it.id }.toSet())
        assertTrue(ring.all { blocked(f, it.coords) })
        f.clock.tick() // Finish the issuing pre-tick, as in NexSpecialLifecycleTest.
        repeat(4) {
            f.tick()
            assertTrue(f.hits().isEmpty(), "Prison callback fired early")
        }
        f.tick()
        assertEquals(1, f.hits().size)
        val hit = f.hits().single().second
        assertEquals(75, hit.damage)
        assertEquals(HitType.Typeless, hit.type)
        assertEquals(99, f.player.hitpoints)
        assertGone(f, ring)
        f.processPlayerHits(1)
        assertEquals(24, f.player.hitpoints)
        f.tick(8)
        assertTrue(f.hits().isEmpty())
    }

    @Test
    fun `registered rescue removes only selected segment and cancels the later prison hit`() {
        val f = world()
        val ring = cast(f, f.player)
        val selected = segment(ring)
        assertTrue(f.clickIce(selected, requireNotNull(f.partner)))
        assertNull(find(f, selected), "Selected native segment was not removed")
        assertFalse(blocked(f, selected.coords))
        assertEquals(
            7,
            ring.count { find(f, it) != null },
            "Rescue must preserve the other segments",
        )
        f.tick(5)
        assertTrue(f.hits().isEmpty(), "A rescued prison must not shatter from absent state")
        assertGone(f, ring)
        f.processPlayerHits(1)
        assertEquals(99, f.player.hitpoints)
    }

    @Test
    fun `clicked higher uid prison is rescued while the other target still expires`() {
        val f = world()
        val partner = requireNotNull(f.partner)
        partner.coords = SECOND_TARGET
        val low = cast(f, f.player)
        val high = cast(f, partner)
        val selected = segment(high)
        // Both targets are real admitted participants. The lower-UID player rescues their ally.
        assertTrue(f.clickIce(selected, f.player))
        assertNull(find(f, selected))
        assertTrue(low.all { find(f, it) != null }, "Clicked prison must not select the lowest UID")
        f.tick(5)
        assertEquals(1, f.hits(f.player).size)
        assertTrue(f.hits(partner).isEmpty())
        f.processPlayerHits(1)
        assertEquals(24, f.player.hitpoints)
        assertEquals(99, partner.hitpoints)
        assertGone(f, low + high)
    }

    @Test
    fun `self outsider and unowned same id clicks cannot consume a valid prison`() {
        for (kind in listOf("self", "outsider", "unowned")) {
            val f = world()
            val ring = cast(f, f.player)
            val selected = segment(ring)
            val helper = requireNotNull(f.partner)
            var unrelated: LocInfo? = null
            val actor =
                if (kind == "self") f.player
                else if (kind == "outsider")
                    Player().apply {
                        slotId = 3
                        uuid = 3
                        observerUUID = 3
                        assignUid()
                        coords = helper.coords
                        statMap.setBaseLevel("stat.hitpoints", 99)
                        statMap.setCurrentLevel("stat.hitpoints", 99)
                        currentMapClock = f.clock.cycle
                        processedMapClock = f.clock.cycle
                        f.players[slotId] = this
                    }
                else helper
            val clicked =
                if (kind == "unowned")
                    f.locs
                        .add(
                            UNRELATED_TILE,
                            requireNotNull(ServerCacheManager.getObject(42944)),
                            30,
                            LocAngle.North,
                            LocShape.CentrepieceStraight,
                        )
                        .also { unrelated = it }
                else selected
            assertTrue(f.clickIce(clicked, actor), kind)
            assertNotNull(find(f, clicked), "$kind click removed a location")
            assertNotNull(find(f, selected), "$kind click consumed the real prison")
            assertTrue(f.clickIce(selected, helper))
            assertNull(
                find(f, selected),
                "Invalid click must not prevent a subsequent valid rescue",
            )
            f.tick(5)
            assertTrue(f.hits().isEmpty(), kind)
            unrelated?.let { assertNotNull(find(f, it)) }
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `recast and overlapping segment expiry cannot consume newer cast ownership`() {
        for (sameTarget in listOf(true, false)) {
            val f = world()
            val partner = requireNotNull(f.partner)
            val first = cast(f, f.player)
            f.tick(2)
            val target =
                if (sameTarget) f.player else partner.apply { coords = FIRST_TARGET.translateX(1) }
            val second = cast(f, target)
            val selected =
                second.first {
                    it.id == 42944 && (sameTarget || first.any { old -> old.coords == it.coords })
                }
            f.tick(3) // The old callback has expired; the new cast has not.
            assertNotNull(find(f, selected), "Old cleanup removed a newer segment")
            val helper = if (sameTarget) partner else f.player
            assertTrue(f.clickIce(selected, helper))
            assertNull(find(f, selected), "Old callback consumed the new cast's rescue state")
            f.tick(4)
            assertTrue(f.hits(target).isEmpty(), "Rescued replacement prison emitted damage")
            assertGone(f, second)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `arrival suspended click cannot release a same value replacement prison`() {
        val f = world()
        val helper = requireNotNull(f.partner)
        val oldRing = cast(f, f.player)
        val oldSegment = segment(oldRing)
        helper.lastMovement = helper.processedMapClock - 1
        assertTrue(f.clickIce(oldSegment, helper))
        assertNotNull(find(f, oldSegment), "Arrival should suspend before mutation")
        val replacement = cast(f, f.player)
        val sameValue = replacement.single { it == oldSegment }
        f.tick()
        assertNotNull(find(f, sameValue), "Old delayed click released the replacement prison")
        helper.lastMovement = 0
        assertTrue(f.clickIce(sameValue, helper))
        assertNull(find(f, sameValue))
        f.tick(6)
        assertTrue(f.hits().isEmpty())
        assertGone(f, replacement)
    }

    @Test
    fun `failed native add owns no segment and failed native delete preserves rescue ownership`() {
        for (failAdd in listOf(true, false)) {
            val f = world()
            val helper = requireNotNull(f.partner)
            if (failAdd) {
                val shape = LocShape.CentrepieceStraight
                val missing =
                    LocInfo(
                        LocLayerConstants.of(shape.id),
                        FIRST_TARGET.translate(-1, 0),
                        LocEntity(42944, shape.id, LocAngle.North.id),
                    )
                f.rejectLocAddAt = missing.coords
                f.registeredSpecial()
                f.registeredSpecial()
                val owned = ring(f, FIRST_TARGET)
                assertEquals(7, owned.size)
                assertNull(find(f, missing))
                assertFalse(blocked(f, missing.coords))
                f.clock.tick()
                f.rejectLocAddAt = null
                assertTrue(f.locs.add(missing, 30))
                assertTrue(f.clickIce(missing, helper))
                assertNotNull(
                    find(f, missing),
                    "Failed add must not own a later identical native loc",
                )
                assertTrue(blocked(f, missing.coords))
                val selected = segment(owned)
                assertTrue(f.clickIce(selected, helper))
                assertNull(find(f, selected))
                f.tick(5)
                assertTrue(f.hits().isEmpty())
                assertGone(f, owned)
                assertNotNull(find(f, missing))
            } else {
                val ring = cast(f, f.player)
                val selected = segment(ring)
                f.rejectLocDeleteAt = selected.coords
                assertTrue(f.clickIce(selected, helper))
                assertNotNull(find(f, selected))
                assertTrue(blocked(f, selected.coords))
                f.rejectLocDeleteAt = null
                assertTrue(f.clickIce(selected, helper))
                assertNull(find(f, selected), "Failed delete must retain the pending rescue")
                f.tick(5)
                assertTrue(f.hits().isEmpty())
                assertGone(f, ring)
            }
            f.processPlayerHits(1)
            assertEquals(99, f.player.hitpoints)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `recast after emission cancels old impact and preserves the replacement impact once`() {
        val f = world()
        cast(f, f.player)
        f.tick(5)
        assertEquals(1, f.hits().size)
        val replacement = cast(f, f.player)
        f.processPlayerHits(1)
        assertEquals(99, f.player.hitpoints, "Recast must invalidate the already queued old hit")
        assertTrue(f.hits().isEmpty())
        assertTrue(replacement.all { find(f, it) != null })
        f.tick(5)
        assertEquals(1, f.hits().size, "Old impact must not consume replacement ownership")
        f.processPlayerHits(1)
        assertEquals(24, f.player.hitpoints)
        assertGone(f, replacement)
        f.tick(6)
        assertTrue(f.hits().isEmpty())
    }

    @Test
    fun `rescue is accepted before expiry but rejected on its exact cycle before native removal`() {
        for (exactDeadline in listOf(false, true)) {
            val f = world()
            val issued = f.clock.cycle
            val ring = cast(f, f.player)
            val selected = segment(ring)
            f.tick(if (exactDeadline) 4 else 3)
            assertEquals(issued + if (exactDeadline) 5 else 4, f.clock.cycle)
            assertNotNull(
                find(f, selected),
                "Inspect the interaction before native post-tick removal",
            )
            assertTrue(f.hits().isEmpty())
            assertTrue(f.clickIce(selected, requireNotNull(f.partner)))
            if (exactDeadline) assertNotNull(find(f, selected)) else assertNull(find(f, selected))
            f.tick(2)
            assertEquals(if (exactDeadline) 1 else 0, f.hits().size)
            f.processPlayerHits(1)
            assertEquals(if (exactDeadline) 24 else 99, f.player.hitpoints)
            assertGone(f, ring)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `phase handoff cancels pending callback but preserves an already emitted native hit`() {
        for (afterEmission in listOf(false, true)) {
            val f = world()
            val boss = f.npc(11278)
            boss.hitpoints = 681
            f.damage(boss, 20)
            f.tick() // Native impact calls Glacies, retaining the ICE elemental phase.
            val ring = cast(f, f.player)
            if (afterEmission) {
                f.tick(5)
                assertEquals(1, f.hits().size)
            }
            // Existing production death hook is phase setup, not a guard death timing assertion.
            assertEquals(
                NexEncounterManager.DeathKind.BODYGUARD,
                f.manager.handleDeath(f.npc(11286)),
            )
            assertEquals("ZAROS", f.encounters.of(boss).currentPhaseName)
            if (!afterEmission) f.tick(5)
            assertEquals(if (afterEmission) 1 else 0, f.hits().size)
            f.processPlayerHits(1)
            assertEquals(if (afterEmission) 24 else 99, f.player.hitpoints)
            assertGone(f, ring)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `departure and target identity reuse cancel only the departing prison at callback and impact`() {
        val departures: List<(NexMinionTestWorld) -> Unit> =
            listOf(
                { it.manager.playerLoggedOut(it.player) },
                { it.player.coords = NEX_BANK_RETURN },
                { it.player.clientDisconnected.set(true) },
                { it.player.pendingLogout = true },
                { it.player.loggingOut = true },
                { it.manager.playerDied(it.player) },
                {
                    it.players[it.player.slotId] =
                        Player().apply {
                            slotId = it.player.slotId
                            uuid = 999
                            observerUUID = 999
                            assignUid()
                        }
                },
            )
        for (afterCallback in listOf(false, true)) for ((index, depart) in departures.withIndex()) {
            val f = world()
            val partner = requireNotNull(f.partner).apply { coords = SECOND_TARGET }
            val first = cast(f, f.player)
            val second = cast(f, partner)
            if (afterCallback) {
                f.tick(5)
                assertEquals(1, f.hits().size)
                assertEquals(1, f.hits(partner).size)
            }
            depart(f)
            if (afterCallback) f.processPlayerHits(1)
            else {
                f.tick(5)
                assertTrue(f.hits().isEmpty(), "departure=$index")
                assertEquals(1, f.hits(partner).size)
                f.processPlayerHits(1)
            }
            assertEquals(99, f.player.hitpoints, "departure=$index afterCallback=$afterCallback")
            assertEquals(24, partner.hitpoints)
            f.tick(2)
            assertGone(f, first + second)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `retired room clears owned locs and old timers cannot delete a later native replacement`() {
        for (deleteSource in listOf(false, true)) {
            val f = world()
            val ring = cast(f, f.player)
            val selected = segment(ring)
            val helper = requireNotNull(f.partner)
            helper.lastMovement = helper.processedMapClock - 1
            assertTrue(f.clickIce(selected, helper))
            val oldBoss = f.npc(11278)
            val oldEncounter = f.encounters.of(oldBoss)
            if (deleteSource) f.repository.del(oldBoss, Int.MAX_VALUE)
            else {
                f.manager.playerLoggedOut(f.player)
                f.manager.playerLoggedOut(helper)
            }
            f.tick(2)
            assertTrue(oldEncounter.isEnded)
            assertNotSame(oldBoss, f.npc(11278))
            assertGone(f, ring)
            assertTrue(f.hits().isEmpty())
            assertTrue(f.hits(helper).isEmpty())
            assertTrue(f.locs.add(selected, 30))
            assertNotNull(find(f, selected))
            f.tick(10)
            assertNotNull(find(f, selected), "A retired prison timer deleted the later replacement")
            assertTrue(blocked(f, selected.coords))
            assertTrue(f.locs.del(selected, Int.MAX_VALUE))
            assertFalse(blocked(f, selected.coords))
            framework().clearInlineMocks()
        }
    }

    private fun world(): NexMinionTestWorld =
        NexMinionTestWorld(duo = true, nativeLocs = true).apply {
            reachPhase(NexState.ICE)
            player.coords = FIRST_TARGET
            requireNotNull(partner).coords = HELPER_TILE
            player.queueList.clear()
            partner?.queueList?.clear()
        }

    private fun cast(f: NexMinionTestWorld, target: Player): List<LocInfo> {
        f.registeredSpecial(target) // Preserve existing containment/prison alternation.
        f.registeredSpecial(target)
        val ring = ring(f, target.coords)
        assertEquals(8, ring.size)
        f.clock.tick()
        return ring
    }

    private fun ring(f: NexMinionTestWorld, center: CoordGrid): List<LocInfo> =
        (-1..1).flatMap { x ->
            (-1..1)
                .filterNot { z -> x == 0 && z == 0 }
                .flatMap { z ->
                    f.locs
                        .findAll(center.translate(x, z))
                        .filter { it.id in setOf(42943, 42944) }
                        .toList()
                }
        }

    private fun segment(ring: List<LocInfo>): LocInfo = ring.first { it.id == 42944 }

    private fun find(f: NexMinionTestWorld, loc: LocInfo): LocInfo? =
        f.locs.findExact(loc.coords, requireNotNull(ServerCacheManager.getObject(loc.id)))

    private fun blocked(f: NexMinionTestWorld, coords: CoordGrid): Boolean =
        f.collision[coords.x, coords.z, coords.level] and CollisionFlag.LOC != 0

    private fun assertGone(f: NexMinionTestWorld, ring: List<LocInfo>) {
        for (loc in ring) {
            assertNull(find(f, loc), "Native location remains at ${loc.coords}")
            assertFalse(blocked(f, loc.coords), "Native loc collision remains at ${loc.coords}")
        }
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        private val FIRST_TARGET = CoordGrid(2915, 5200)
        private val SECOND_TARGET = CoordGrid(2933, 5200)
        private val HELPER_TILE = CoordGrid(2915, 5198)
        private val UNRELATED_TILE = CoordGrid(2935, 5212)

        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
