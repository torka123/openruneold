package org.rsmod.content.skills.sailing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class SailingNavigationTest {
    @Test
    fun `production surface never treats zero collision as proof of water`() {
        val collision = CollisionFlagMap()
        val tile = CoordGrid(100, 100, 0)
        collision.allocateIfAbsent(tile.x, tile.z, tile.level)
        val surface = Revision240SailableTileMap()
        val result =
            SailingMotionValidator(
                    collision = collision,
                    surface = surface,
                    expectedRevision = surface.revision,
                    expectedEvidenceHash = surface.evidenceHash,
                )
                .validate(intent(tile, dx = WorldEntity.FINE_UNITS_PER_TILE))

        assertEquals(0, collision[tile.x, tile.z, tile.level])
        assertEquals(SailingTileClass.Unknown, surface.classify(tile))
        assertEquals(SailingMotionValidation.Blocked(SailingMotionBlock.UnknownTile(tile)), result)
    }

    @Test
    fun `unknown and explicitly blocked tiles deny movement`() {
        val collision = allocatedCollision(99..103, 100..100)
        val start = CoordGrid(100, 100, 0)

        val unknown =
            validator(collision, FixedSurface(sailable = setOf(start)))
                .validate(intent(start, dx = WorldEntity.FINE_UNITS_PER_TILE))
        assertEquals(
            SailingMotionValidation.Blocked(SailingMotionBlock.UnknownTile(start.translate(1, 0))),
            unknown,
        )

        val blockedTile = start.translate(1, 0)
        val blocked =
            validator(
                    collision,
                    FixedSurface(sailable = setOf(start), blocked = setOf(blockedTile)),
                )
                .validate(intent(start, dx = WorldEntity.FINE_UNITS_PER_TILE))
        assertEquals(
            SailingMotionValidation.Blocked(SailingMotionBlock.BlockedTile(blockedTile)),
            blocked,
        )
    }

    @Test
    fun `swept path cannot tunnel through an intermediate static blocker`() {
        val collision = allocatedCollision(99..105, 99..101)
        val start = CoordGrid(100, 100, 0)
        val blockedTile = start.translate(2, 0)
        collision.add(blockedTile.x, blockedTile.z, blockedTile.level, CollisionFlag.LOC)
        val surface = FixedSurface(sailable = (100..104).map { CoordGrid(it, 100, 0) }.toSet())

        val result = validator(collision, surface).validate(intent(start, dx = 448))

        assertEquals(
            SailingMotionValidation.Blocked(
                SailingMotionBlock.StaticCollision(start.translate(1, 0), blockedTile)
            ),
            result,
        )
    }

    @Test
    fun `diagonal sweep rejects corner clipping`() {
        val collision = allocatedCollision(99..103, 99..103)
        val start = CoordGrid(100, 100, 0)
        collision.add(101, 100, 0, CollisionFlag.LOC)
        val surface =
            FixedSurface(sailable = setOf(start, start.translate(1, 1), start.translate(2, 2)))

        val result = validator(collision, surface).validate(intent(start, dx = 256, dz = 256))

        assertEquals(
            SailingMotionValidation.Blocked(
                SailingMotionBlock.StaticCollision(start, start.translate(1, 1))
            ),
            result,
        )
    }

    @Test
    fun `unloaded and out of world paths fail closed`() {
        val start = CoordGrid(100, 100, 0)
        val unloaded =
            validator(CollisionFlagMap(), FixedSurface(sailable = setOf(start)))
                .validate(intent(start, dx = 128))
        assertEquals(
            SailingMotionValidation.Blocked(SailingMotionBlock.UnloadedTile(start)),
            unloaded,
        )

        val collision = allocatedCollision(0..1, 0..1)
        val outOfWorld =
            validator(collision, FixedSurface(sailable = setOf(CoordGrid(0, 0, 0))))
                .validate(
                    BoatMotionIntent(
                        startFineX = 16,
                        startFineZ = 16,
                        level = 0,
                        angle = 0,
                        speed = 32,
                        vector = SailingFineVector(-32, 0),
                    )
                )
        assertEquals(SailingMotionValidation.Blocked(SailingMotionBlock.OutOfWorld), outOfWorld)
    }

    @Test
    fun `surface revision and checksum mismatches fail before tile probes`() {
        val collision = allocatedCollision(99..102, 99..101)
        val start = CoordGrid(100, 100, 0)
        val wrongRevision =
            FixedSurface(sailable = setOf(start, start.translate(1, 0)), revision = 239)
        val wrongHash =
            FixedSurface(sailable = setOf(start, start.translate(1, 0)), evidenceHash = "other")

        assertEquals(
            SailingMotionValidation.Blocked(
                SailingMotionBlock.SurfaceRevisionMismatch(EXPECTED_REVISION, 239)
            ),
            validator(collision, wrongRevision)
                .validate(intent(start, dx = WorldEntity.FINE_UNITS_PER_TILE)),
        )
        assertEquals(
            SailingMotionValidation.Blocked(
                SailingMotionBlock.SurfaceHashMismatch(EXPECTED_HASH, "other")
            ),
            validator(collision, wrongHash)
                .validate(intent(start, dx = WorldEntity.FINE_UNITS_PER_TILE)),
        )
    }

    @Test
    fun `blocked movement leaves every boat state and mooring unchanged`() {
        val boat = boat()
        boat.dockId = 0
        boat.targetAngle = 512
        boat.targetSpeed = 320
        val before =
            listOf(
                boat.entity.fineX,
                boat.entity.fineZ,
                boat.entity.angle,
                boat.speed,
                boat.targetSpeed,
            )

        val result =
            BoatKinematicsKernel.step(boat) {
                SailingMotionValidation.Blocked(SailingMotionBlock.UnknownTile(boat.entity.coords))
            }

        assertIs<BoatMotionStepResult.Blocked>(result)
        assertEquals(
            before,
            listOf(
                boat.entity.fineX,
                boat.entity.fineZ,
                boat.entity.angle,
                boat.speed,
                boat.targetSpeed,
            ),
        )
        assertEquals(0, boat.dockId)
    }

    @Test
    fun `known clear swept path is accepted`() {
        val collision = allocatedCollision(99..105, 99..101)
        val start = CoordGrid(100, 100, 0)
        val surface = FixedSurface(sailable = (100..104).map { CoordGrid(it, 100, 0) }.toSet())

        assertEquals(
            SailingMotionValidation.Accepted,
            validator(collision, surface).validate(intent(start, dx = 448)),
        )
    }

    private fun validator(
        collision: CollisionFlagMap,
        surface: SailableTileMap,
    ): SailingMotionValidator =
        SailingMotionValidator(
            collision = collision,
            surface = surface,
            expectedRevision = EXPECTED_REVISION,
            expectedEvidenceHash = EXPECTED_HASH,
        )

    private fun intent(start: CoordGrid, dx: Int = 0, dz: Int = 0): BoatMotionIntent =
        BoatMotionIntent(
            startFineX = WorldEntity.tileToFine(start.x),
            startFineZ = WorldEntity.tileToFine(start.z),
            level = start.level,
            angle = 0,
            speed = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)),
            vector = SailingFineVector(dx, dz),
        )

    private fun allocatedCollision(xRange: IntRange, zRange: IntRange): CollisionFlagMap {
        val collision = CollisionFlagMap()
        for (x in xRange) {
            for (z in zRange) {
                collision.allocateIfAbsent(x, z, 0)
            }
        }
        return collision
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
                fineX = WorldEntity.tileToFine(100),
                fineZ = WorldEntity.tileToFine(100),
                projectedLevel = 0,
                angle = 0,
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

    private class FixedSurface(
        private val sailable: Set<CoordGrid>,
        private val blocked: Set<CoordGrid> = emptySet(),
        override val revision: Int = EXPECTED_REVISION,
        override val evidenceHash: String = EXPECTED_HASH,
    ) : SailableTileMap {
        override fun classify(tile: CoordGrid): SailingTileClass =
            when (tile) {
                in sailable -> SailingTileClass.Sailable
                in blocked -> SailingTileClass.Blocked
                else -> SailingTileClass.Unknown
            }
    }

    private companion object {
        private const val EXPECTED_REVISION = 240
        private const val EXPECTED_HASH = "test-fixture"
    }
}
