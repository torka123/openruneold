package org.rsmod.content.skills.sailing

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid

class BoatKinematicsKernelTest {
    @Test
    fun `all headings and supported speeds retain the imported vector output`() {
        val digest = MessageDigest.getInstance("SHA-256")
        for (angle in 0 until ANGLE_COUNT) {
            for (speed in SUPPORTED_SPEEDS) {
                val vector = BoatKinematicsKernel.motionVector(angle, speed)
                digest.update("$angle:$speed:${vector.dx}:${vector.dz}\n".toByteArray())
            }
        }
        assertEquals(
            "a1f5aa3b0a662f48a477687be9e0facda58249bbab425bb8c231a845f9bcbf60",
            digest.digest().toHexString(),
        )
    }

    @Test
    fun `zero speed leaves the entity coordinate unchanged`() {
        val boat = boat(angle = 512)
        val before = Triple(boat.entity.fineX, boat.entity.fineZ, boat.entity.angle)

        BoatKinematicsKernel.step(boat)

        assertEquals(before, Triple(boat.entity.fineX, boat.entity.fineZ, boat.entity.angle))
    }

    @Test
    fun `speed accelerates and decelerates by one configured step per tick`() {
        val accelerating = boat(angle = 0)
        accelerating.targetSpeed = 192
        BoatKinematicsKernel.step(accelerating)
        assertEquals(64, accelerating.speed)
        assertEquals(START_FINE_Z - 64, accelerating.entity.fineZ)

        val decelerating = boat(angle = 0)
        decelerating.speed = 192
        decelerating.targetSpeed = 64
        BoatKinematicsKernel.step(decelerating)
        assertEquals(128, decelerating.speed)
        assertEquals(START_FINE_Z - 128, decelerating.entity.fineZ)
    }

    @Test
    fun `turns use the shortest wrapped path and deterministic half-turn tie break`() {
        val crossingZero = boat(angle = 2000)
        crossingZero.targetAngle = 64
        BoatKinematicsKernel.step(crossingZero)
        assertEquals(64, crossingZero.entity.angle)

        val halfTurn = boat(angle = 0)
        halfTurn.targetAngle = 1024
        BoatKinematicsKernel.step(halfTurn)
        assertEquals(1920, halfTurn.entity.angle)
    }

    @Test
    fun `long constant trajectory stays deterministic`() {
        val boat = boat(angle = 256)
        boat.targetAngle = 256
        boat.speed = 320
        boat.targetSpeed = 320

        repeat(10_000) { BoatKinematicsKernel.step(boat) }

        assertEquals(-1_840_000, boat.entity.fineX)
        assertEquals(-1_840_000, boat.entity.fineZ)
        assertEquals(256, boat.entity.angle)
        assertEquals(320, boat.speed)
    }

    @Test
    fun `seeded control trajectory retains its complete state digest`() {
        val boat = boat(angle = 0)
        var state = 0x5A17B1E3
        val digest = MessageDigest.getInstance("SHA-256")

        repeat(20_000) { tick ->
            if (tick % 17 == 0) {
                state = nextState(state)
                boat.targetAngle = state ushr 21
                state = nextState(state)
                boat.targetSpeed = SUPPORTED_SPEEDS[(state ushr 1) % SUPPORTED_SPEEDS.size]
            }
            BoatKinematicsKernel.step(boat)
            digest.update(
                "$tick:${boat.entity.fineX}:${boat.entity.fineZ}:${boat.entity.angle}:${boat.speed}\n"
                    .toByteArray()
            )
        }

        assertEquals(
            "52b9bbaf91f79bda84263a0659b69c98d9c74dffb7d17f6141785e675bd4af37",
            digest.digest().toHexString(),
        )
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

    private fun nextState(state: Int): Int = state * 1_664_525 + 1_013_904_223

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        private const val ANGLE_COUNT = 2048
        private const val START_FINE_X = 400_000
        private const val START_FINE_Z = 400_000
        private val SUPPORTED_SPEEDS = intArrayOf(0, 64, 128, 192, 256, 320, 384, 448)
    }
}
