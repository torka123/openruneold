package org.rsmod.content.skills.sailing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid

class BoatLifecycleTest {
    @Test
    fun `depart clears stale mooring ownership`() {
        val boat = boat()
        boat.dockId = 0

        boat.depart()

        assertNull(boat.dockId)
    }

    @Test
    fun `the first movement tick clears stale mooring ownership`() {
        val boat = boat()
        boat.dockId = 0
        boat.targetSpeed = boat.type.baseSpeed

        BoatKinematicsKernel.step(boat)

        assertTrue(boat.speed > 0)
        assertNull(boat.dockId)
    }

    @Test
    fun `only the owning helmsman can release the helm`() {
        val boat = boat()
        val owner = Player()
        val stranger = Player()
        boat.helmsman = owner
        boat.moveMode = SailingMoveModes.FULL
        boat.targetSpeed = boat.type.baseSpeed

        assertFalse(boat.relinquishHelm(stranger))
        assertSame(owner, boat.helmsman)
        assertEquals(SailingMoveModes.FULL, boat.moveMode)
        assertEquals(boat.type.baseSpeed, boat.targetSpeed)

        assertTrue(boat.relinquishHelm(owner))
        assertNull(boat.helmsman)
        assertEquals(SailingMoveModes.STOPPED, boat.moveMode)
        assertEquals(0, boat.targetSpeed)
    }

    private fun boat(): Boat {
        val type = BoatTypes.RAFT
        val entity =
            WorldEntity(
                id = type.worldEntityType,
                sizeX = type.sizeZonesX,
                sizeZ = type.sizeZonesZ,
                southWestZoneX = 100,
                southWestZoneZ = 100,
                fineX = 400_000,
                fineZ = 400_000,
                projectedLevel = 0,
            )
        val region =
            Region(
                southWest = CoordGrid(800, 800, 0),
                northEast = CoordGrid(808, 808, 0),
                uid = 1,
                slot = 1,
            )
        return Boat(type, entity, region)
    }
}
