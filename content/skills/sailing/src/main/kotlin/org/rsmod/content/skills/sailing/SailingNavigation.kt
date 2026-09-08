package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.collision.CollisionStrategy

internal enum class SailingTileClass {
    Sailable,
    Blocked,
    Unknown,
}

/**
 * A revision- and evidence-bound authority for Sailing navigation tiles.
 *
 * A normal collision flag of zero is not proof of water in revision 240. Implementations must be
 * built from a captured, checksummed fixture; every tile outside that fixture remains [Unknown].
 */
internal interface SailableTileMap {
    val revision: Int
    val evidenceHash: String

    fun classify(tile: CoordGrid): SailingTileClass
}

/**
 * Production placeholder until the complete revision-240 Sailing surface is captured. This is
 * deliberately fail-closed and must not be replaced with collision-mask or underlay guesses.
 */
@Singleton
internal class Revision240SailableTileMap @Inject constructor() : SailableTileMap {
    override val revision: Int = REVISION
    override val evidenceHash: String = EVIDENCE_HASH

    override fun classify(tile: CoordGrid): SailingTileClass = SailingTileClass.Unknown

    companion object {
        const val REVISION: Int = 240
        const val EVIDENCE_HASH: String = "capture-required"
    }
}

internal sealed interface SailingMotionBlock {
    data object OutOfWorld : SailingMotionBlock

    data class SurfaceRevisionMismatch(val expected: Int, val actual: Int) : SailingMotionBlock

    data class SurfaceHashMismatch(val expected: String, val actual: String) : SailingMotionBlock

    data class UnloadedTile(val tile: CoordGrid) : SailingMotionBlock

    data class UnknownTile(val tile: CoordGrid) : SailingMotionBlock

    data class BlockedTile(val tile: CoordGrid) : SailingMotionBlock

    data class StaticCollision(val from: CoordGrid, val to: CoordGrid) : SailingMotionBlock
}

internal sealed interface SailingMotionValidation {
    data object Accepted : SailingMotionValidation

    data class Blocked(val reason: SailingMotionBlock) : SailingMotionValidation
}

@Singleton
class SailingNavigationAuthority
@Inject
internal constructor(collision: CollisionFlagMap, surface: Revision240SailableTileMap) {
    private val validator =
        SailingMotionValidator(
            collision = collision,
            surface = surface,
            expectedRevision = Revision240SailableTileMap.REVISION,
            expectedEvidenceHash = Revision240SailableTileMap.EVIDENCE_HASH,
        )

    internal fun validate(intent: BoatMotionIntent): SailingMotionValidation =
        validator.validate(intent)
}

/** Validates the complete centre path without publishing intermediate world-entity updates. */
internal class SailingMotionValidator(
    private val collision: CollisionFlagMap,
    private val surface: SailableTileMap,
    private val expectedRevision: Int,
    private val expectedEvidenceHash: String,
) {
    private val steps = StepValidator(collision)

    fun validate(intent: BoatMotionIntent): SailingMotionValidation {
        if (!intent.moves) return SailingMotionValidation.Accepted
        if (surface.revision != expectedRevision) {
            return blocked(
                SailingMotionBlock.SurfaceRevisionMismatch(expectedRevision, surface.revision)
            )
        }
        if (surface.evidenceHash != expectedEvidenceHash) {
            return blocked(
                SailingMotionBlock.SurfaceHashMismatch(expectedEvidenceHash, surface.evidenceHash)
            )
        }
        if (!inWorld(intent.startFineX.toLong(), intent.startFineZ.toLong())) {
            return blocked(SailingMotionBlock.OutOfWorld)
        }
        if (!inWorld(intent.endFineX, intent.endFineZ)) {
            return blocked(SailingMotionBlock.OutOfWorld)
        }

        var previous = tile(intent.startFineX.toLong(), intent.startFineZ.toLong(), intent.level)
        validateTile(previous)?.let {
            return blocked(it)
        }

        val sampleCount =
            (max(abs(intent.vector.dx), abs(intent.vector.dz)) + FINE_SAMPLE_SIZE - 1) /
                FINE_SAMPLE_SIZE
        for (sample in 1..sampleCount) {
            val fineX =
                intent.startFineX.toLong() + intent.vector.dx.toLong() * sample / sampleCount
            val fineZ =
                intent.startFineZ.toLong() + intent.vector.dz.toLong() * sample / sampleCount
            val next = tile(fineX, fineZ, intent.level)
            if (next == previous) continue

            validateTile(next)?.let {
                return blocked(it)
            }
            val dx = next.x - previous.x
            val dz = next.z - previous.z
            if (
                dx !in -1..1 ||
                    dz !in -1..1 ||
                    !steps.canTravel(
                        level = previous.level,
                        x = previous.x,
                        z = previous.z,
                        offsetX = dx,
                        offsetZ = dz,
                        size = 1,
                        collision = CollisionStrategy.Normal,
                    )
            ) {
                return blocked(SailingMotionBlock.StaticCollision(previous, next))
            }
            previous = next
        }
        return SailingMotionValidation.Accepted
    }

    private fun validateTile(tile: CoordGrid): SailingMotionBlock? {
        if (!collision.isZoneAllocated(tile.x, tile.z, tile.level)) {
            return SailingMotionBlock.UnloadedTile(tile)
        }
        return when (surface.classify(tile)) {
            SailingTileClass.Sailable -> null
            SailingTileClass.Blocked -> SailingMotionBlock.BlockedTile(tile)
            SailingTileClass.Unknown -> SailingMotionBlock.UnknownTile(tile)
        }
    }

    private fun inWorld(fineX: Long, fineZ: Long): Boolean =
        fineX in 0 until WORLD_FINE_EXCLUSIVE && fineZ in 0 until WORLD_FINE_EXCLUSIVE

    private fun tile(fineX: Long, fineZ: Long, level: Int): CoordGrid =
        CoordGrid((fineX shr FINE_BITS).toInt(), (fineZ shr FINE_BITS).toInt(), level)

    private fun blocked(reason: SailingMotionBlock): SailingMotionValidation =
        SailingMotionValidation.Blocked(reason)

    private companion object {
        private const val FINE_BITS = 7
        private const val FINE_SAMPLE_SIZE = 32
        private const val WORLD_TILES = 16_384L
        private const val WORLD_FINE_EXCLUSIVE = WORLD_TILES shl FINE_BITS
    }
}
