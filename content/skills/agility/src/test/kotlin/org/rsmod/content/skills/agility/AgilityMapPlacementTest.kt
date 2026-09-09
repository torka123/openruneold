package org.rsmod.content.skills.agility

import dev.openrune.ServerCacheManager
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.GameMapSpawnSink
import dev.openrune.map.MapSingletons
import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.obj.MapObjDefinition
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.rsmod.content.skills.agility.rooftop.ObstacleMove
import org.rsmod.content.skills.agility.rooftop.RooftopCourses
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid

class AgilityMapPlacementTest {
    @Test
    fun `revision 240 contains every definition animation and expected map placement`() {
        val cache = ServerCacheManager.init(240)
        try {
            for (course in AgilityDefinitions.courses) {
                for (obstacle in course.obstacles) {
                    assertNotNull(
                        ServerCacheManager.getObject(obstacle.objectId),
                        "${course.key}: object ${obstacle.objectId}",
                    )
                    assertNotNull(
                        ServerCacheManager.getAnim(obstacle.movement.animation),
                        "${course.key}: animation ${obstacle.movement.animation}",
                    )
                    obstacle.movement.travelAnimation?.let {
                        assertNotNull(
                            ServerCacheManager.getAnim(it),
                            "${course.key}: animation $it",
                        )
                    }
                    obstacle.movement.landingAnimation?.let {
                        assertNotNull(
                            ServerCacheManager.getAnim(it),
                            "${course.key}: animation $it",
                        )
                    }
                    for (step in AgilityRoutes.routeFor(course.key, obstacle.stage)) {
                        step.animation?.let {
                            assertNotNull(
                                ServerCacheManager.getAnim(it),
                                "${course.key}: animation $it",
                            )
                        }
                        step.landingAnimation?.let {
                            assertNotNull(
                                ServerCacheManager.getAnim(it),
                                "${course.key}: animation $it",
                            )
                        }
                    }
                }
            }

            GameMapDecoder.decodeAll(NoOpSpawnSink, cache)
            val placed = mutableSetOf<Int>()
            val roofIds =
                RooftopCourses.layouts
                    .flatMap { it.obstacles }
                    .flatMap { it.locs }
                    .map { it.asRSCM(RSCMType.LOC) }
                    .toSet()
            val roofLocs = mutableMapOf<Int, MutableList<BoundLocInfo>>()
            for (zone in MapSingletons.locZones.mapLocs.zoneKeys()) {
                val entries = MapSingletons.locZones.mapLocs[zone] ?: continue
                for (entry in entries.byte2IntEntrySet()) {
                    val entity = LocEntity(entry.intValue)
                    placed += entity.id
                    if (entity.id in roofIds) {
                        val key = LocZoneKey(entry.byteKey)
                        val coords = zone.toCoords().translate(key.x, key.z)
                        val info = LocInfo(key.layer, coords, entity)
                        roofLocs
                            .getOrPut(entity.id) { mutableListOf() }
                            .add(
                                BoundLocInfo(
                                    info,
                                    assertNotNull(ServerCacheManager.getObject(entity.id)),
                                )
                            )
                    }
                }
            }
            val missing =
                AgilityDefinitions.courses
                    .flatMap { it.obstacles }
                    .map { it.objectId }
                    .filterNot { it in placed }
                    .toSet()
            assertEquals(emptySet(), missing)
            for (layout in RooftopCourses.layouts) for (obstacle in layout.obstacles) {
                for (gameval in obstacle.locs) {
                    val id = gameval.asRSCM(RSCMType.LOC)
                    val loc = assertNotNull(ServerCacheManager.getObject(id), gameval)
                    assertNotNull(loc.actions.getOpOrNull(0), "$gameval first operation")
                    assertTrue(id in placed, "$gameval must be on the active map")
                }
                val seqs =
                    when (val move = obstacle.move) {
                        is ObstacleMove.Climb -> listOf(move.seq)
                        is ObstacleMove.Leap -> listOf(move.seq)
                        is ObstacleMove.Zipline ->
                            listOf(AgilityAnims.ZIPLINE_GRAB, AgilityAnims.ZIPLINE_SLIDE)
                        is ObstacleMove.Balance ->
                            listOfNotNull(
                                move.style.ready,
                                move.style.walk,
                                move.style.mount,
                                move.style.dismount,
                            )
                    }
                for (seq in seqs) {
                    val animation =
                        assertNotNull(ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ)), seq)
                    assertTrue(animation.totalDelay > 0, "$seq must have a cache duration")
                }
            }
            // Full native approach/op pipeline against the real r240 collision and actual locs.
            // No repositioning between obstacles: a bad landing must fail the following click.
            for (layout in RooftopCourses.layouts) {
                val f = RooftopFixture(MapSingletons.collision, allocate = false)
                f.player.coords = layout.obstacles.first().start
                repeat(2) { lap ->
                    for (obstacle in layout.obstacles) {
                        val id = obstacle.locs[lap % obstacle.locs.size].asRSCM(RSCMType.LOC)
                        val loc =
                            assertNotNull(roofLocs[id]).minBy {
                                kotlin.math.abs(it.coords.x - obstacle.start.x) +
                                    kotlin.math.abs(it.coords.z - obstacle.start.z)
                            }
                        f.interact(loc)
                        assertEquals(
                            obstacle.move.destination,
                            f.player.coords,
                            "${layout.course}: ${obstacle.name} ($id at ${loc.coords})",
                        )
                    }
                    assertEquals(layout.lapXp * (lap + 1), f.xp, 0.1, layout.course.name)
                }
            }
        } finally {
            cache.close()
        }
    }

    private object NoOpSpawnSink : GameMapSpawnSink {
        override fun onNpcSpawn(def: MapNpcDefinition, coords: CoordGrid) = Unit

        override fun onObjSpawn(def: MapObjDefinition, coords: CoordGrid) = Unit
    }
}
