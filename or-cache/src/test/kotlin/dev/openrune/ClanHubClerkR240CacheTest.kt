package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.npc.MapNpcListDecoder
import dev.openrune.map.util.InlineByteBuf
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClanHubClerkR240CacheTest {
    @Test
    fun `revision 240 clan clerk is packed inside edgeville bank`() {
        val live = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val provider = OsrsCacheProvider(live, 240)
            provider.init()
            assertEquals("Clerk", requireNotNull(provider.npcs[10759]).name)
        } finally {
            live.close()
        }

        val server = Cache.load(Path.of(".data/cache/SERVER"))
        try {
            val edgevilleBank = (48 shl 8) or 54
            val data = requireNotNull(server.data(MAPS, edgevilleBank, 5))
            val spawns = MapNpcListDecoder.decode(InlineByteBuf(data)).packedSpawns
            val definitions = buildList {
                val iterator = spawns.intIterator()
                while (iterator.hasNext()) add(MapNpcDefinition(iterator.nextInt()))
            }
            assertEquals(
                listOf(MapNpcDefinition(10759, localX = 22, localZ = 36, level = 0)),
                definitions.filter { it.id == 10759 },
            )
        } finally {
            server.close()
        }
    }
}
