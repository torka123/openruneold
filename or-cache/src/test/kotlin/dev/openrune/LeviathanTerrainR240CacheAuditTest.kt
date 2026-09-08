package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.tile.MapTileSimpleDefinition
import dev.openrune.map.util.InlineByteBuf
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LeviathanTerrainR240CacheAuditTest {
    @Test
    fun `revision 240 Leviathan terrain retains bridge links on source plane one`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val terrain = requireNotNull(cache.data(MAPS, SOURCE_REGION, 0))
            val tiles = MapTileDecoder.decode(InlineByteBuf(terrain))
            val linked =
                buildList {
                    for (x in 0 until 64) {
                        for (z in 0 until 64) {
                            val flags = tiles[x, z, 1].toInt()
                            if ((flags and MapTileSimpleDefinition.LINK_BELOW) != 0) {
                                add(x to z)
                            }
                        }
                    }
                }
            val representativeArenaTiles =
                listOf(
                    23 to 32, // Safe instance entry.
                    29 to 42, // Combat start tile.
                    30 to 33, // Leviathan spawn tile.
                )
            representativeArenaTiles.forEach { tile ->
                assertTrue(tile in linked, "Expected plane-1 LINK_BELOW at source tile $tile")
            }
        } finally {
            cache.close()
        }
    }

    private companion object {
        const val SOURCE_REGION = 8291
    }
}
