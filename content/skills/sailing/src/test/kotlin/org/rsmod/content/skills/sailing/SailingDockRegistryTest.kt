package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.GameMapSpawnSink
import dev.openrune.map.MapSingletons
import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.obj.MapObjDefinition
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.map.CoordGrid

@ResourceLock("sailing-server-cache")
class SailingDockRegistryTest {
    @Test
    fun `all revision-240 dock placements are exact and travel remains capture blocked`() {
        val root = Path.of("").toAbsolutePath()
        GameValProvider.load("$root${File.separator}")
        val cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), REVISION)
        try {
            GameMapDecoder.decodeAll(NoOpSpawnSink, cache)
            val definitions = SailingDockDefinitionRegistry()
            val registry = SailingDockRegistry(definitions, MapSingletons.locZones)

            assertEquals(61, registry.all.size)
            assertEquals(
                SailingDockRegistry.EXPECTED_PARENT_PLACEMENTS,
                registry.all.sumOf { it.interactionPlacements.size },
            )
            assertEquals(
                SailingDockRegistry.EXPECTED_BUOY_PLACEMENTS,
                registry.all.sumOf { it.buoyPlacements.size },
            )
            assertEquals(
                34,
                registry.all.count {
                    it.definition.interactionKind == SailingDockInteractionKind.Gangplank
                },
            )
            assertEquals(
                27,
                registry.all.count {
                    it.definition.interactionKind == SailingDockInteractionKind.Mooring
                },
            )

            for (entry in registry.all) {
                assertSame(entry, registry.byDockId(entry.definition.dockId))
                assertSame(entry, registry.byRowId(entry.definition.rowId))
                assertSame(entry, registry.byRowKey(entry.definition.rowKey))
                assertSame(entry, registry.byParentLocId(entry.definition.parentLocId))
                assertSame(entry, registry.byBuoyLocId(entry.definition.buoyLocId))
                for (placement in entry.interactionPlacements) {
                    assertSame(entry, registry.byInteractionCoord(placement.coords))
                    assertEquals(entry.definition.parentLocId, placement.locId)
                    assertEquals(entry.definition.parentLocKey, placement.locKey)
                }
                for (placement in entry.buoyPlacements) {
                    assertSame(entry, registry.byBuoyCoord(placement.coords))
                    assertEquals(entry.definition.buoyLocId, placement.locId)
                    assertEquals(entry.definition.buoyLocKey, placement.locKey)
                }
                val blocked =
                    assertIs<SailingDockOperationalGeometry.BlockedByCapture>(entry.operational)
                assertEquals(MissingSailingDockField.entries.toSet(), blocked.missingFields)
            }

            assertParentPlacements(registry)
            assertBuoyPlacements(registry)
            assertEquals(52, registry.byDockId(22)?.definition?.levelRequired)
            assertNull(registry.byInteractionCoord(CoordGrid(0, 0, 0)))
            assertNull(registry.byBuoyCoord(CoordGrid(0, 0, 0)))
            assertNull(registry.byDockId(-1))
        } finally {
            cache.close()
        }
    }

    private fun assertParentPlacements(registry: SailingDockRegistry) {
        val portSarim = requireNotNull(registry.byDockId(0))
        assertEquals(
            listOf(CoordGrid(3051, 3193, 0)),
            portSarim.interactionPlacements.map { it.coords },
        )
        assertEquals(listOf(22), portSarim.interactionPlacements.map { it.shape })
        assertEquals(listOf(3), portSarim.interactionPlacements.map { it.angle })
        assertEquals(listOf(3), portSarim.interactionPlacements.map { it.layer })

        val prifddinas = requireNotNull(registry.byDockId(29))
        assertEquals(
            listOf(CoordGrid(2158, 3324, 0), CoordGrid(3182, 6076, 0)),
            prifddinas.interactionPlacements.map { it.coords },
        )
        assertEquals(2, prifddinas.buoyPlacements.size)

        assertEquals(
            listOf(CoordGrid(2568, 2295, 0), CoordGrid(2568, 2296, 0)),
            requireNotNull(registry.byDockId(59)).interactionPlacements.map { it.coords },
        )
        assertEquals(
            listOf(CoordGrid(2582, 8605, 0), CoordGrid(2582, 8606, 0)),
            requireNotNull(registry.byDockId(60)).interactionPlacements.map { it.coords },
        )
    }

    private fun assertBuoyPlacements(registry: SailingDockRegistry) {
        assertEquals(
            listOf(CoordGrid(2569, 2298, 0)),
            requireNotNull(registry.byDockId(59)).buoyPlacements.map { it.coords },
        )
        assertEquals(
            listOf(CoordGrid(2578, 8608, 0)),
            requireNotNull(registry.byDockId(60)).buoyPlacements.map { it.coords },
        )
    }

    private object NoOpSpawnSink : GameMapSpawnSink {
        override fun onNpcSpawn(def: MapNpcDefinition, coords: CoordGrid) = Unit

        override fun onObjSpawn(def: MapObjDefinition, coords: CoordGrid) = Unit
    }

    private companion object {
        private const val REVISION = 240
    }
}
