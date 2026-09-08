package org.rsmod.content.skills.agility

import dev.openrune.ServerCacheManager
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.GameMapSpawnSink
import dev.openrune.map.MapSingletons
import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.obj.MapObjDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.rsmod.game.loc.LocEntity
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
            for (zone in MapSingletons.locZones.mapLocs.zoneKeys()) {
                val entries = MapSingletons.locZones.mapLocs[zone] ?: continue
                for (entry in entries.byte2IntEntrySet()) placed += LocEntity(entry.intValue).id
            }
            val missing =
                AgilityDefinitions.courses
                    .flatMap { it.obstacles }
                    .map { it.objectId }
                    .filterNot { it in placed }
                    .toSet()
            assertEquals(emptySet(), missing)
        } finally {
            cache.close()
        }
    }

    private object NoOpSpawnSink : GameMapSpawnSink {
        override fun onNpcSpawn(def: MapNpcDefinition, coords: CoordGrid) = Unit

        override fun onObjSpawn(def: MapObjDefinition, coords: CoordGrid) = Unit
    }
}
