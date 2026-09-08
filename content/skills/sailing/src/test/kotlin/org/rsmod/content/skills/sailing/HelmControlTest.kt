package org.rsmod.content.skills.sailing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid

class HelmControlTest {
    @Test
    fun `first heading starts toward the requested direction without departing early`() {
        val boat = boat(angle = 512)
        boat.dockId = 0
        boat.moveMode = SailingMoveModes.HELM_IDLE

        assertTrue(SailingHelmControl.applyHeading(boat, heading = 0))

        assertEquals(SailingMoveModes.FULL, boat.moveMode)
        assertEquals(boat.type.baseSpeed, boat.targetSpeed)
        assertEquals(0, boat.targetAngle)
        assertEquals(0, boat.dockId)

        val result =
            BoatKinematicsKernel.step(boat) {
                SailingMotionValidation.Blocked(SailingMotionBlock.UnknownTile(boat.entity.coords))
            }

        assertIs<BoatMotionStepResult.Blocked>(result)
        assertEquals(0, boat.speed)
        assertEquals(512, boat.entity.angle)
        assertEquals(0, boat.dockId)
    }

    @Test
    fun `accepted first movement clears the dock only when movement commits`() {
        val boat = boat(angle = 0)
        boat.dockId = 0
        boat.moveMode = SailingMoveModes.HELM_IDLE

        SailingHelmControl.applyHeading(boat, heading = 0)
        val result = BoatKinematicsKernel.step(boat)

        assertIs<BoatMotionStepResult.Committed>(result)
        assertEquals(64, boat.speed)
        assertEquals(null, boat.dockId)
    }

    @Test
    fun `invalid heading cannot mutate boat state`() {
        val boat = boat(angle = 256)
        boat.dockId = 0
        boat.moveMode = SailingMoveModes.HELM_IDLE

        assertFalse(SailingHelmControl.applyHeading(boat, heading = 16))

        assertEquals(SailingMoveModes.HELM_IDLE, boat.moveMode)
        assertEquals(0, boat.targetSpeed)
        assertEquals(256, boat.targetAngle)
        assertEquals(0, boat.dockId)
    }

    @Test
    fun `always-start policy restarts a stopped boat when setting a heading`() {
        val boat = boat(angle = 0)
        boat.moveMode = SailingMoveModes.STOPPED

        assertTrue(
            SailingHelmControl.applyHeading(
                boat,
                heading = 4,
                startPolicy = SailingHelmControl.START_ALWAYS,
            )
        )

        assertEquals(SailingMoveModes.FULL, boat.moveMode)
        assertEquals(boat.type.baseSpeed, boat.targetSpeed)
        assertEquals(512, boat.targetAngle)
    }

    @Test
    fun `never-start policy changes heading without starting the boat`() {
        val boat = boat(angle = 0)
        boat.moveMode = SailingMoveModes.HELM_IDLE

        assertTrue(
            SailingHelmControl.applyHeading(
                boat,
                heading = 6,
                startPolicy = SailingHelmControl.START_NEVER,
            )
        )

        assertEquals(SailingMoveModes.HELM_IDLE, boat.moveMode)
        assertEquals(0, boat.targetSpeed)
        assertEquals(768, boat.targetAngle)
    }

    private fun boat(angle: Int): Boat {
        val type = BoatTypes.RAFT
        val entity =
            WorldEntity(
                id = type.worldEntityType,
                sizeX = type.sizeZonesX,
                sizeZ = type.sizeZonesZ,
                southWestZoneX = 100,
                southWestZoneZ = 100,
                fineX = START_FINE_X,
                fineZ = START_FINE_Z,
                projectedLevel = 0,
                angle = angle,
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

    private companion object {
        private const val START_FINE_X = 400_000
        private const val START_FINE_Z = 400_000
    }
}
