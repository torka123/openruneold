package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Singleton
class BoatKinematics
@Inject
constructor(private val boats: BoatManager, private val navigation: SailingNavigationAuthority) {
    fun tick() {
        for (boat in boats.all) {
            BoatKinematicsKernel.step(boat, navigation::validate)
        }
    }
}

internal data class SailingFineVector(val dx: Int, val dz: Int)

internal data class BoatMotionIntent(
    val startFineX: Int,
    val startFineZ: Int,
    val level: Int,
    val angle: Int,
    val speed: Int,
    val vector: SailingFineVector,
) {
    val endFineX: Long
        get() = startFineX.toLong() + vector.dx

    val endFineZ: Long
        get() = startFineZ.toLong() + vector.dz

    val moves: Boolean
        get() = vector.dx != 0 || vector.dz != 0
}

internal sealed interface BoatMotionStepResult {
    data class Committed(val intent: BoatMotionIntent) : BoatMotionStepResult

    data class Blocked(val intent: BoatMotionIntent, val reason: SailingMotionBlock) :
        BoatMotionStepResult
}

internal object BoatKinematicsKernel {
    fun step(
        boat: Boat,
        validate: (BoatMotionIntent) -> SailingMotionValidation = {
            SailingMotionValidation.Accepted
        },
    ): BoatMotionStepResult {
        val intent = intent(boat)
        val validation = if (intent.moves) validate(intent) else SailingMotionValidation.Accepted
        if (validation is SailingMotionValidation.Blocked) {
            return BoatMotionStepResult.Blocked(intent, validation.reason)
        }
        commit(boat, intent)
        return BoatMotionStepResult.Committed(intent)
    }

    fun motionVector(angle: Int, speed: Int): SailingFineVector {
        val theta = (angle and ANGLE_MASK) * TWO_PI / ANGLE_FULL
        return SailingFineVector(
            dx = quantize(speed * -sin(theta)),
            dz = quantize(speed * -cos(theta)),
        )
    }

    private fun intent(boat: Boat): BoatMotionIntent {
        val speed = nextSpeed(boat)
        val angle = nextAngle(boat)
        return BoatMotionIntent(
            startFineX = boat.entity.fineX,
            startFineZ = boat.entity.fineZ,
            level = boat.entity.projectedLevel,
            angle = angle,
            speed = speed,
            vector = motionVector(angle, speed),
        )
    }

    private fun nextSpeed(boat: Boat): Int {
        val acceleration = boat.type.acceleration
        return when {
            boat.speed < boat.targetSpeed -> min(boat.speed + acceleration, boat.targetSpeed)
            boat.speed > boat.targetSpeed -> max(boat.speed - acceleration, boat.targetSpeed)
            else -> boat.speed
        }
    }

    private fun nextAngle(boat: Boat): Int {
        val entity = boat.entity
        val difference =
            ((boat.targetAngle - entity.angle + HALF_ANGLE) and ANGLE_MASK) - HALF_ANGLE
        if (difference == 0) return entity.angle
        val turn = difference.coerceIn(-TURN_RATE_PER_TICK, TURN_RATE_PER_TICK)
        return (entity.angle + turn) and ANGLE_MASK
    }

    private fun commit(boat: Boat, intent: BoatMotionIntent) {
        boat.speed = intent.speed
        if (intent.angle != boat.entity.angle) {
            boat.entity.updateAngle(intent.angle)
        }
        if (!intent.moves) {
            return
        }
        boat.depart()
        val entity = boat.entity
        entity.updateCoord(
            level = intent.level,
            fineX = intent.endFineX.toInt(),
            fineZ = intent.endFineZ.toInt(),
            teleport = false,
        )
    }

    private fun quantize(value: Double): Int =
        if (value >= 0) {
            (value / VELOCITY_QUANTUM + 0.5).toInt() * VELOCITY_QUANTUM
        } else {
            -((-value / VELOCITY_QUANTUM + 0.5).toInt() * VELOCITY_QUANTUM)
        }

    private const val ANGLE_FULL = 2048
    private const val ANGLE_MASK = ANGLE_FULL - 1
    private const val HALF_ANGLE = ANGLE_FULL / 2
    private const val TURN_RATE_PER_TICK = 128
    private const val VELOCITY_QUANTUM = 32
    private const val TWO_PI = 2.0 * PI
}
