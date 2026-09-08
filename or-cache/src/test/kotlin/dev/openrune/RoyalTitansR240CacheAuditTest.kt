package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.map.square.MapSquareGrid

class RoyalTitansR240CacheAuditTest {
    @Test
    fun `revision 240 contains the exact Royal Titans entrance placement`() {
        val cache = Cache.load(Path.of(".data/cache/SERVER"))
        try {
            val matches =
                cache
                    .archives(MAPS)
                    .asSequence()
                    .filter { it <= 25286 }
                    .mapNotNull { groupId ->
                        cache.data(MAPS, groupId, 0) ?: return@mapNotNull null
                        val locData = cache.data(MAPS, groupId, 1) ?: return@mapNotNull null
                        val squareX = groupId shr 8
                        val squareZ = groupId and 0xFF
                        val decoded = MapLocListDecoder.decode(InlineByteBuf(locData))
                        val placements = mutableListOf<LocPlacement>()
                        val iterator = decoded.spawns.longIterator()
                        while (iterator.hasNext()) {
                            val loc = MapLocDefinition(iterator.nextLong())
                            if (loc.id == ROYAL_TITANS_ENTRANCE) {
                                placements +=
                                    LocPlacement(
                                        x = squareX * MapSquareGrid.LENGTH + loc.localX,
                                        z = squareZ * MapSquareGrid.LENGTH + loc.localZ,
                                        level = loc.level,
                                        shape = loc.shape,
                                        angle = loc.angle,
                                    )
                            }
                        }
                        placements.takeIf(List<LocPlacement>::isNotEmpty)
                    }
                    .flatten()
                    .toList()
            assertEquals(listOf(LocPlacement(2948, 9573, 0, shape = 10, angle = 2)), matches)
        } finally {
            cache.close()
        }
    }

    private data class LocPlacement(
        val x: Int,
        val z: Int,
        val level: Int,
        val shape: Int,
        val angle: Int,
    )

    private companion object {
        const val ROYAL_TITANS_ENTRANCE = 55986
    }
}
