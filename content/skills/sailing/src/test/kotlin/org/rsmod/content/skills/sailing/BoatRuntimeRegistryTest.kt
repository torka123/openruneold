package org.rsmod.content.skills.sailing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BoatRuntimeRegistryTest {
    @Test
    fun `deploy creates an immutable indexed lease`() {
        val registry = BoatRuntimeRegistry()
        val id = SailingBoatId(100)

        val deployed = assertIs<BoatDeployResult.Success>(registry.deploy(id, 10, 7, 2))
        assertEquals(emptySet(), deployed.lease.occupantIds)
        assertEquals(deployed.lease, registry.boat(id))
        assertEquals(deployed.lease, registry.boatForOwner(10))
        assertEquals(deployed.lease, registry.boatForEntitySlot(7))

        registry.board(id, 20)
        assertEquals(emptySet(), deployed.lease.occupantIds)
        assertEquals(setOf(20L), registry.boat(id)?.occupantIds)
    }

    @Test
    fun `deploy rejects duplicate boat owner and entity slot independently`() {
        val registry = BoatRuntimeRegistry()
        val first = SailingBoatId(100)
        assertIs<BoatDeployResult.Success>(registry.deploy(first, 10, 7, 2))

        assertIs<BoatDeployResult.BoatAlreadyLive>(registry.deploy(first, 11, 8, 2))
        assertIs<BoatDeployResult.OwnerAlreadyHasBoat>(
            registry.deploy(SailingBoatId(101), 10, 8, 2)
        )
        assertIs<BoatDeployResult.EntitySlotInUse>(registry.deploy(SailingBoatId(102), 11, 7, 2))
        assertEquals(1, registry.count())
    }

    @Test
    fun `deploy rejects invalid owner slot and capacity without mutation`() {
        val registry = BoatRuntimeRegistry()

        assertEquals(BoatDeployResult.InvalidLease, registry.deploy(SailingBoatId(1), 0, 1, 1))
        assertEquals(BoatDeployResult.InvalidLease, registry.deploy(SailingBoatId(2), 1, 0, 1))
        assertEquals(BoatDeployResult.InvalidLease, registry.deploy(SailingBoatId(3), 1, 1, 0))
        assertEquals(0, registry.count())
    }

    @Test
    fun `boarding is idempotent and enforces exact player capacity`() {
        val registry = BoatRuntimeRegistry()
        val id = SailingBoatId(100)
        registry.deploy(id, 10, 7, 2)

        assertIs<BoatBoardResult.Success>(registry.board(id, 20))
        assertIs<BoatBoardResult.AlreadyAboard>(registry.board(id, 20))
        assertIs<BoatBoardResult.Success>(registry.board(id, 21))
        val full = assertIs<BoatBoardResult.Full>(registry.board(id, 22))

        assertEquals(setOf(20L, 21L), full.lease.occupantIds)
        assertNull(registry.boatForPlayer(22))
    }

    @Test
    fun `a player cannot occupy two boats`() {
        val registry = BoatRuntimeRegistry()
        val first = SailingBoatId(100)
        val second = SailingBoatId(101)
        registry.deploy(first, 10, 7, 2)
        registry.deploy(second, 11, 8, 2)
        registry.board(first, 20)

        val rejected = assertIs<BoatBoardResult.AboardOtherBoat>(registry.board(second, 20))

        assertEquals(first, rejected.boatId)
        assertEquals(first, registry.boatForPlayer(20)?.boatId)
        assertEquals(emptySet(), registry.boat(second)?.occupantIds)
    }

    @Test
    fun `owner-only boarding and helm authority fail closed`() {
        val registry = BoatRuntimeRegistry()
        val id = SailingBoatId(100)
        registry.deploy(id, ownerId = 10, entitySlot = 7, playerCapacity = 2)

        assertIs<BoatBoardResult.NotOwner>(registry.boardOwner(id, 20))
        assertEquals(emptySet(), registry.boat(id)?.occupantIds)
        assertEquals(false, registry.canHelm(7, 10))
        assertEquals(false, registry.canHelm(7, 20))

        assertIs<BoatBoardResult.Success>(registry.boardOwner(id, 10))
        assertEquals(false, registry.canHelm(7, 10))
        assertEquals(BoatHelmClaimResult.NotAboard, registry.claimHelm(7, 20))
        assertIs<BoatHelmClaimResult.Success>(registry.claimHelm(7, 10))
        assertEquals(true, registry.canHelm(7, 10))
        assertEquals(false, registry.canHelm(7, 20))

        assertIs<BoatHelmReleaseResult.Success>(registry.releaseHelm(10))
        assertEquals(false, registry.canHelm(7, 10))
        assertIs<BoatHelmClaimResult.Success>(registry.claimHelm(7, 10))
        registry.leave(10)
        assertEquals(false, registry.canHelm(7, 10))
        assertEquals(BoatHelmReleaseResult.NotHelmsman, registry.releaseHelm(10))
        assertEquals(BoatBoardResult.InvalidPlayer, registry.boardOwner(id, 0))
        assertEquals(BoatBoardResult.MissingBoat, registry.boardOwner(SailingBoatId(101), 10))
    }

    @Test
    fun `leave is idempotent and permits boarding another boat`() {
        val registry = BoatRuntimeRegistry()
        val first = SailingBoatId(100)
        val second = SailingBoatId(101)
        registry.deploy(first, 10, 7, 1)
        registry.deploy(second, 11, 8, 1)
        registry.board(first, 20)

        assertEquals(BoatLeaveResult.Success(first), registry.leave(20))
        assertEquals(BoatLeaveResult.NotAboard, registry.leave(20))
        assertIs<BoatBoardResult.Success>(registry.board(second, 20))
        assertEquals(second, registry.boatForPlayer(20)?.boatId)
    }

    @Test
    fun `despawn clears every index and allows a clean redeploy`() {
        val registry = BoatRuntimeRegistry()
        val id = SailingBoatId(100)
        registry.deploy(id, 10, 7, 2)
        registry.board(id, 20)
        registry.board(id, 21)

        val removed = assertIs<BoatRuntimeDespawnResult.Success>(registry.despawn(id))

        assertEquals(setOf(20L, 21L), removed.lease.occupantIds)
        assertNull(registry.boat(id))
        assertNull(registry.boatForOwner(10))
        assertNull(registry.boatForEntitySlot(7))
        assertNull(registry.boatForPlayer(20))
        assertEquals(BoatRuntimeDespawnResult.MissingBoat, registry.despawn(id))
        assertIs<BoatDeployResult.Success>(registry.deploy(id, 10, 7, 2))
    }

    @Test
    fun `boarding rejects missing boats and invalid player ids`() {
        val registry = BoatRuntimeRegistry()
        val id = SailingBoatId(100)

        assertEquals(BoatBoardResult.MissingBoat, registry.board(id, 20))
        registry.deploy(id, 10, 7, 2)
        assertEquals(BoatBoardResult.InvalidPlayer, registry.board(id, 0))
        assertEquals(BoatBoardResult.InvalidPlayer, registry.board(id, -1))
    }

    @Test
    fun `owned boat slots unlock at exact OSRS levels`() {
        val expected =
            mapOf(
                0 to 0,
                1 to 1,
                14 to 1,
                15 to 2,
                49 to 2,
                50 to 3,
                77 to 3,
                78 to 4,
                90 to 4,
                91 to 5,
                99 to 5,
            )
        for ((level, slots) in expected) {
            assertEquals(slots, SailingBoatSlotPolicy.maxOwnedBoats(level), "level $level")
        }
    }
}
