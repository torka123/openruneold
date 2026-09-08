package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToaScabarasRegionAuditTest {
    @Test
    fun sourceMapContainsBothExactRedBarriersAndSafeCrossingTiles() =
        withCache { provider, entries ->
            val barrier = required(provider.objects[45135])
            assertEquals("Barrier", barrier.name)
            assertEquals("Pass", barrier.actions.getOpOrNull(0))
            assertEquals("Quick-Pass", barrier.actions.getOpOrNull(1))
            assertEquals(1, barrier.sizeX)
            assertEquals(1, barrier.sizeY)

            assertEquals(
                setOf(
                    LocPlacement(45135, 12, 26, shape = 10, angle = 3),
                    LocPlacement(45135, 12, 38, shape = 10, angle = 3),
                ),
                entries.filter { it.id == 45135 }.mapTo(linkedSetOf()) { it.toPlacement() },
            )

            val byTile = entries.groupBy { it.localX to it.localZ }
            assertTrue(byTile.getValue(13 to 26).any { it.id == 45159 && it.shape == 22 })
            assertTrue(byTile.getValue(13 to 38).any { it.id == 45159 && it.shape == 22 })
            assertTrue(byTile.getValue(13 to 31).any { it.id == 44558 && it.shape == 10 })
        }

    @Test
    fun sourceMapContainsExactBranchPassagesAndPuzzleFloorAnchors() = withCache { _, entries ->
        assertEquals(
            setOf(
                LocPlacement(45343, 28, 35, shape = 0, angle = 1),
                LocPlacement(45343, 28, 29, shape = 0, angle = 3),
            ),
            entries.filter { it.id == 45343 }.mapTo(linkedSetOf()) { it.toPlacement() },
        )

        val floorEntries =
            entries
                .filter { it.id == BASIC_FLOOR_LOC || it.id == DAMAGED_FLOOR_LOC }
                .associateBy { it.localX to it.localZ }
        SIDE_ROOM_GRID_ANCHORS.forEach { (northWestX, northWestZ) ->
            for (xOffset in 0..4) {
                for (zOffset in 0..4) {
                    val tile = northWestX + xOffset to northWestZ - zOffset
                    val floor = required(floorEntries[tile], "Missing Scabaras floor at $tile")
                    assertEquals(22, floor.shape, "Unexpected Scabaras floor shape at $tile")
                }
            }
        }

        assertEquals(49, floorEntries.keys.count { (x, z) -> x in 48..54 && z in 35..41 })
        assertEquals(48, floorEntries.keys.count { (x, z) -> x in 48..54 && z in 23..29 })

        assertTrue(
            entries.none { it.id in SERVER_INSERTED_LOC_IDS },
            "Runtime Scabaras locs must not be assigned guessed source-map placements.",
        )
    }

    @Test
    fun revision240DefinitionsPreserveEachScabarasMechanicFamily() = withCache { provider, _ ->
        val sequenceButton = required(provider.objects[45338])
        assertEquals("Ancient button", sequenceButton.name)
        assertEquals("Push", sequenceButton.actions.getOpOrNull(0))

        val additionTablet = required(provider.objects[45339])
        assertEquals("Ancient tablet", additionTablet.name)
        assertEquals("Inspect", additionTablet.actions.getOpOrNull(0))

        for (id in 45340..45342) {
            val plate = required(provider.objects[id])
            assertEquals("Pressure plate", plate.name)
            assertEquals(1663, plate.category)
        }
        val lightsPlate = required(provider.objects[45344])
        assertEquals("Pressure plate", lightsPlate.name)
        assertEquals("Flip", lightsPlate.actions.getOpOrNull(4))
        assertEquals(1663, lightsPlate.category)

        for (id in 45345..45353) {
            val additionTile = required(provider.objects[id])
            assertEquals("Pressure plate", additionTile.name)
            assertEquals(1664, additionTile.category)
        }
        for (id in 45356..45364) {
            val matchingButton = required(provider.objects[id])
            assertEquals("Tile", matchingButton.name)
            assertEquals("Activate", matchingButton.actions.getOpOrNull(0))
            assertEquals(1665, matchingButton.category)
        }
        for (id in 45365..45373) {
            assertEquals("Symbol", required(provider.objects[id]).name)
        }
        for (id in 45384..45395) {
            assertEquals(1666, required(provider.objects[id]).category)
        }

        val passage = required(provider.objects[45343])
        assertEquals("Passage", passage.name)
        assertEquals("Crawl-through", passage.actions.getOpOrNull(0))

        val scarab = required(provider.npcs[11697])
        assertEquals("Scarab", scarab.name)
        assertEquals(1, scarab.size)
        assertEquals("Attack", scarab.actions.getOpOrNull(1))

        val attackableObelisk = required(provider.npcs[11698])
        assertEquals("<col=00ffff>Obelisk</col>", attackableObelisk.name)
        assertEquals("Hit", attackableObelisk.actions.getOpOrNull(1))
        val hitObelisk = required(provider.npcs[11699])
        assertEquals("<col=00ffff>Obelisk</col>", hitObelisk.name)
        assertEquals(null, hitObelisk.actions.getOpOrNull(1))
    }

    private fun <T> withCache(block: (OsrsCacheProvider, List<MapLocDefinition>) -> T): T {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val provider = OsrsCacheProvider(cache, REVISION)
            provider.init()
            val locData = requireNotNull(cache.data(MAPS, SOURCE_REGION, 1))
            val decoded = MapLocListDecoder.decode(InlineByteBuf(locData))
            val entries = mutableListOf<MapLocDefinition>()
            val iterator = decoded.spawns.longIterator()
            while (iterator.hasNext()) {
                val loc = MapLocDefinition(iterator.nextLong())
                if (loc.level == SOURCE_LEVEL) entries += loc
            }
            return block(provider, entries)
        } finally {
            cache.close()
        }
    }

    private fun <T : Any> required(
        value: T?,
        message: String = "Expected revision-240 cache definition",
    ): T {
        assertTrue(value != null, message)
        return requireNotNull(value)
    }

    private fun MapLocDefinition.toPlacement() = LocPlacement(id, localX, localZ, shape, angle)

    private data class LocPlacement(
        val id: Int,
        val x: Int,
        val z: Int,
        val shape: Int,
        val angle: Int,
    )

    private companion object {
        const val REVISION = 240
        const val SOURCE_REGION = 14162
        const val SOURCE_LEVEL = 0
        const val BASIC_FLOOR_LOC = 45380
        const val DAMAGED_FLOOR_LOC = 45381

        val SIDE_ROOM_GRID_ANCHORS = listOf(19 to 41, 36 to 41, 19 to 29, 36 to 29)

        val SERVER_INSERTED_LOC_IDS = buildSet {
            addAll(45338..45342)
            addAll(45344..45374)
            addAll(45384..45395)
        }
    }
}
